package com.qa.autotest.application.iam;

import java.time.Instant;

/**
 * 审计查询条件（HRD-02）。
 *
 * <p>{@code projectId} 为空表示全局（仅管理员可用）；普通用户必须带项目且是项目所有者。
 *
 * <p>分页有两种模式：
 * <ul>
 *   <li>页码模式（默认）：{@code page/size}，内部用 {@code LIMIT/OFFSET}；</li>
 *   <li>keyset 模式：带 {@code cursorAt + cursorId}，取「该行之后」的一页，深翻不再变慢
 *       （见 {@code V10_1__audit_keyset.sql} 与 {@code docs/evidence/v2.0/b5-keyset/}）。</li>
 * </ul>
 * 两种模式共用同一组过滤条件；{@code page} 在 keyset 模式下仅用于回显。
 */
public record AuditQuery(
        Long projectId,
        String action,
        Instant from,
        Instant to,
        int page,
        int size,
        Instant cursorAt,
        Long cursorId) {

    /** 页码模式（兼容既有调用点）。 */
    public AuditQuery(Long projectId, String action, Instant from, Instant to, int page, int size) {
        this(projectId, action, from, to, page, size, null, null);
    }

    /** keyset 模式是否生效；缺一半按页码模式处理，避免拼出半截条件。 */
    public boolean keyset() {
        return cursorAt != null && cursorId != null;
    }

    /** 归一化分页，避免负数或超大页拖垮查询。 */
    public AuditQuery normalized() {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        return new AuditQuery(projectId, action, from, to, safePage, safeSize, cursorAt, cursorId);
    }
}
