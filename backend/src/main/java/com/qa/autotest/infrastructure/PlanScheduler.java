package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.RunExecutionService;
import com.qa.autotest.application.plan.PlanScheduleService;
import com.qa.autotest.application.plan.PlanScheduleService.PlannedRun;
import com.qa.autotest.application.execution.RunDispatcher;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.plan.PlanRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 执行计划调度器（PLN-01）。
 *
 * <p><b>默认关闭</b>：只有显式配置 {@code planning.scheduler-enabled=true} 才会注册，
 * 避免开发机被动跑起真实 pabot；测试 profile 下同样不启动。
 *
 * <p>每次 tick 只做「领计划 + 建 run」的短事务，真正的执行交给单线程
 * {@link RunDispatcher}，与平台串行执行语义一致。
 */
@Configuration
@EnableScheduling
@Profile("!test")
@ConditionalOnProperty(prefix = "planning", name = "scheduler-enabled", havingValue = "true")
public class PlanScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PlanScheduler.class);

    private final PlanScheduleService schedules;
    private final RunExecutionService executions;
    private final RunDispatcher dispatcher;

    public PlanScheduler(PlanScheduleService schedules, RunExecutionService executions,
                         RunDispatcher dispatcher) {
        this.schedules = schedules;
        this.executions = executions;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${planning.tick-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void tick() {
        List<PlannedRun> runs;
        try {
            runs = schedules.tick();
        } catch (RuntimeException ex) {
            LOG.error("计划调度 tick 失败: {}", ex.getMessage(), ex);
            return;
        }
        for (PlannedRun planned : runs) {
            dispatcher.submit(planned.plan().projectId(), planned.plan().id(),
                    planned.record().runId(), planned.command().priority(), () -> {
                        RunRecord result;
                        try {
                            result = executions.execute(planned.record(), planned.command());
                        } catch (RuntimeException ex) {
                            // 派发线程没有请求线程的租户上下文，不补的话跳过事件的审计行 project_id 为空，
                            // 按项目查审计永远查不到，前端「依赖跳过事件」列表就是空的
                            asPlanContext(planned.plan(), () ->
                                    schedules.afterExecutionError(planned.plan(),
                                            planned.record().runId(), ex));
                            return;
                        }
                        asPlanContext(planned.plan(), () -> {
                            try {
                                schedules.afterRun(planned.plan(), result);
                            } catch (RuntimeException ex) {
                                LOG.warn("计划 {} 下游联动失败: {}", planned.plan().id(), ex.getMessage());
                            }
                        });
                    });
        }
    }

    /** 按计划归属补租户上下文，回调结束自动还原，不污染派发线程后续任务。 */
    private void asPlanContext(PlanRecord plan, Runnable action) {
        TenantContext.runAs(plan.createdBy(), "planner", plan.projectId(), action);
    }
}
