package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.LeaseView;
import com.qa.autotest.application.repository.ResourceLeaseRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.execution.Lease;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租约仓储实现。
 *
 * <p>互斥只靠 {@code uk_resource_lease_key} 唯一约束：{@link #tryAcquire} 是纯 INSERT，
 * 捕获 {@link DuplicateKeyException} 即视为被占用，不做 SELECT 预判（spec §4.3）。
 * 释放必须删除行，否则唯一键无法被下一次申请复用；删除前写 {@code resource_lease_history}。
 */
@Repository
public class ResourceLeaseRepositoryJdbc implements ResourceLeaseRepository {

    private static final String SELECT = """
            SELECT resource_key, job_id, run_id, project_id, leased_at, expires_at
              FROM resource_lease
            """;

    private static final RowMapper<Lease> MAPPER = (rs, rowNum) -> new Lease(
            rs.getString("resource_key"),
            rs.getLong("job_id"),
            rs.getString("run_id"),
            rs.getObject("project_id") == null ? 0L : rs.getLong("project_id"),
            JdbcSupport.instant(rs.getTimestamp("leased_at")),
            JdbcSupport.instant(rs.getTimestamp("expires_at")));

    private final JdbcTemplate jdbc;

    public ResourceLeaseRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryAcquire(Lease lease) {
        String sql = """
                INSERT INTO resource_lease (job_id, resource_key, lease_state, leased_at, expires_at,
                                            run_id, project_id, last_heartbeat_at)
                VALUES (?, ?, 'held', ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "resource_lease");
        try {
            jdbc.update(sql, lease.jobId(), lease.resourceKey(),
                    Timestamp.from(lease.acquiredAt()), Timestamp.from(lease.expiresAt()),
                    lease.runId(), lease.projectId(), Timestamp.from(lease.acquiredAt()));
            return true;
        } catch (DuplicateKeyException occupied) {
            return false;
        }
    }

    @Override
    public int renew(long jobId, Instant expiresAt) {
        String sql = """
                UPDATE resource_lease
                   SET expires_at = ?, last_heartbeat_at = ?, updated_at = CURRENT_TIMESTAMP(3)
                 WHERE job_id = ? AND lease_state = 'held' AND project_id > 0
                """;
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.update(sql, Timestamp.from(expiresAt), Timestamp.from(expiresAt), jobId);
    }

    @Override
    @Transactional
    public int release(long jobId, String reason) {
        List<Lease> held = findByJob(jobId);
        Instant releasedAt = Instant.now();
        held.forEach(lease -> recordHistory(lease, releasedAt, "released", reason));
        String sql = "DELETE FROM resource_lease WHERE job_id = ? AND project_id > 0";
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.update(sql, jobId);
    }

    @Override
    @Transactional
    public int releaseOne(String resourceKey, long jobId, String reason) {
        String select = SELECT + " WHERE resource_key = ? AND job_id = ? AND project_id > 0";
        TenantGuard.requireProjectScope(select, "resource_lease");
        Optional<Lease> held = JdbcSupport.first(jdbc.query(select, MAPPER, resourceKey, jobId));
        if (held.isEmpty()) {
            return 0;
        }
        recordHistory(held.get(), Instant.now(), stateOf(reason), reason);
        String sql = """
                DELETE FROM resource_lease
                 WHERE resource_key = ? AND job_id = ? AND project_id > 0
                """;
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.update(sql, resourceKey, jobId);
    }

    @Override
    public List<Lease> findByJob(long jobId) {
        String sql = SELECT + " WHERE job_id = ? AND project_id > 0";
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.query(sql, MAPPER, jobId);
    }

    @Override
    public List<Lease> findByRunId(String runId) {
        String sql = SELECT + " WHERE run_id = ? AND project_id > 0";
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.query(sql, MAPPER, runId);
    }

    @Override
    public List<Lease> findExpired(Instant now) {
        String sql = SELECT
                + " WHERE lease_state = 'held' AND expires_at IS NOT NULL AND expires_at < ?"
                + " AND project_id > 0 ORDER BY expires_at";
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.query(sql, MAPPER, Timestamp.from(now));
    }

    @Override
    public List<Long> findHeldJobIds() {
        String sql = """
                SELECT DISTINCT job_id FROM resource_lease
                 WHERE lease_state = 'held' AND project_id > 0
                """;
        TenantGuard.requireProjectScope(sql, "resource_lease");
        return jdbc.queryForList(sql, Long.class);
    }

    @Override
    public List<LeaseView> listByProject(long projectId) {
        String sql = """
                SELECT resource_key, run_id, job_id, project_id, leased_at, expires_at, lease_state
                  FROM resource_lease
                 WHERE project_id = ? AND lease_state = 'held'
                 ORDER BY resource_key
                """;
        TenantGuard.requireProjectScope(sql, "resource_lease");
        Instant now = Instant.now();
        return jdbc.query(sql, (rs, rowNum) -> {
            Instant expiresAt = JdbcSupport.instant(rs.getTimestamp("expires_at"));
            long remaining = expiresAt == null ? 0 : Math.max(0,
                    Duration.between(now, expiresAt).toSeconds());
            return new LeaseView(rs.getString("resource_key"), rs.getString("run_id"),
                    rs.getLong("job_id"), rs.getLong("project_id"),
                    JdbcSupport.instant(rs.getTimestamp("leased_at")), expiresAt,
                    remaining, rs.getString("lease_state"));
        }, projectId);
    }

    @Override
    public void recordHistory(Lease lease, Instant releasedAt, String state, String reason) {
        String sql = """
                INSERT INTO resource_lease_history (resource_key, job_id, run_id, project_id,
                                                    acquired_at, released_at, lease_state,
                                                    release_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "resource_lease_history");
        jdbc.update(sql, lease.resourceKey(), lease.jobId(), lease.runId(), lease.projectId(),
                Timestamp.from(lease.acquiredAt()), Timestamp.from(releasedAt), state, reason);
    }

    /** 回收走 releaseOne，状态由原因推断：含 expired 记 expired，其余记 released。 */
    private static String stateOf(String reason) {
        return reason != null && reason.contains("expired") ? "expired" : "released";
    }
}
