package com.qa.autotest.application.overview;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.OverviewRepository;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.overview.ProjectOverview;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 总览聚合（OVW-01，PRD §3.4）。
 *
 * <p>这里只做三件事：鉴权、参数夹边界、把仓储的原始计数派生成页面要的读数。
 * 口径常量集中在 {@code docs/plan/总览页真实化-评估与设计-20261104.md} §4。
 */
@Service
public class OverviewService {

    /** 近期执行条数上限：再多就不是「近期」而是报表了。 */
    static final int MAX_RECENT = 20;
    /** 趋势窗口上限：90 天足够看出周节奏，再长对 10 人团队没有决策价值。 */
    static final int MAX_DAYS = 90;
    /** 技术域 Top N：总览只给主要几个，全量分布去用例库筛。 */
    static final int TOP_DOMAINS = 6;

    private final OverviewRepository overview;
    private final AccessControlService access;

    public OverviewService(OverviewRepository overview, AccessControlService access) {
        this.overview = overview;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public ProjectOverview load(long projectId, int recent, int days) {
        access.bindProject(projectId);
        access.require(PermissionResource.CASE, PermissionAction.READ);

        int recentLimit = clamp(recent, 1, MAX_RECENT);
        int windowDays = clamp(days, 1, MAX_DAYS);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate firstDay = today.minusDays(windowDays - 1L);
        // 窗口从「第一天的 0 点」开始，而不是 now-30×24h：否则趋势图第一天只有半天数据，
        // 补零后也会多出/少掉一个点，与「恰好 N 个点」的契约冲突。
        Instant windowStart = firstDay.atStartOfDay(zone).toInstant();

        ProjectOverview.CaseStats cases = overview.caseStats(projectId, TOP_DOMAINS);
        ProjectOverview.AssetStats assets = overview.assetStats(projectId);
        ProjectOverview.RunCounts counts = overview.runCounts(projectId, windowStart);
        List<ProjectOverview.RecentRun> recentRuns = overview.recentRuns(projectId, recentLimit);
        List<ProjectOverview.DailyPoint> trend =
                fillMissingDays(overview.dailyTrend(projectId, windowStart), firstDay, today);

        ProjectOverview.RunStats runs = derive(counts);

        return new ProjectOverview(projectId, Instant.now(), windowDays,
                cases, assets, runs, recentRuns, trend);
    }

    /**
     * 把仓储计数派生成页面读数。抽成包私有静态方法是为了能单测：
     * 「无样本 → null」「平均耗时四舍五入」这两条最容易在重构里被改坏。
     */
    static ProjectOverview.RunStats derive(ProjectOverview.RunCounts counts) {
        Integer passRateBp = ProjectOverview.passRateBp(
                counts.casePass(), counts.caseFail(), counts.caseSkip());
        Long avgDurationMs = counts.avgDurationMs() == null
                ? null : Math.round(counts.avgDurationMs());
        return new ProjectOverview.RunStats(
                counts.total(), counts.inWindow(), counts.running(),
                counts.finished(), counts.failed(), counts.canceled(),
                counts.casePass(), counts.caseFail(), counts.caseSkip(),
                passRateBp, avgDurationMs, counts.lastRunAt());
    }

    /** 补零：SQL 只返回有运行的日期，趋势图需要连续日期，否则横轴会被压缩成「有数据的那几天」。 */
    static List<ProjectOverview.DailyPoint> fillMissingDays(List<ProjectOverview.DailyPoint> rows,
                                                            LocalDate firstDay, LocalDate lastDay) {
        Map<String, ProjectOverview.DailyPoint> byDate = new LinkedHashMap<>();
        for (ProjectOverview.DailyPoint row : rows) {
            byDate.put(row.date(), row);
        }
        List<ProjectOverview.DailyPoint> filled = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            String key = day.toString();
            filled.add(byDate.getOrDefault(key, new ProjectOverview.DailyPoint(key, 0, 0, 0)));
        }
        return filled;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
