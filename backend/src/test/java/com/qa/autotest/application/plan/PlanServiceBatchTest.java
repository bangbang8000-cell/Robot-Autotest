package com.qa.autotest.application.plan;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.application.repository.TestSetRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** W5 批量启停：逐条返回，单条脏 id 不影响其余；重复执行保持幂等（changed=false）。 */
class PlanServiceBatchTest {

    private static final long PROJECT = 29L;
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

    private PlanRepository plans;
    private AuditService audit;
    private PlanService service;

    @BeforeEach
    void setUp() {
        plans = mock(PlanRepository.class);
        audit = mock(AuditService.class);
        AccessControlService access = mock(AccessControlService.class);
        doNothing().when(access).bindProject(anyLong());
        service = new PlanService(plans, mock(TestSetRepository.class), access, audit,
                mock(PlanScheduleService.class), mock(ExecutionProperties.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("批量暂停：改状态 + 清空 next_fire_at，逐条审计")
    void batchPauseClearsNextFire() {
        PlanRecord running = plan(51, PlanStatus.ENABLED, "0 0 2 * * *");
        when(plans.find(PROJECT, 51)).thenReturn(Optional.of(running));

        List<PlanService.BatchPlanResult> results = service.batchStatus(
                PROJECT, List.of(51L), "PAUSED");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.changed()).isTrue();
            assertThat(result.status()).isEqualTo("PAUSED");
        });
        verify(plans).updateStatus(PROJECT, 51L, PlanStatus.PAUSED);
        verify(plans).updateSchedule(eq(PROJECT), eq(51L), any(), isNull());
        verify(audit).record("PLAN_BATCH_PAUSE", "execution_plan", "51", "batch");
    }

    @Test
    @DisplayName("批量启用：从当前时刻之后的触发点重算，last_fire_at 归零避免补跑")
    void batchEnableRecomputesNextFire() {
        PlanRecord paused = plan(52, PlanStatus.PAUSED, "0 0 2 * * *");
        when(plans.find(PROJECT, 52)).thenReturn(Optional.of(paused));

        List<PlanService.BatchPlanResult> results = service.batchStatus(
                PROJECT, List.of(52L), "ENABLED");

        assertThat(results).singleElement().satisfies(result -> assertThat(result.changed()).isTrue());
        verify(plans).updateStatus(PROJECT, 52L, PlanStatus.ENABLED);
        // cron 是 Asia/Shanghai 的 02:00，NOW=00:00Z=08:00 上海，故下一次是次日 02:00+08 = 18:00Z
        verify(plans).updateSchedule(PROJECT, 52L, NOW, Instant.parse("2026-11-07T18:00:00Z"));
        verify(audit).record("PLAN_BATCH_ENABLE", "execution_plan", "52", "batch");
    }

    @Test
    @DisplayName("已处于目标状态不写库，changed=false")
    void alreadyInTargetStateIsNoop() {
        when(plans.find(PROJECT, 53)).thenReturn(Optional.of(plan(53, PlanStatus.PAUSED, "0 0 2 * * *")));

        List<PlanService.BatchPlanResult> results = service.batchStatus(
                PROJECT, List.of(53L), "PAUSED");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.changed()).isFalse();
            assertThat(result.message()).contains("已处于目标状态");
        });
        verify(plans, never()).updateStatus(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("不存在的 id 只让该条失败，其余照常执行")
    void missingPlanDoesNotAbortBatch() {
        when(plans.find(PROJECT, 99)).thenReturn(Optional.empty());
        when(plans.find(PROJECT, 54)).thenReturn(Optional.of(plan(54, PlanStatus.ENABLED, "0 0 2 * * *")));

        List<PlanService.BatchPlanResult> results = service.batchStatus(
                PROJECT, List.of(99L, 54L), "PAUSED");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).changed()).isFalse();
        assertThat(results.get(0).message()).contains("不存在");
        assertThat(results.get(1).changed()).isTrue();
    }

    @Test
    @DisplayName("非法状态 / 空 id 列表在入口拒绝")
    void invalidInputRejected() {
        assertThatThrownBy(() -> service.batchStatus(PROJECT, List.of(51L), "RUNNING"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        assertThatThrownBy(() -> service.batchStatus(PROJECT, List.of(), "PAUSED"))
                .isInstanceOf(BusinessException.class);
    }

    private static PlanRecord plan(long id, PlanStatus status, String cron) {
        return new PlanRecord(id, PROJECT, "计划 " + id, null, null, List.of("suites/a"), null,
                1, 3600, cron, "Asia/Shanghai", status, null, null, null, 1L, NOW);
    }
}
