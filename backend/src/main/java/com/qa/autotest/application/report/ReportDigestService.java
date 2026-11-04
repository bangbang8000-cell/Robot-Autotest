package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ReportDigestRepository;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.ReportDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报告结果概览（PRJ-04，PRD FR-v0.2-53）。
 *
 * <p>只做三件事：鉴权、参数夹边界、把仓储原始计数派生成页面读数。口径见 {@link ReportDigest} 类注释。
 * <p>接口不做 400：首页聚合参数超界时夹到边界，返回可用数据比返回错误页更有价值（与 OVW-01 一致）。
 */
@Service
public class ReportDigestService {

    /** 最近报告条数：5 份足够看清「最近几次是变好还是变坏」。 */
    static final int RECENT_REPORTS = 5;
    /** 失败 Top：10 条是「该先修哪几个」的可执行上限，再多就变成清单。 */
    static final int TOP_FAILED = 10;
    /** 趋势点数上限：运行多时截断，保留最近 60 个点，避免折线糊成一团。 */
    static final int MAX_TREND_POINTS = 60;
    /** 窗口上限同总览：90 天。 */
    static final int MAX_DAYS = 90;

    private final ReportDigestRepository digest;
    private final AccessControlService access;

    public ReportDigestService(ReportDigestRepository digest, AccessControlService access) {
        this.digest = digest;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public ReportDigest load(long projectId, int days) {
        access.bindProject(projectId);
        // 报告读权限与 RPT-01 列表/详情同口径（EXECUTION.READ），不新造资源维度免动权限矩阵。
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        int windowDays = clamp(days, 1, MAX_DAYS);
        ZoneId zone = ZoneId.systemDefault();
        // 与总览一致：窗口从「第一天 0 点」开始，不是 now-30×24h；否则首日只算半天。
        Instant windowStart = LocalDate.now(zone).minusDays(windowDays - 1L)
                .atStartOfDay(zone).toInstant();

        List<ReportDigest.ReportRow> recent = digest.recentReports(projectId, RECENT_REPORTS);
        List<ReportDigest.FailedCase> failed =
                digest.topFailedCases(projectId, windowStart, TOP_FAILED);
        int[] counts = digest.coverageCounts(projectId);
        List<ReportDigest.PassPoint> trend =
                digest.passRateTrend(projectId, windowStart, MAX_TREND_POINTS);

        return new ReportDigest(projectId, Instant.now(), windowDays,
                recent, failed,
                ReportDigest.coverage(counts[0], counts[1]),
                trend);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
