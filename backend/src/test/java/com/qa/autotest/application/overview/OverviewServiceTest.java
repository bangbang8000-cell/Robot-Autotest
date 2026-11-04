package com.qa.autotest.application.overview;

import static org.assertj.core.api.Assertions.assertThat;

import com.qa.autotest.domain.overview.ProjectOverview;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 总览口径的纯函数测试（TC-OVW-06）。
 *
 * <p>只测最容易算错、且错了最不容易被发现的三件事：补零后的点数、无样本时的通过率、
 * 平均耗时的取整。SQL 与真实数据的一致性由 {@code OverviewRepositoryIT} 负责。
 */
class OverviewServiceTest {

    @Test
    @DisplayName("趋势补零：有 2 天数据也要返回 30 个连续日期，缺失日补 0")
    void 趋势补零返回连续日期() {
        LocalDate first = LocalDate.of(2026, 10, 6);
        LocalDate last = LocalDate.of(2026, 11, 4);
        List<ProjectOverview.DailyPoint> rows = List.of(
                new ProjectOverview.DailyPoint("2026-10-20", 2, 1, 0),
                new ProjectOverview.DailyPoint("2026-11-04", 5, 0, 1));

        List<ProjectOverview.DailyPoint> filled = OverviewService.fillMissingDays(rows, first, last);

        assertThat(filled).hasSize(30);
        assertThat(filled.get(0).date()).isEqualTo("2026-10-06");
        assertThat(filled.get(0).finished()).isZero();
        assertThat(filled.get(14).date()).isEqualTo("2026-10-20");
        assertThat(filled.get(14).finished()).isEqualTo(2);
        assertThat(filled.get(29).date()).isEqualTo("2026-11-04");
        assertThat(filled.get(29).canceled()).isEqualTo(1);
    }

    @Test
    @DisplayName("参数夹边界：recent 1~20、days 1~90，越界不抛异常")
    void 参数夹边界() {
        assertThat(OverviewService.clamp(0, 1, OverviewService.MAX_RECENT)).isEqualTo(1);
        assertThat(OverviewService.clamp(-5, 1, OverviewService.MAX_RECENT)).isEqualTo(1);
        assertThat(OverviewService.clamp(999, 1, OverviewService.MAX_RECENT))
                .isEqualTo(OverviewService.MAX_RECENT);
        assertThat(OverviewService.clamp(0, 1, OverviewService.MAX_DAYS)).isEqualTo(1);
        assertThat(OverviewService.clamp(999, 1, OverviewService.MAX_DAYS))
                .isEqualTo(OverviewService.MAX_DAYS);
        assertThat(OverviewService.clamp(8, 1, OverviewService.MAX_RECENT)).isEqualTo(8);
    }

    @Test
    @DisplayName("无样本：通过率必须是 null，不能是 0（0% 会被读成「跑过且全挂」）")
    void 无样本通过率为null() {
        assertThat(ProjectOverview.passRateBp(0, 0, 0)).isNull();

        ProjectOverview.RunStats stats = OverviewService.derive(new ProjectOverview.RunCounts(
                0, 0, 0, 0, 0, 0, 0, 0, 0, null, null));
        assertThat(stats.passRateBp()).isNull();
        assertThat(stats.avgDurationMs()).isNull();
        assertThat(stats.lastRunAt()).isNull();
    }

    @Test
    @DisplayName("通过率按用例级算：3 过 1 挂 = 7500 基点，取消/运行中不进分母")
    void 用例级通过率() {
        assertThat(ProjectOverview.passRateBp(3, 1, 0)).isEqualTo(7500);
        assertThat(ProjectOverview.passRateBp(1, 0, 0)).isEqualTo(10000);
        assertThat(ProjectOverview.passRateBp(0, 2, 0)).isZero();
        // SKIP 计入分母：跳过不等于通过
        assertThat(ProjectOverview.passRateBp(2, 0, 2)).isEqualTo(5000);
    }

    @Test
    @DisplayName("平均耗时四舍五入到整毫秒")
    void 平均耗时取整() {
        Instant last = Instant.parse("2026-11-04T01:00:00Z");
        ProjectOverview.RunStats stats = OverviewService.derive(new ProjectOverview.RunCounts(
                3, 2, 1, 1, 1, 1, 3, 1, 0, 1234.6, last));

        assertThat(stats.avgDurationMs()).isEqualTo(1235L);
        assertThat(stats.passRateBp()).isEqualTo(7500);
        assertThat(stats.lastRunAt()).isEqualTo(last);
        assertThat(stats.canceled()).isEqualTo(1);
        assertThat(stats.running()).isEqualTo(1);
    }
}
