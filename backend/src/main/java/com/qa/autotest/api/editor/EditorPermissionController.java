package com.qa.autotest.api.editor;

import com.qa.autotest.application.editor.EditorPermissionService;
import com.qa.autotest.application.editor.EditorPermissionService.EditorPermissions;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编辑器权限接口（EDT-05 / FR-v0.3-18）。
 *
 * <p>宿主页进入编辑器前先问一次权限：{@code canWrite=false} 时以只读方式挂载。
 * 该接口只做能力告知，不作为安全边界——保存链路的 403 由各业务接口自己把关。
 */
@RestController
@RequestMapping("/api/editor")
public class EditorPermissionController {

    private final EditorPermissionService permissions;

    public EditorPermissionController(EditorPermissionService permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/permissions")
    public ApiResponse<EditorPermissions> permissions(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(permissions.permissions(projectId));
    }
}
