package com.qa.autotest.application.plan;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** W5 计划编排助手的冲突检测：只报四类可确定判定，且检测过程零写库。 */
class PlanConflictServiceTest {

    private static final long PROJECT = 29L;
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

    private PlanRepository plans;
    private PlanConflictService service;

    @BeforeEach
    void setUp() {
        plans = mock(PlanRepository.class);
        AccessControlService access = mock(AccessControlService.class);
        doNothing().when(access).bindProject(anyLong());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new PlanConflictService(plans, access, clock);
    }

    @Test
    @DisplayName("同目标 + 同触发点：两条计划都被点名，且 id 升序")
    void duplicateTargetAndSameSlot() {
        PlanRecord first = plan(11, "夜间回归 A", PlanStatus.ENABLED, "0 0 2 * * *",
                List.of("suites/night"), "smoke");
        PlanRecord second = plan(12, "夜间回归 B", PlanStatus.ENABLED, "0 0 2 * * *",
                List.of("suites/night"), "smoke");
        givenPlans(first, second);
        when(plans.dependencies(PROJECT, 11)).thenReturn(List.of());
        when(plans.dependencies(PROJECT, 12)).thenReturn(List.of());

        List<PlanConflictService.PlanConflict> conflicts = service.detect(PROJECT);

        assertThat(conflicts).extracting(PlanConflictService.PlanConflict::type)
                .containsExactlyInAnyOrder("DUPLICATE_TARGET", "SAME_SLOT");
        assertThat(conflicts).allSatisfy(conflict -> assertThat(conflict.planIds())
                .containsExactly(11L, 12L));
        assertThat(conflicts).filteredOn(c -> c.type().equals("DUPLICATE_TARGET"))
                .singleElement().satisfies(c -> assertThat(c.severity()).isEqualTo("WARN"));
    }

    @Test
    @DisplayName("cron 解析失败是 FAIL：该计划永远不会触发")
    void invalidCronIsFail() {
        PlanRecord broken = plan(21, "坏 cron", PlanStatus.ENABLED, "not-a-cron",
                List.of("suites/a"), null);
        givenPlans(broken);
        when(plans.dependencies(PROJECT, 21)).thenReturn(List.of());

        List<PlanConflictService.PlanConflict> conflicts = service.detect(PROJECT);

        assertThat(conflicts).filteredOn(c -> c.type().equals("INVALID_CRON"))
                .singleElement().satisfies(c -> {
                    assertThat(c.severity()).isEqualTo("FAIL");
                    assertThat(c.planIds()).containsExactly(21L);
                    assertThat(c.detail()).contains("坏 cron");
                });
    }

    @Test
    @DisplayName("依赖被暂停是 FAIL：下游计划永远等不到上游")
    void disabledDependencyIsFail() {
        PlanRecord upstream = plan(31, "上游", PlanStatus.PAUSED, "0 0 1 * * *",
                List.of("suites/up"), null);
        PlanRecord downstream = plan(32, "下游", PlanStatus.ENABLED, "0 0 3 * * *",
                List.of("suites/down"), null);
        givenPlans(upstream, downstream);
        when(plans.dependencies(PROJECT, 32)).thenReturn(List.of(31L));

        List<PlanConflictService.PlanConflict> conflicts = service.detect(PROJECT);

        assertThat(conflicts).filteredOn(c -> c.type().equals("DISABLED_DEPENDENCY"))
                .singleElement().satisfies(c -> {
                    assertThat(c.severity()).isEqualTo("FAIL");
                    assertThat(c.planIds()).containsExactly(32L);
                    assertThat(c.detail()).contains("上游").contains("31");
                });
    }

    @Test
    @DisplayName("健康编排零冲突：目标不同、触发点不同、依赖都启用")
    void healthyPlansHaveNoConflicts() {
        PlanRecord first = plan(41, "上午", PlanStatus.ENABLED, "0 0 9 * * *",
                List.of("suites/a"), null);
        PlanRecord second = plan(42, "下午", PlanStatus.ENABLED, "0 0 15 * * *",
                List.of("suites/b"), null);
        givenPlans(first, second);
        when(plans.dependencies(PROJECT, 41)).thenReturn(List.of());
        when(plans.dependencies(PROJECT, 42)).thenReturn(List.of());

        assertThat(service.detect(PROJECT)).isEmpty();
    }

    private void givenPlans(PlanRecord... records) {
        when(plans.page(PROJECT, 1, 100)).thenReturn(
                new PageResult<>(List.of(records), records.length, 1, 100));
    }

    private static PlanRecord plan(long id, String name, PlanStatus status, String cron,
                                   List<String> suites, String tag) {
        return new PlanRecord(id, PROJECT, name, null, null, suites, tag, 1, 3600,
                cron, "Asia/Shanghai", status, null, null, null, 1L, NOW);
    }
}
