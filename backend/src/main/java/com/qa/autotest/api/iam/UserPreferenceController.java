package com.qa.autotest.api.iam;

import com.qa.autotest.application.iam.UserPreferenceService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的偏好（UX-01 / FR-v0.2-33）。
 *
 * <p>路径刻意用 {@code /users/me/preferences} 而不是 {@code /users/{id}/preferences}：
 * 偏好只属于本人，不提供"管理员改别人主题"的能力，也就没必要暴露 userId 参数。
 */
@RestController
@RequestMapping("/api/users/me/preferences")
public class UserPreferenceController {

    private final UserPreferenceService preferences;

    public UserPreferenceController(UserPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> get() {
        return ApiResponse.ok(preferences.get(TenantContext.requireUserId()));
    }

    @PutMapping
    public ApiResponse<Map<String, String>> put(@RequestBody Map<String, String> values) {
        return ApiResponse.ok(preferences.put(TenantContext.requireUserId(), values));
    }
}
