package com.qa.autotest.domain.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 权限矩阵单测（PRD §3.1，IAM-03）。 */
class PermissionMatrixTest {

    /** 资源按 {@link PermissionResource} 声明顺序排列，与 {@link #EXPECTED} 的字符串逐位对应。 */
    private static final List<PermissionResource> RESOURCES = List.of(PermissionResource.values());

    /** 动作按 {@link PermissionAction} 声明顺序：READ / CREATE / UPDATE / DELETE / EXECUTE / USE。 */
    private static final List<PermissionAction> ACTIONS = List.of(PermissionAction.values());

    /**
     * 全量期望表：每个角色一行，按资源顺序给 8 个 6 字符串，位序同 {@link #ACTIONS}，
     * {@code '-'} 表示拒绝。
     *
     * <p>这张表是**独立于实现**的 PRD 抄录，不是从 {@code PermissionMatrix} 反推的；
     * 改矩阵必须同步改这里，否则全量用例会指出具体是哪一格变了。
     */
    private static final Map<RoleCode, List<String>> EXPECTED = Map.of(
            RoleCode.ADMIN, List.of(
                    "RCUDES", "RCUDES", "RCUDES", "RCUDES",
                    "RCUDES", "RCUDES", "RCUDES", "RCUDES"),
            RoleCode.PM, List.of(
                    "R-----", // 用户/角色：只读
                    "R-U---", // 项目：读 + 改自己
                    "RCUDES", // 设备/端口/拓扑
                    "RCUDES", // 凭据
                    "RCUDES", // 用例/用例集
                    "RCUDES", // 执行
                    "----E-", // 导入：只执行
                    "R-----"), // 审计：只读
            RoleCode.ENGINEER, List.of(
                    "R-----", // 用户/角色：只读（仅自己由服务层细化）
                    "R-----", // 项目：只读
                    "RCU---", // 设备/端口/拓扑：读 / 建 / 改，不可删
                    "-----S", // 凭据：只可使用，不可读清单
                    "RCUDES", // 用例/用例集
                    "RCU-E-", // 执行：读 / 建 / 改 / 执行，不可删
                    "----E-", // 导入：只执行
                    "------"), // 审计：无
            RoleCode.VIEWER, List.of(
                    "------", // 用户/角色
                    "R-----", // 项目
                    "R-----", // 设备/端口/拓扑
                    "------", // 凭据
                    "R-----", // 用例/用例集
                    "R-----", // 执行
                    "------", // 导入
                    "------")); // 审计

    @Test
    @DisplayName("全量权限矩阵：4 角色 × 8 资源 × 6 动作 = 192 格逐格断言")
    void 全量矩阵逐格断言() {
        int checked = 0;
        for (RoleCode role : RoleCode.values()) {
            List<String> row = EXPECTED.get(role);
            assertEquals(RESOURCES.size(), row.size(), role + " 的期望表必须覆盖全部资源");
            for (int r = 0; r < RESOURCES.size(); r++) {
                String flags = row.get(r);
                assertEquals(ACTIONS.size(), flags.length(), role + ":" + RESOURCES.get(r) + " 位数不对");
                for (int a = 0; a < ACTIONS.size(); a++) {
                    boolean want = flags.charAt(a) != '-';
                    PermissionResource resource = RESOURCES.get(r);
                    PermissionAction action = ACTIONS.get(a);
                    assertEquals(want, PermissionMatrix.allows(role, resource, action),
                            role + " " + resource + ":" + action + " 期望 " + (want ? "允许" : "拒绝"));
                    checked++;
                }
            }
        }
        assertEquals(RoleCode.values().length * RESOURCES.size() * ACTIONS.size(), checked,
                "必须遍历全部角色 × 资源 × 动作，不允许抽样");
        assertEquals(192, checked);
    }

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
