package com.qa.autotest.application.plan;

import com.qa.autotest.domain.execution.RunOrigin;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.qa.autotest.application.plan.PlanTestSupport.CLOCK;
import static com.qa.autotest.application.plan.PlanTestSupport.PROJECT;
import static com.qa.autotest.application.plan.PlanTestSupport.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 计划调度核心逻辑（PLN-01）：重叠、并发上限、领取、暂停、手动、metadata、next_fire、错过。 */
class PlanScheduleServiceTest {

    private static final Instant NOW = CLOCK.instant();
    private static final Instant FIRE = Instant.parse("2026-11-03T02:00:00Z");

    private final PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
    private final PlanTestSupport.StubRunService runs = new PlanTestSupport.StubRunService();
    private final PlanTestSupport.StubAudit audit = new PlanTestSupport.StubAudit();
    private final PlanTestSupport.RecordingDispatcher dispatcher =
            new PlanTestSupport.RecordingDispatcher();
    private final PlanProperties properties = new PlanProperties();
    private final PlanScheduleService service =
            PlanTestSupport.service(plans, runs, audit, dispatcher, properties);

    @Test
    @DisplayName("到点触发：建 run、trigger_type=schedule、plan_id 落库、next_fire 推进")
    void 到点触发并推进下一次() {
        plans.put(plan(1L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));

        List<PlanScheduleService.PlannedRun> fired = service.tick();

        assertEquals(1, fired.size());
        assertEquals(1, runs.origins.size());
        RunOrigin origin = runs.origins.get(0);
        assertEquals("schedule", origin.triggerType());
        assertEquals(1L, origin.planId());
        assertEquals(FIRE, origin.triggeredByPlanAt());
        assertTrue(audit.actions.contains("PLAN_TRIGGERED"));

        Instant next = plans.store.get(1L).nextFireAt();
        assertTrue(next.isAfter(NOW), "next_fire_at 必须推进到当前之后");
        assertTrue(!plans.store.get(1L).nextFireAt().equals(FIRE));
    }

    @Test
    @DisplayName("重叠 SKIP：已有排队/运行中的 run 时跳过，记录 skipped_overlap 并推进下一个点")
    void 重叠跳过() {
        plans.put(plan(2L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));
        plans.activeRunForPlan = true;

        List<PlanScheduleService.PlannedRun> fired = service.tick();

        assertTrue(fired.isEmpty());
        assertTrue(runs.commands.isEmpty());
        assertTrue(audit.contains("PLAN_SKIPPED_OVERLAP", "policy=skip"));
        assertTrue(plans.store.get(2L).nextFireAt().isAfter(NOW));
    }

    @Test
    @DisplayName("项目并发上限：deferred 不建 run，保持到点并留下审计，下一 tick 再试")
    void 并发上限_deferred() {
        properties.setMaxConcurrentPerProject(2);
        plans.activeScheduledRuns = 2;
        plans.put(plan(3L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));

        List<PlanScheduleService.PlannedRun> fired = service.tick();

        assertTrue(fired.isEmpty());
        assertTrue(runs.commands.isEmpty());
        assertTrue(audit.contains("PLAN_DEFERRED", "reason=project_concurrency_limit"));
        assertTrue(!plans.store.get(3L).nextFireAt().isAfter(NOW),
                "deferred 不得推进到未来，否则会被静默丢弃");
    }

    @Test
    @DisplayName("SKIP LOCKED 领取：同一条计划只被消费一次，第二次 tick 不再重复触发")
    void 领取只消费一次() {
        plans.put(plan(4L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));

        assertEquals(1, service.tick().size());
        assertEquals(0, service.tick().size());
        assertEquals(2, plans.lockDueCalls);
        assertEquals(1, runs.commands.size());
    }

    @Test
    @DisplayName("暂停的计划不触发")
    void 暂停不触发() {
        plans.put(plan(5L, PlanStatus.PAUSED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));

        assertTrue(service.tick().isEmpty());
        assertTrue(runs.commands.isEmpty());
        assertEquals(0, audit.actions.size());
    }

    @Test
    @DisplayName("手动触发：trigger_type=manual、带 plan_id、不受并发上限约束、仍走串行派发")
    void 手动触发() {
        properties.setMaxConcurrentPerProject(1);
        plans.activeScheduledRuns = 5;
        var target = plan(6L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot"));
        plans.put(target);

        RunRecord created = service.triggerManual(target);

        assertEquals(1, runs.origins.size());
        assertEquals("manual", runs.origins.get(0).triggerType());
        assertEquals(6L, runs.origins.get(0).planId());
        assertTrue(runs.commands.get(0).runRequestId().startsWith("manual-plan-6-"));
        assertEquals(List.of(created.runId()), dispatcher.runIds);
    }

    @Test
    @DisplayName("metadata 注入来源：PLAN_ID / TRIGGER_TYPE 通过 RunOrigin 落到 run 表")
    void metadata_注入来源() {
        plans.put(plan(7L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));

        service.tick();

        RunOrigin origin = runs.origins.get(0);
        assertEquals(7L, origin.planId(), "PLAN_ID 必须来自计划而非请求体");
        assertEquals("schedule", origin.triggerType(), "TRIGGER_TYPE 必须是 schedule");
        assertEquals(FIRE, origin.triggeredByPlanAt(), "触发点要能反查是哪一次 cron");
    }

    @Test
    @DisplayName("停机错过：多个触发点只补一次，随后 next_fire 直接跳到未来")
    void 错过只补一次() {
        // 每小时一次；停机 3 小时，next_fire 停在 2 小时前，last_fire 停在 3 小时前。
        Instant last = Instant.parse("2026-11-03T00:00:00Z");
        Instant due = Instant.parse("2026-11-03T01:00:00Z");
        plans.put(plan(8L, PlanStatus.ENABLED, last, due, "0 0 * * * *", List.of("suites/a.robot")));

        List<PlanScheduleService.PlannedRun> first = service.tick();
        List<PlanScheduleService.PlannedRun> second = service.tick();

        assertEquals(1, first.size(), "同一触发点只补一次");
        assertTrue(second.isEmpty(), "不得连续补跑历史触发点");
        assertTrue(audit.contains("PLAN_MISSED_CATCHUP", "catch_up_once=true"));
        assertTrue(plans.store.get(8L).nextFireAt().isAfter(NOW));
    }

    @Test
    @DisplayName("suitePaths 为空的计划不产出 run，记录无效并推进，避免每 tick 空转")
    void 空套件跳过() {
        plans.put(plan(9L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of()));

        assertTrue(service.tick().isEmpty());
        assertTrue(runs.commands.isEmpty());
        assertTrue(audit.contains("PLAN_SKIPPED_INVALID", "no_suite_paths"));
        assertTrue(plans.store.get(9L).nextFireAt().isAfter(NOW));
    }

    @Test
    @DisplayName("项目隔离：FAKE 仓储按 projectId 查询，计划触发使用自己的 project")
    void 触发使用计划所属项目() {
        plans.put(plan(10L, PlanStatus.ENABLED, null, FIRE, "0 */5 * * * *", List.of("suites/a.robot")));

        service.tick();

        assertEquals(PROJECT, runs.commands.get(0).projectId());
    }
}
