package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.domain.node.ExecutionNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 执行节点 JDBC 实现（NODX-01）。
 *
 * <p>execution_node 是平台级表（无 project_id 列），**有意不走**
 * {@code TenantGuard.requireProjectScope}——豁免依据写在接口与迁移注释里。
 */
@Repository
public class ExecutionNodeRepositoryJdbc implements ExecutionNodeRepository {

    private static final String SELECT = """
            SELECT id, name, token_hash, status, rf_available, pytest_available,
                   max_concurrency, last_heartbeat_at, created_at
              FROM execution_node
            """;

    private final JdbcTemplate jdbc;
    private final java.time.Clock clock;

    public ExecutionNodeRepositoryJdbc(JdbcTemplate jdbc, java.time.Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<ExecutionNode> find(long id) {
        return jdbc.query(SELECT + " WHERE id = ?", ExecutionNodeRepositoryJdbc::map, id)
                .stream().findFirst();
    }

    @Override
    public Optional<ExecutionNode> findByName(String name) {
        return jdbc.query(SELECT + " WHERE name = ?", ExecutionNodeRepositoryJdbc::map, name)
                .stream().findFirst();
    }

    @Override
    public boolean nameExists(String name) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM execution_node WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    @Override
    public List<ExecutionNode> list() {
        return jdbc.query(SELECT + " ORDER BY id", ExecutionNodeRepositoryJdbc::map);
    }

    @Override
    public ExecutionNode insert(String name, String tokenHash, boolean rfAvailable,
                                boolean pytestAvailable, int maxConcurrency, Long operatorId) {
        jdbc.update("""
                INSERT INTO execution_node
                  (name, token_hash, status, rf_available, pytest_available, max_concurrency,
                   created_by)
                VALUES (?, ?, 'NEW', ?, ?, ?, ?)
                """, name, tokenHash, rfAvailable, pytestAvailable, maxConcurrency, operatorId);
        return findByName(name).orElseThrow();
    }

    @Override
    public void heartbeat(long id, boolean rfAvailable, boolean pytestAvailable) {
        jdbc.update("""
                UPDATE execution_node
                   SET status = 'ONLINE', rf_available = ?, pytest_available = ?,
                       last_heartbeat_at = ?
                 WHERE id = ?
                """, rfAvailable, pytestAvailable, Timestamp.from(clock.instant()), id);
    }

    @Override
    public int markStaleOffline(Instant staleBefore) {
        return jdbc.update("""
                UPDATE execution_node SET status = 'OFFLINE'
                 WHERE status = 'ONLINE' AND last_heartbeat_at < ?
                """, Timestamp.from(staleBefore));
    }

    @Override
    public void updateStatus(long id, String status, Long operatorId) {
        jdbc.update("UPDATE execution_node SET status = ?, updated_by = ? WHERE id = ?",
                status, operatorId, id);
    }

    private static ExecutionNode map(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        Timestamp heartbeat = rs.getTimestamp("last_heartbeat_at");
        return new ExecutionNode(rs.getLong("id"), rs.getString("name"),
                rs.getString("token_hash"), rs.getString("status"),
                rs.getBoolean("rf_available"), rs.getBoolean("pytest_available"),
                rs.getInt("max_concurrency"),
                heartbeat == null ? null : heartbeat.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
