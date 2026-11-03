package com.qa.autotest.domain.iam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 权限矩阵单测（PRD §3.1，IAM-03）。 */
class PermissionMatrixTest {

    @Test
    @DisplayName("管理员拥有全部资源全部动作")
    void 管理员全权() {
        for (PermissionResource resource : PermissionResource.values()) {
            for (PermissionAction action : PermissionAction.values()) {
                assertTrue(PermissionMatrix.allows(RoleCode.ADMIN, resource, action),
                        "管理员应允许 " + resource + ":" + action);
            }
        }
    }

    @Test
    @DisplayName("访客只读，不能写任何资源")
    void 访客只读() {
        assertTrue(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.CASE,
                PermissionAction.READ));
        assertFalse(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.CASE,
                PermissionAction.CREATE));
        assertFalse(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.CREDENTIAL,
                PermissionAction.READ));
    }

    @Test
    @DisplayName("工程师可用凭据但不能读凭据清单、不能删资产")
    void 工程师边界() {
        assertTrue(PermissionMatrix.allows(RoleCode.ENGINEER, PermissionResource.CREDENTIAL,
                PermissionAction.USE));
        assertFalse(PermissionMatrix.allows(RoleCode.ENGINEER, PermissionResource.CREDENTIAL,
                PermissionAction.READ));
        assertFalse(PermissionMatrix.allows(RoleCode.ENGINEER, PermissionResource.ASSET,
                PermissionAction.DELETE));
    }

    @Test
    @DisplayName("项目经理不能删用户、不能建项目")
    void 项目经理边界() {
        assertFalse(PermissionMatrix.allows(RoleCode.PM, PermissionResource.USER_ROLE,
                PermissionAction.DELETE));
        assertFalse(PermissionMatrix.allows(RoleCode.PM, PermissionResource.PROJECT,
                PermissionAction.CREATE));
        assertTrue(PermissionMatrix.allows(RoleCode.PM, PermissionResource.IMPORT,
                PermissionAction.EXECUTE));
    }

    @Test
    @DisplayName("空角色一律拒绝")
    void 空角色拒绝() {
        assertFalse(PermissionMatrix.allows(null, PermissionResource.CASE, PermissionAction.READ));
    }

    @Test
    @DisplayName("执行域权限：工程师可建可取消，访客只读")
    void 执行域权限() {
        assertTrue(PermissionMatrix.allows(RoleCode.ENGINEER, PermissionResource.EXECUTION,
                PermissionAction.CREATE));
        assertTrue(PermissionMatrix.allows(RoleCode.ENGINEER, PermissionResource.EXECUTION,
                PermissionAction.UPDATE));
        assertFalse(PermissionMatrix.allows(RoleCode.ENGINEER, PermissionResource.EXECUTION,
                PermissionAction.DELETE));
        assertTrue(PermissionMatrix.allows(RoleCode.PM, PermissionResource.EXECUTION,
                PermissionAction.DELETE));
        assertTrue(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.EXECUTION,
                PermissionAction.READ));
        assertFalse(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.EXECUTION,
                PermissionAction.CREATE));
    }
}
