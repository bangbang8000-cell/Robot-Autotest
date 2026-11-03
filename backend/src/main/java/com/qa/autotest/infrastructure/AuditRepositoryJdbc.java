package com.qa.autotest.infrastructure;

import com.qa.autotest.application.iam.AuditQuery;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.AuditEntry;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 审计日志实现（audit_log 在白名单内，不需要 project_id 条件）。 */
@Repository
public class AuditRepositoryJdbc implements AuditRepository {

    private static final RowMapper<AuditEntry> ROW = (rs, rowNum) -> new AuditEntry(
            rs.getLong("id"),
            nullableLong(rs.getObject("project_id")),
            nullableLong(rs.getObject("actor_id")),
            rs.getString("operator"),
            rs.getString("action"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("detail"),
            rs.getString("summary"),
            rs.getString("ip"),
            timestamp(rs.getTimestamp("occurred_at")));

    private final JdbcTemplate jdbc;

    public AuditRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Long projectId, Long actorId, String action, String resourceType,
                       String resourceId, String detail) {
        jdbc.update("""
                INSERT INTO audit_log (project_id, actor_id, action, resource_type, resource_id, detail)
                VALUES (?, ?, ?, ?, ?, ?)
                """, projectId, actorId, action, resourceType, resourceId, detail);
    }

    @Override
    public void insertEntry(AuditEntry entry) {
        jdbc.update("""
                INSERT INTO audit_log
                    (project_id, actor_id, operator, action, resource_type, resource_id,
                     detail, summary, ip, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.projectId(), entry.actorId(), entry.operator(), entry.action(),
                entry.resourceType(), entry.resourceId(), entry.detail(), entry.summary(),
                entry.ip(), Timestamp.from(entry.occurredAt() == null
                        ? java.time.Instant.now() : entry.occurredAt()));
    }

    @Override
    public List<String> recentActions(Long projectId, int limit) {
        return jdbc.query("""
                SELECT action FROM audit_log WHERE project_id = ? ORDER BY occurred_at DESC LIMIT ?
                """, (rs, rowNum) -> rs.getString(1), projectId, limit);
    }

    @Override
    public PageResult<AuditEntry> page(AuditQuery query) {
        AuditQuery q = query.normalized();
        List<Object> args = new ArrayList<>();
        String where = where(q, args);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log" + where,
                Long.class, args.toArray());
        args.add(q.size());
        args.add((q.page() - 1) * q.size());
        List<AuditEntry> items = jdbc.query(
                "SELECT * FROM audit_log" + where
                        + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW, args.toArray());
        return new PageResult<>(items, total, q.page(), q.size());
    }

    @Override
    public List<AuditEntry> export(AuditQuery query, int limit) {
        AuditQuery q = query.normalized();
        List<Object> args = new ArrayList<>();
        String where = where(q, args);
        args.add(limit);
        return jdbc.query("SELECT * FROM audit_log" + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT ?", ROW, args.toArray());
    }

    /** 拼过滤条件；projectId 为空时查全局（仅管理员会走到这里）。 */
    private static String where(AuditQuery query, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        if (query.projectId() != null) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ").append("project_id = ?");
            args.add(query.projectId());
        }
        if (query.action() != null && !query.action().isBlank()) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ").append("action LIKE ?");
            args.add("%" + query.action().trim() + "%");
        }
        if (query.from() != null) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ").append("occurred_at >= ?");
            args.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            sb.append(sb.length() == 0 ? " WHERE " : " AND ").append("occurred_at <= ?");
            args.add(Timestamp.from(query.to()));
        }
        return sb.toString();
    }

    private static Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static java.time.Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
