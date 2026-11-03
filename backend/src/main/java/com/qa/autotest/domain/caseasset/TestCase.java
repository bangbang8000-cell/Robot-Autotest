package com.qa.autotest.domain.caseasset;

/**
 * 用例台账（ADR-0005）：可完全独立于脚本存在。
 *
 * <p>{@code caseCode} 即对外文档中的 {@code external_id}，项目内唯一。
 */
public record TestCase(
        Long id,
        Long projectId,
        String caseCode,
        String title,
        String category,
        String priority,
        String precondition,
        String expected,
        String domainTag,
        String reviewStatus,
        String source,
        String capability,
        String description) {
}
