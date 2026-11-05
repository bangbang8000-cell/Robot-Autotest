package com.qa.autotest.application.plan;

import com.qa.autotest.application.execution.RunDispatcher;
import com.qa.autotest.application.execution.RunExecutionService;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunOrigin;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.plan.PlanDependencyGraph;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 计划编排核心（PLN-01 + PLN-02）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>{@link #tick()} 只做<b>短事务</b>：领计划、判重叠/并发、建 run、推进 next_fire_at，
 *       然后把「待执行列表」交回调度器；pabot 长任务在事务之外通过单线程执行器串行跑。</li>
 *   <li>{@link #triggerManual(PlanRecord)} 手动触发，不受依赖与并发上限约束（权限由 PlanService 判）。</li>
 *   <li>{@link #afterRun(PlanRecord, RunRecord)} 上游结束后的 DAG 联动：成功触发直接下游，
 *       失败/取消只记录 {@code skipped_dependency}，不触发。</li>
 * </ul>
 */
@Service
public class PlanScheduleService {

    private static final Logger LOG = LoggerFactory.getLogger(PlanScheduleService.class);

    /** 调度器拿到的待执行计划（run 已落库，事务提交后再交给执行器）。 */
    public record PlannedRun(PlanRecord plan, RunRecord record, RunService.CreateRunCommand command) {
    }

    private final PlanRepository plans;
    private final RunService runs;
    private final RunExecutionService executions;
    private final AuditService audit;
    private final PlanProperties properties;
    private final RunDispatcher dispatcher;
    private final Clock clock;

    public PlanScheduleService(PlanRepository plans,
                               RunService runs,
                               RunExecutionService executions,
                               AuditService audit,
                               PlanProperties properties,
                               RunDispatcher dispatcher,
                               Clock clock) {
        this.plans = plans;
        this.runs = runs;
        this.executions = executions;
        this.audit = audit;
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /**
     * 一次调度 tick：用 {@code FOR UPDATE SKIP LOCKED} 领取到点计划，逐条处理。
     *
     * <p>多实例部署时，同一条计划只会被一个实例拿到；拿到的计划在本事务内推进
     * last_fire_at / next_fire_at，其它实例的 SELECT 会跳过该行。
     */
    @Transactional
    public List<PlannedRun> tick() {
        Instant now = clock.instant();
        List<PlanRecord> due = plans.lockDue(now, Math.max(1, properties.getLockBatch()));
        List<PlannedRun> fire = new ArrayList<>();
        for (PlanRecord plan : due) {
            TenantContext.runAs(plan.createdBy(), "planner", plan.projectId(),
                    () -> process(plan, now, fire));
        }
        return fire;
    }

    private void process(PlanRecord plan, Instant now, List<PlannedRun> fire) {
        if (!plan.status().triggersAllowed() || plan.nextFireAt() == null) {
            return;
        }
        CronSchedule schedule;
        try {
            schedule = CronSchedule.parse(plan.cron(), plan.timezone());
        } catch (IllegalArgumentException invalid) {
            plans.updateSchedule(plan.projectId(), plan.id(), plan.lastFireAt(), null);
            audit("PLAN_CRON_INVALID", plan, "cron=" + plan.cron() + ", reason=" + invalid.getMessage());
            return;
        }
        if (plan.suitePaths().isEmpty()) {
            Instant nextFire = schedule.nextAfter(now);
            plans.updateSchedule(plan.projectId(), plan.id(), plan.lastFireAt(), nextFire);
            audit("PLAN_SKIPPED_INVALID", plan, "reason=no_suite_paths, next=" + nextFire);
            return;
        }

        Instant fireAt = plan.nextFireAt();

        // 重叠：上一轮仍排队/执行，按 SKIP 跳过本次并推进到下一个触发点。
        if (plans.hasActiveRun(plan.projectId(), plan.id())) {
            Instant nextFire = schedule.nextAfter(now);
            plans.updateSchedule(plan.projectId(), plan.id(), fireAt, nextFire);
            audit("PLAN_SKIPPED_OVERLAP", plan,
                    "policy=" + plan.overlapPolicy().dbValue() + ", fire_at=" + fireAt
                            + ", next=" + nextFire);
            LOG.info("计划 {} 触发点 {} 因上一轮未结束被跳过", plan.id(), fireAt);
            return;
        }

        // 项目级并发上限：本次不建 run，保持到点状态等下一 tick 再看，并记录 deferred（不静默丢弃）。
        int active = plans.countActiveScheduledRuns(plan.projectId());
        int maxConcurrent = Math.max(1, properties.getMaxConcurrentPerProject());
        if (active >= maxConcurrent) {
            boolean firstDefer = plan.lastFireAt() == null || plan.lastFireAt().isBefore(fireAt);
            plans.updateSchedule(plan.projectId(), plan.id(), fireAt, fireAt);
            if (firstDefer) {
                audit("PLAN_DEFERRED", plan,
                        "reason=project_concurrency_limit, active=" + active + ", max=" + maxConcurrent
                                + ", fire_at=" + fireAt);
            }
            LOG.info("计划 {} 触发点 {} 因项目并发上限 {}/{} 被 defer", plan.id(), fireAt, active, maxConcurrent);
            return;
        }

        // 停机错过：从 last_fire_at 到现在超过 1 个触发点即视为 missed；同一触发点只补一次，
        // 补跑后 next_fire_at 直接推进到 now 之后的第一个点，不连续补历史。
        int missedPoints = plan.lastFireAt() == null
                ? 0 : schedule.countFirePoints(plan.lastFireAt(), now, 2);
        boolean missed = missedPoints > 1;
        Instant nextFire = schedule.nextAfter(now);

        RunService.CreateRunCommand command = buildCommand(plan, "plan-" + plan.id() + "-" + fireAt.toEpochMilli());
        RunOrigin origin = RunOrigin.scheduled(plan.id(), fireAt);
        RunRecord created = runs.createForScheduler(command, origin);
        plans.updateSchedule(plan.projectId(), plan.id(), now, nextFire);

        if (missed) {
            audit("PLAN_MISSED_CATCHUP", plan,
                    "fire_at=" + fireAt + ", missed_points>=2, catch_up_once=true, next=" + nextFire);
        }
        audit("PLAN_TRIGGERED", plan,
                "run=" + created.runId() + ", fire_at=" + fireAt + ", next=" + nextFire);
        LOG.info("计划 {} 触发 run {}（触发点 {}）", plan.id(), created.runId(), fireAt);

        if (created.state() == RunState.QUEUED) {
            fire.add(new PlannedRun(plan, created, command));
        }
    }

    /**
     * 手动触发（页面「立即执行」）：不受依赖与并发上限约束，但仍写 trigger_type='manual'，
     * 并保留 plan_id 便于反查。权限由 {@link PlanService#triggerNow(long)} 判 EXECUTION:EXECUTE。
     */
    @Transactional
    public RunRecord triggerManual(PlanRecord plan) {
        RunService.CreateRunCommand command = buildCommand(plan,
                "manual-plan-" + plan.id() + "-" + clock.instant().toEpochMilli());
        RunOrigin origin = RunOrigin.manualOnPlan(plan.id());
        RunRecord created = runs.createForScheduler(command, origin);
        audit("PLAN_MANUAL_TRIGGER", plan, "run=" + created.runId());
        if (created.state() == RunState.QUEUED) {
            dispatch(plan, created, command);
        }
        return created;
    }

    /**
     * CI 触发（PLN-03）：与手动触发同一条创建路径，只是 trigger_type='ci'，
     * 便于在 run 表与报告 metadata 里区分来源。权限已在 CI 令牌层判完（项目绑定 + scope）。
     *
     * <p>run 落库后立即返回；执行由单线程派发器异步进行，接口不等待结果。
     */
    @Transactional
    public RunRecord triggerCi(PlanRecord plan, String requestId) {
        RunService.CreateRunCommand command = buildCommand(plan, requestId);
        RunOrigin origin = RunOrigin.ci(plan.id());
        RunRecord created = runs.createForScheduler(command, origin);
        audit("PLAN_CI_TRIGGER", plan, "run=" + created.runId() + ", request=" + requestId);
        if (created.state() == RunState.QUEUED) {
            dispatch(plan, created, command);
        }
        return created;
    }

    /**
     * 上游结束后的 DAG 联动。
     *
     * <p>成功（state=FINISHED，即用例全部通过）→ 触发直接下游；失败/取消 → 下游不触发，
     * 记录 {@code mark=skipped_dependency} 并写明原因。下游仍受暂停状态与项目并发上限约束。
     */
    @Transactional
    public void afterRun(PlanRecord upstream, RunRecord finished) {
        if (finished.state() == RunState.FINISHED) {
            fireDownstream(upstream, finished);
            return;
        }
        if (finished.state() == RunState.FAILED || finished.state() == RunState.CANCELED) {
            markDownstreamSkipped(upstream, finished,
                    "upstream_" + finished.state().dbValue());
        }
    }

    /** 执行链路自身抛异常（未拿到终态）时同样阻断下游，避免静默继续。 */
    @Transactional
    public void afterExecutionError(PlanRecord upstream, String runId, Throwable error) {
        markDownstreamSkipped(upstream,
                "upstream=error, run=" + runId,
                "upstream_error: " + (error == null ? "unknown" : error.getMessage()));
    }

    private void fireDownstream(PlanRecord upstream, RunRecord finished) {
        Map<Long, List<Long>> edges = plans.allDependencies(upstream.projectId());
        PlanDependencyGraph graph = PlanDependencyGraph.of(edges);
        for (Long downstreamId : graph.directDownstream(upstream.id())) {
            Optional<PlanRecord> found = plans.find(upstream.projectId(), downstreamId);
            if (found.isEmpty()) {
                audit("PLAN_DOWNSTREAM_SKIPPED", upstream,
                        "mark=skipped_dependency, downstream=" + downstreamId
                                + ", reason=plan_missing, upstream_run=" + finished.runId());
                continue;
            }
            PlanRecord downstream = found.get();
            if (downstream.status() != PlanStatus.ENABLED) {
                audit("PLAN_DOWNSTREAM_SKIPPED", upstream,
                        "mark=skipped_dependency, downstream=" + downstreamId
                                + ", reason=downstream_paused, upstream_run=" + finished.runId());
                continue;
            }
            if (plans.hasActiveRun(downstream.projectId(), downstream.id())) {
                audit("PLAN_DOWNSTREAM_SKIPPED", upstream,
                        "mark=skipped_dependency, downstream=" + downstreamId
                                + ", reason=downstream_overlap, upstream_run=" + finished.runId());
                continue;
            }
            int active = plans.countActiveScheduledRuns(downstream.projectId());
            int maxConcurrent = Math.max(1, properties.getMaxConcurrentPerProject());
            if (active >= maxConcurrent) {
                audit("PLAN_DEFERRED", downstream,
                        "reason=project_concurrency_limit, downstream_of=" + upstream.id()
                                + ", active=" + active + ", max=" + maxConcurrent);
                continue;
            }
            String requestId = "dep-" + upstream.id() + "-" + downstream.id() + "-"
                    + Math.abs(finished.runId().hashCode());
            RunService.CreateRunCommand command = buildCommand(downstream, requestId);
            RunOrigin origin = RunOrigin.scheduled(downstream.id(), clock.instant());
            RunRecord created = runs.createForScheduler(command, origin);
            audit("PLAN_DOWNSTREAM_TRIGGERED", downstream,
                    "upstream_plan=" + upstream.id() + ", upstream_run=" + finished.runId()
                            + ", run=" + created.runId());
            if (created.state() == RunState.QUEUED) {
                dispatch(downstream, created, command);
            }
        }
    }

    private void markDownstreamSkipped(PlanRecord upstream, RunRecord finished, String reason) {
        Map<Long, List<Long>> edges = plans.allDependencies(upstream.projectId());
        PlanDependencyGraph graph = PlanDependencyGraph.of(edges);
        for (Long downstreamId : graph.directDownstream(upstream.id())) {
            audit("PLAN_DOWNSTREAM_SKIPPED", upstream,
                    "mark=skipped_dependency, downstream=" + downstreamId + ", reason=" + reason
                            + ", upstream_state=" + finished.state().dbValue()
                            + ", upstream_run=" + finished.runId());
        }
        LOG.info("计划 {} 运行 {} 结束于 {}，下游 {} 个被标记 skipped_dependency",
                upstream.id(), finished.runId(), finished.state(),
                graph.directDownstream(upstream.id()).size());
    }

    private void markDownstreamSkipped(PlanRecord upstream, String detailPrefix, String reason) {
        Map<Long, List<Long>> edges = plans.allDependencies(upstream.projectId());
        PlanDependencyGraph graph = PlanDependencyGraph.of(edges);
        for (Long downstreamId : graph.directDownstream(upstream.id())) {
            audit("PLAN_DOWNSTREAM_SKIPPED", upstream,
                    "mark=skipped_dependency, downstream=" + downstreamId + ", reason=" + reason
                            + ", " + detailPrefix);
        }
    }

    private void dispatch(PlanRecord plan, RunRecord created, RunService.CreateRunCommand command) {
        dispatcher.submit(plan.projectId(), plan.id(), created.runId(), command.priority(), () -> {
            RunRecord result;
            try {
                result = executions.execute(created, command);
            } catch (RuntimeException ex) {
                afterExecutionError(plan, created.runId(), ex);
                return;
            }
            try {
                afterRun(plan, result);
            } catch (RuntimeException ex) {
                LOG.warn("计划 {} 下游联动失败: {}", plan.id(), ex.getMessage());
            }
        });
    }

    /** 计划 -> CreateRunCommand 的唯一映射点；metadata 的 PLAN_ID/TRIGGER_TYPE 走 run 表列。 */
    private RunService.CreateRunCommand buildCommand(PlanRecord plan, String requestId) {
        return new RunService.CreateRunCommand(
                plan.projectId(),
                plan.testSetId(),
                plan.suitePaths(),
                plan.concurrency(),
                plan.environmentId(),
                List.of(),
                requestId,
                5,
                plan.timeoutSeconds(),
                plan.includeTag(),
                null);
    }

    private void audit(String action, PlanRecord plan, String detail) {
        audit.record(action, "execution_plan", String.valueOf(plan.id()), detail);
    }
}
