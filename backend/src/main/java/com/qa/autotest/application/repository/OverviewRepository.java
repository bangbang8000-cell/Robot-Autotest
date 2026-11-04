package com.qa.autotest.application.repository;

import com.qa.autotest.domain.overview.ProjectOverview;
import java.time.Instant;
import java.util.List;

/**
 * 总览只读仓储（OVW-01）。
 *
 * <p>只提供聚合查询，不含业务口径（通过率怎么算在应用层）；实现方每条 SQL 必须显式带
 * {@code project_id} 并过 {@code TenantGuard}。
 */
public interface OverviewRepository {

    /** 用例统计：总数 + 优先级/评审状态分布 + 技术域 Top N。 */
    ProjectOverview.CaseStats caseStats(long projectId, int topDomainLimit);

    /** 资产统计：设备/端口/链路/凭据/环境/用例集。 */
    ProjectOverview.AssetStats assetStats(long projectId);

    /** 运行计数；窗口只影响 {@code inWindow} 与用例级通过率的分母。 */
    ProjectOverview.RunCounts runCounts(long projectId, Instant windowStart);

    /** 近期执行，按开始时间倒序。 */
    List<ProjectOverview.RecentRun> recentRuns(long projectId, int limit);

    /** 按天聚合的运行数（只含窗口内），缺失日期由应用层补 0。 */
    List<ProjectOverview.DailyPoint> dailyTrend(long projectId, Instant windowStart);
}
