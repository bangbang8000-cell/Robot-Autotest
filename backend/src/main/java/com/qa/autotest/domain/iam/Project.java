package com.qa.autotest.domain.iam;

/** 项目（多项目隔离的根）。 */
public record Project(
        Long id,
        String code,
        String name,
        String description,
        boolean enabled,
        Long ownerId,
        String visibility) {
}
