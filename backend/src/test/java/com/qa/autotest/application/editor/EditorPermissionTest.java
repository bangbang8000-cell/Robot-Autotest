package com.qa.autotest.application.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.editor.EditorPermissionService.EditorPermissions;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionMatrix;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.iam.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 编辑器权限视图单测（EDT-05，FR-v0.3-16 / FR-v0.3-17）。 */
class EditorPermissionTest {

    @Test
    @DisplayName("访客只读：canRead=true、canWrite=false")
    void 访客只读() {
        EditorPermissions p = EditorPermissions.of(RoleCode.VIEWER);
        assertEquals("viewer", p.role());
        assertTrue(p.canRead());
        assertFalse(p.canWrite());
    }

    @Test
    @DisplayName("工程师与项目经理可读可写")
    void 可写角色() {
        for (RoleCode role : new RoleCode[] {RoleCode.ENGINEER, RoleCode.PM, RoleCode.ADMIN}) {
            EditorPermissions p = EditorPermissions.of(role);
            assertTrue(p.canRead(), role + " 应可读");
            assertTrue(p.canWrite(), role + " 应可写");
        }
    }

    @Test
    @DisplayName("空角色不可读不可写")
    void 空角色() {
        EditorPermissions p = EditorPermissions.of(null);
        assertNull(p.role());
        assertFalse(p.canRead());
        assertFalse(p.canWrite());
    }

    @Test
    @DisplayName("接口层兜底依据：访客对用例的 CREATE/UPDATE/DELETE 全部拒绝")
    void 访客写接口必然403() {
        assertFalse(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.CASE,
                PermissionAction.CREATE), "保存接口 require(CASE, CREATE) 必须拒绝访客");
        assertFalse(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.CASE,
                PermissionAction.UPDATE), "移动/改名接口 require(CASE, UPDATE) 必须拒绝访客");
        assertFalse(PermissionMatrix.allows(RoleCode.VIEWER, PermissionResource.CASE,
                PermissionAction.DELETE), "删除接口 require(CASE, DELETE) 必须拒绝访客");
    }
}
