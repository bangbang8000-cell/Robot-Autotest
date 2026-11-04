package com.qa.autotest.api.report;

import com.qa.autotest.application.report.ReportDigestService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.report.ReportDigest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告结果概览（PRJ-04，PRD FR-v0.2-53）。
 *
 * <p>只读聚合：一次返回「最近报告 + 失败 Top10 + 覆盖率 + 通过率趋势」，让项目首页不必为结果视角
 * 再打 4 个接口自己拼。{@code days} 默认 30，超界由服务层夹到 1~90。
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class ReportDigestController {

    private final ReportDigestService digest;

    public ReportDigestController(ReportDigestService digest) {
        this.digest = digest;
    }

    @GetMapping("/report-digest")
    public ApiResponse<ReportDigest> digest(@PathVariable long projectId,
                                            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(digest.load(projectId, days));
    }
}
