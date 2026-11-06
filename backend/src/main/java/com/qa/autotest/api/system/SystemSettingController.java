package com.qa.autotest.api.system;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.system.SystemSettingService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置 API（NAV-02 / FR-v2.0-10）。
 *
 * <p>平台级（{@code /api/system/settings}）写入仅管理员；项目级写入先做
 * {@code requireProjectAccess}，跨项目访问一律 403 且不泄露项目是否存在。
 * 每次写入由服务层写 {@code audit_log}，前后值都进摘要。
 */
@RestController
@RequestMapping("/api")
public class SystemSettingController {

    private final SystemSettingService settings;
    private final AccessControlService access;

    public SystemSettingController(SystemSettingService settings, AccessControlService access) {
        this.settings = settings;
        this.access = access;
    }

    @GetMapping("/system/settings")
    public ApiResponse<Map<String, String>> platform() {
        return ApiResponse.ok(settings.platform());
    }

    @PutMapping("/system/settings")
    public ApiResponse<Map<String, String>> putPlatform(@RequestBody(required = false)
                                                           Map<String, String> values) {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "平台设置仅管理员可修改");
        }
        return ApiResponse.ok(settings.putPlatform(values, true));
    }

    @GetMapping("/projects/{projectId}/settings")
    public ApiResponse<Map<String, String>> project(@PathVariable long projectId) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(settings.project(projectId));
    }

    @PutMapping("/projects/{projectId}/settings")
    public ApiResponse<Map<String, String>> putProject(@PathVariable long projectId,
                                                        @RequestBody(required = false)
                                                        Map<String, String> values) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(settings.putProject(projectId, values));
    }
}
