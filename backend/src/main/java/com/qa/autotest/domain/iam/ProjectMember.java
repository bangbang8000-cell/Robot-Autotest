package com.qa.autotest.domain.iam;

/**
 * 项目成员关系（行级隔离的依据）。
 *
 * @param roleCode 项目内角色：admin / pm / engineer / viewer（与全局角色同值）
 */
public record ProjectMember(
        Long id,
        Long projectId,
        Long userId,
        String username,
        String roleCode) {
}
