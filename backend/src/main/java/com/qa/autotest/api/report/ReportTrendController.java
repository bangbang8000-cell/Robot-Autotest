package com.qa.autotest.api.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.report.TrendService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 趋势与性能基线接口（RPT-04 / FR-v0.5-17~20）。
 *
 * <p>控制器先做项目绑定，应用服务再按 {@code EXECUTION:READ} 判权；
 * 趋势 SQL 全部带 project_id，跨项目不可见。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportTrendController {

    private final TrendService trend;
    private final AccessControlService access;

    public ReportTrendController(TrendService trend, AccessControlService access) {
        this.trend = trend;
        this.access = access;
    }

    /** 某用例最近 N 次趋势 + 性能基线（baselineMs / deviated 随结果一起返回）。 */
    @GetMapping("/{runId}/trend")
    public ApiResponse<TrendService.CaseTrendResult> trend(
            @PathVariable String runId,
            @RequestParam long projectId,
            @RequestParam String caseKey,
            @RequestParam(defaultValue = "5") int limit) {
        access.bindProject(projectId);
        return ApiResponse.ok(trend.caseTrend(projectId, runId, caseKey, limit));
    }

    /** 通过率趋势：按天 / 周聚合。 */
    @GetMapping("/trend/pass-rate")
    public ApiResponse<TrendService.PassRateResult> passRate(
            @RequestParam long projectId,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "12") int limit) {
        access.bindProject(projectId);
        return ApiResponse.ok(trend.passRate(projectId, granularity, limit));
    }
}
