package com.qa.autotest.application.editor;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionMatrix;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.iam.RoleCode;
import org.springframework.stereotype.Service;

/**
 * 编辑器权限视图（EDT-05 / FR-v0.3-16、FR-v0.3-17）。
 *
 * <p>宿主拿到 {@code canWrite=false} 时把编辑器以只读方式挂载（界面禁用 + 写回闸门）；
 * 真正的兜底在接口层：保存类接口各自 {@code require(CASE, CREATE/UPDATE)}，
 * 只读用户直连接口同样得到 403。
 */
@Service
public class EditorPermissionService {

    private final AccessControlService access;

    public EditorPermissionService(AccessControlService access) {
        this.access = access;
    }

    /**
     * 当前用户在某项目（{@code projectId=null} 表示平台级）上的编辑器权限。
     *
     * <p>非项目成员直接 403，不返回「能读但实际看不到」的假乐观结果。
     */
    public EditorPermissions permissions(Long projectId) {
        if (projectId != null) {
            access.bindProject(projectId);
        }
        long userId = TenantContext.requireUserId();
        return EditorPermissions.of(access.effectiveRole(userId, projectId));
    }

    /** 编辑器权限三元组：角色码 + 读用例 + 写用例。 */
    public record EditorPermissions(String role, boolean canRead, boolean canWrite) {

        /** 由角色推导；空角色一律不可读不可写。 */
        public static EditorPermissions of(RoleCode role) {
            if (role == null) {
                return new EditorPermissions(null, false, false);
            }
            return new EditorPermissions(
                    role.code(),
                    PermissionMatrix.allows(role, PermissionResource.CASE, PermissionAction.READ),
                    PermissionMatrix.allows(role, PermissionResource.CASE, PermissionAction.CREATE));
        }
    }
}
