package com.qa.autotest.domain.iam;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 角色 × 资源 × 动作 的权限矩阵（PRD §3.1，本版定稿）。
 *
 * <p>纯函数：给定角色与资源动作返回是否允许。接口层与服务层都查同一张表，
 * 避免「注解允许、服务层又拒」的两套口径。v1.0 HRD-01 会对此做全量校验。
 */
public final class PermissionMatrix {

    private static final Map<RoleCode, Map<PermissionResource, Set<PermissionAction>>> MATRIX =
            build();

    private PermissionMatrix() {
    }

    /** 是否允许。 */
    public static boolean allows(RoleCode role, PermissionResource resource, PermissionAction action) {
        if (role == null) {
            return false;
        }
        Set<PermissionAction> actions = MATRIX.getOrDefault(role, Map.of())
                .getOrDefault(resource, Set.of());
        return actions.contains(action);
    }

    private static Map<RoleCode, Map<PermissionResource, Set<PermissionAction>>> build() {
        Map<RoleCode, Map<PermissionResource, Set<PermissionAction>>> matrix = new EnumMap<>(RoleCode.class);

        // 管理员：全部资源全部动作
        Map<PermissionResource, Set<PermissionAction>> admin = new EnumMap<>(PermissionResource.class);
        for (PermissionResource resource : PermissionResource.values()) {
            admin.put(resource, EnumSet.allOf(PermissionAction.class));
        }
        matrix.put(RoleCode.ADMIN, admin);

        // 项目经理：用户读、项目改自己、资产与凭据全、用例全、导入执行、审计读
        matrix.put(RoleCode.PM, Map.of(
                PermissionResource.USER_ROLE, EnumSet.of(PermissionAction.READ),
                PermissionResource.PROJECT, EnumSet.of(PermissionAction.READ, PermissionAction.UPDATE),
                PermissionResource.ASSET, EnumSet.allOf(PermissionAction.class),
                PermissionResource.CREDENTIAL, EnumSet.allOf(PermissionAction.class),
                PermissionResource.CASE, EnumSet.allOf(PermissionAction.class),
                PermissionResource.EXECUTION, EnumSet.allOf(PermissionAction.class),
                PermissionResource.IMPORT, EnumSet.of(PermissionAction.EXECUTE),
                PermissionResource.AUDIT, EnumSet.of(PermissionAction.READ)));

        // 测试工程师：读用户（仅自己由服务层细化）、读项目、资产增改、凭据使用、用例全、导入执行
        matrix.put(RoleCode.ENGINEER, Map.of(
                PermissionResource.USER_ROLE, EnumSet.of(PermissionAction.READ),
                PermissionResource.PROJECT, EnumSet.of(PermissionAction.READ),
                PermissionResource.ASSET, EnumSet.of(PermissionAction.READ, PermissionAction.CREATE,
                        PermissionAction.UPDATE),
                PermissionResource.CREDENTIAL, EnumSet.of(PermissionAction.USE),
                PermissionResource.CASE, EnumSet.allOf(PermissionAction.class),
                PermissionResource.EXECUTION, EnumSet.of(PermissionAction.READ,
                        PermissionAction.CREATE, PermissionAction.UPDATE, PermissionAction.EXECUTE),
                PermissionResource.IMPORT, EnumSet.of(PermissionAction.EXECUTE)));

        // 访客：只读
        matrix.put(RoleCode.VIEWER, Map.of(
                PermissionResource.PROJECT, EnumSet.of(PermissionAction.READ),
                PermissionResource.ASSET, EnumSet.of(PermissionAction.READ),
                PermissionResource.CASE, EnumSet.of(PermissionAction.READ),
                PermissionResource.EXECUTION, EnumSet.of(PermissionAction.READ)));

        return matrix;
    }
}
