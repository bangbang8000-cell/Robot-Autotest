package com.qa.autotest.domain.iam;

import java.time.Instant;

/**
 * 审计日志视图（FR-v1.0-06）。
 *
 * <p>纯数据记录，不含任何 Spring 注解；字段与 {@code audit_log} 表一一对应。
 * {@code summary} 为变更前后摘要，写入前已脱敏，绝不含口令 / 令牌明文。
 */
public record AuditEntry(
        Long id,
        Long projectId,
        Long actorId,
        String operator,
        String action,
        String resourceType,
        String resourceId,
        String detail,
        String summary,
        String ip,
        Instant occurredAt) {
}
