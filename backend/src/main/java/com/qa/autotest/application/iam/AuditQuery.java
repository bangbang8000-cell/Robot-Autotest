package com.qa.autotest.application.iam;

import java.time.Instant;

/**
 * 审计查询条件（HRD-02）。
 *
 * <p>{@code projectId} 为空表示全局（仅管理员可用）；普通用户必须带项目且是项目所有者。
 */
public record AuditQuery(
        Long projectId,
        String action,
        Instant from,
        Instant to,
        int page,
        int size) {

    /** 归一化分页，避免负数或超大页拖垮查询。 */
    public AuditQuery normalized() {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        return new AuditQuery(projectId, action, from, to, safePage, safeSize);
    }
}
