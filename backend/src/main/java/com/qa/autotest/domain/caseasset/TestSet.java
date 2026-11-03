package com.qa.autotest.domain.caseasset;

/** 用例集（FR-v0.2-19）：一条用例可出现在多个集合。 */
public record TestSet(
        Long id,
        Long projectId,
        String setCode,
        String name,
        String description,
        Long ownerId,
        String setStatus,
        int itemCount) {
}
