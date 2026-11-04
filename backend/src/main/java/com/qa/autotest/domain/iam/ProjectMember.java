package com.qa.autotest.domain.iam;

/**
 * 项目成员关系（行级隔离的依据）。
 *
 * @param roleCode    项目内角色：admin / pm / engineer / viewer（与全局角色同值）
 * @param displayName 用户显示名（成员表用，来自 app_user）
 * @param enabled     用户是否启用；停用用户仍保留成员关系，界面标「已停用」
 */
public record ProjectMember(
        Long id,
        Long projectId,
        Long userId,
        String username,
        String roleCode,
        String displayName,
        boolean enabled) {
}
