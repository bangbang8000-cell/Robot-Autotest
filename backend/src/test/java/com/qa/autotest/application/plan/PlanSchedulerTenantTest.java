package com.qa.autotest.application.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qa.autotest.application.execution.RunDispatcher;
import com.qa.autotest.application.execution.RunExecutionService;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.application.plan.PlanScheduleService.PlannedRun;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import com.qa.autotest.infrastructure.PlanScheduler;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * PLN-02：下游联动的审计写入必须带上计划所属项目。
 *
 * <p>派发线程不是请求线程，没有 {@link TenantContext}；如果不补上下文，
 * {@code PLAN_DOWNSTREAM_SKIPPED} 审计行的 {@code project_id} 是 NULL，
 * 前端按项目查「依赖跳过事件」永远是空列表。
 */
class PlanSchedulerTenantTest {

    @AfterEach
    void 清理上下文() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("下游联动回调在计划租户上下文里执行，结束后还原")
    void 下游联动带计划租户上下文() {
        PlanScheduleService schedules = mock(PlanScheduleService.class);
        RunExecutionService executions = mock(RunExecutionService.class);
        RunDispatcher dispatcher = mock(RunDispatcher.class);
        PlanScheduler scheduler = new PlanScheduler(schedules, executions, dispatcher);

        PlanRecord upstream = PlanTestSupport.plan(11L, PlanStatus.ENABLED, null, null,
                "0 3 * * *", List.of("suites/smoke"));
        RunRecord queued = PlanTestSupport.run(2L, "UP-RUN-1", RunState.QUEUED);
        RunRecord failed = PlanTestSupport.run(2L, "UP-RUN-1", RunState.FAILED);
        RunService.CreateRunCommand command = new RunService.CreateRunCommand(
                PlanTestSupport.PROJECT, null, List.of("suites/smoke"), 1, null, List.of(),
                "REQ-1", 5, 3600, null, List.of(), 0);
        when(schedules.tick()).thenReturn(List.of(new PlannedRun(upstream, queued, command)));
        when(executions.execute(any(), any())).thenReturn(failed);

        AtomicReference<Long> seenProject = new AtomicReference<>();
        AtomicReference<Long> seenUser = new AtomicReference<>();
        doAnswer(invocation -> {
            seenProject.set(TenantContext.projectId());
            seenUser.set(TenantContext.userId());
            return null;
        }).when(schedules).afterRun(any(), any());

        scheduler.tick();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(dispatcher).submit(eq(PlanTestSupport.PROJECT), eq(11L), eq("UP-RUN-1"), anyInt(),
                task.capture());
        // 派发前线程本身没有上下文，才能证明后面看到的项目是 asPlanContext 补进去的
        assertThat(TenantContext.projectId()).isNull();

        task.getValue().run();

        assertThat(seenProject.get()).isEqualTo(PlanTestSupport.PROJECT);
        assertThat(seenUser.get()).isEqualTo(PlanTestSupport.OPERATOR);
        assertThat(TenantContext.projectId()).as("回调结束后不能污染派发线程").isNull();
    }

    @Test
    @DisplayName("执行抛异常时补的上下文同样生效（失败传导的审计也带项目）")
    void 执行异常时也带计划租户上下文() {
        PlanScheduleService schedules = mock(PlanScheduleService.class);
        RunExecutionService executions = mock(RunExecutionService.class);
        RunDispatcher dispatcher = mock(RunDispatcher.class);
        PlanScheduler scheduler = new PlanScheduler(schedules, executions, dispatcher);

        PlanRecord upstream = PlanTestSupport.plan(12L, PlanStatus.ENABLED, null, null,
                "0 3 * * *", List.of("suites/smoke"));
        RunRecord queued = PlanTestSupport.run(3L, "UP-RUN-2", RunState.QUEUED);
        RunService.CreateRunCommand command = new RunService.CreateRunCommand(
                PlanTestSupport.PROJECT, null, List.of("suites/smoke"), 1, null, List.of(),
                "REQ-2", 5, 3600, null, List.of(), 0);
        when(schedules.tick()).thenReturn(List.of(new PlannedRun(upstream, queued, command)));
        when(executions.execute(any(), any())).thenThrow(new IllegalStateException("robot 未安装"));

        AtomicReference<Long> seenProject = new AtomicReference<>();
        doAnswer(invocation -> {
            seenProject.set(TenantContext.projectId());
            return null;
        }).when(schedules).afterExecutionError(any(), any(), any());

        scheduler.tick();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(dispatcher).submit(eq(PlanTestSupport.PROJECT), eq(12L), eq("UP-RUN-2"), anyInt(),
                task.capture());
        task.getValue().run();

        assertThat(seenProject.get()).isEqualTo(PlanTestSupport.PROJECT);
        assertThat(TenantContext.projectId()).isNull();
    }
}
