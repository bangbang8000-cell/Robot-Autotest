package com.qa.autotest.api.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.report.RetentionService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产物清理接口（RPT-06）。
 *
 * <p>{@code dryRun} 默认 true：不传参数只会返回候选清单与总空间，绝不删文件；
 * 管理员显式传 {@code dryRun=false} 才执行，服务层仍会再判一次管理员身份（非管理员 403）。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportRetentionController {

    private final RetentionService retention;
    private final AccessControlService access;

    public ReportRetentionController(RetentionService retention, AccessControlService access) {
        this.retention = retention;
        this.access = access;
    }

    @PostMapping("/retention/run")
    public ApiResponse<RetentionService.RetentionResult> run(
            @RequestParam long projectId,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        access.bindProject(projectId);
        return ApiResponse.ok(retention.run(projectId, dryRun));
    }
}
