package com.qa.autotest.api.iam;

import com.qa.autotest.application.iam.RoleService;
import com.qa.autotest.application.iam.UserService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.Role;
import com.qa.autotest.domain.iam.UserSummary;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户与角色管理（IAM-01）。 */
@RestController
@RequestMapping("/api")
public class UserController {

    public record CreateUserRequest(String username, String displayName, String email, Long orgId,
                                    String password, List<Long> roleIds) {
    }

    public record UpdateUserRequest(String displayName, String email, Long orgId, Boolean enabled,
                                    List<Long> roleIds) {
    }

    public record ResetPasswordRequest(String password) {
    }

    public record RoleRequest(String code, String name, String description,
                              String permissionsJson) {
    }

    private final UserService users;
    private final RoleService roles;

    public UserController(UserService users, RoleService roles) {
        this.users = users;
        this.roles = roles;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserSummary>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(users.page(keyword, status, page, Math.min(size, 200)));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserSummary> get(@PathVariable long id) {
        return ApiResponse.ok(users.get(id));
    }

    @PostMapping("/users")
    public ApiResponse<UserSummary> create(@RequestBody CreateUserRequest request) {
        return ApiResponse.ok(users.create(request.username(), request.displayName(),
                request.email(), request.orgId(), request.password(), request.roleIds()));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserSummary> update(@PathVariable long id,
                                           @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(users.update(id, request.displayName(), request.email(),
                request.orgId(), request.enabled() == null || request.enabled(),
                request.roleIds()));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        users.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable long id,
                                           @RequestBody ResetPasswordRequest request) {
        users.resetPassword(id, request.password());
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<Role>> roles() {
        return ApiResponse.ok(roles.list());
    }

    @PostMapping("/roles")
    public ApiResponse<Role> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.ok(roles.create(request.code(), request.name(), request.description(),
                request.permissionsJson()));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<Role> updateRole(@PathVariable long id, @RequestBody RoleRequest request) {
        return ApiResponse.ok(roles.update(id, request.name(), request.description(),
                request.permissionsJson()));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable long id) {
        roles.delete(id);
        return ApiResponse.ok(null);
    }
}
