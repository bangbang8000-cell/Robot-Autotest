package com.qa.autotest.domain.iam;

/** 角色（内置角色 {@code builtIn=true} 不可删除，FR-v0.2-02）。 */
public record Role(
        Long id,
        String code,
        String name,
        String description,
        boolean builtIn,
        String permissionsJson) {
}
