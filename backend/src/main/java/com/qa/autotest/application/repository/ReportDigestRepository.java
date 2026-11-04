package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.ReportDigest;
import java.time.Instant;
import java.util.List;

/**
 * 报告结果概览只读仓储（PRJ-04）。
 *
 * <p>四条查询一律显式带 {@code project_id}；{@code test_result} 没有该列，join {@code run} 取租户。
 */
public interface ReportDigestRepository {

    /** 最近 N 份已完成运行（按结束时间倒序）。 */
    List<ReportDigest.ReportRow> recentReports(long projectId, int limit);

    /** 窗口内失败用例 Top N（按失败次数倒序）。 */
    List<ReportDigest.FailedCase> topFailedCases(long projectId, Instant windowStart, int limit);

    /** 覆盖率原始计数：{@code [启用用例数, 能在结果里找到同名 key 的用例数]}。 */
    int[] coverageCounts(long projectId);

    /** 窗口内每次已完成运行的用例级通过率，按时间升序。 */
    List<ReportDigest.PassPoint> passRateTrend(long projectId, Instant windowStart, int limit);
}
