package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.ExecutionShardRepository;
import com.qa.autotest.application.repository.ExecutionShardRepository.AffectedRun;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.execution.ExecutionShard;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 运行分片 JDBC 实现（W2）。
 *
 * <p>并发口径（与规划文档 §4 W2 一致）：
 * <ol>
 *   <li>领取 = 先查候选再条件 UPDATE；UPDATE 的 WHERE 里带「PENDING 或租约过期」，
 *       两个 agent 同时抢同一片时只有一个能更新到 1 行，另一个 retry 下一片；</li>
 *   <li>回写必须带 {@code fencing_token}：租约被重抢后令牌已经 +1，
 *       旧持有者晚到的 DONE 自然落到 0 行，不会覆盖新结果；</li>
 *   <li>{@code expireLeases} 把过期 LEASED 置 LOST——这是 W3 重派的入口。</li>
 * </ol>
 */
@Repository
public class ExecutionShardRepositoryJdbc implements ExecutionShardRepository {

    private static final String SELECT = """
            SELECT id, run_id, project_id, node_id, shard_index, suite_paths, state,
                   lease_owner, lease_until, fencing_token, attempt, test_count, fail_count,
                   started_at, finished_at
              FROM execution_shard
            """;

    /**
     * 候选 = PENDING，或 LEASED 但租约已到期（NULL 视为到期，便于人工放行），
     * 或 LOST 且 attempt 未用尽（W3 重派；LOST 是「持有者失联/租约超时」的结果，
     * 重派靠 fencing_token +1 让旧节点的迟到回写失效）。
     */
    private static final String CLAIMABLE =
            "(state = 'PENDING'"
                    + " OR (state = 'LEASED' AND (lease_until IS NULL OR lease_until < ?))"
                    + " OR (state = 'LOST' AND attempt < ?))";

    private final JdbcTemplate jdbc;

    public ExecutionShardRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int replacePlan(String runId, long projectId, List<ShardDraft> drafts, Long operatorId) {
        String delete = """
                DELETE FROM execution_shard WHERE run_id = ? AND project_id = ?
                """;
        TenantGuard.requireProjectScope(delete, "execution_shard");
        jdbc.update(delete, runId, projectId);
        int inserted = 0;
        for (ShardDraft draft : drafts) {
            String insert = """
                    INSERT INTO execution_shard
                      (run_id, project_id, shard_index, suite_paths, state, created_by)
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """;
            TenantGuard.requireProjectScope(insert, "execution_shard");
            inserted += jdbc.update(insert, runId, projectId, draft.shardIndex(),
                    String.join("\n", draft.suitePaths()), operatorId);
        }
        return inserted;
    }

    @Override
    public List<ExecutionShard> listByRun(long projectId, String runId) {
        String sql = SELECT + """
                 WHERE project_id = ? AND run_id = ?
                 ORDER BY shard_index
                """;
        TenantGuard.requireProjectScope(sql, "execution_shard");
        return jdbc.query(sql, ExecutionShardRepositoryJdbc::map, projectId, runId);
    }

    @Override
    public Optional<ExecutionShard> find(long projectId, String runId, int shardIndex) {
        String sql = SELECT + """
                 WHERE project_id = ? AND run_id = ? AND shard_index = ?
                """;
        TenantGuard.requireProjectScope(sql, "execution_shard");
        return jdbc.query(sql, ExecutionShardRepositoryJdbc::map, projectId, runId, shardIndex)
                .stream().findFirst();
    }

    @Override
    public Optional<ExecutionShard> claimNext(long projectId, String runId, long nodeId,
                                              String leaseOwner, Instant now, Instant leaseUntil,
                                              int maxAttempts) {
        Timestamp nowTs = Timestamp.from(now);
        for (int retry = 0; retry < 3; retry++) {
            String candidateSql = SELECT + """
                     WHERE project_id = ? AND run_id = ? AND """ + CLAIMABLE + """
                     ORDER BY shard_index
                     LIMIT 1
                    """;
            TenantGuard.requireProjectScope(candidateSql, "execution_shard");
            List<ExecutionShard> candidates = jdbc.query(candidateSql,
                    ExecutionShardRepositoryJdbc::map, projectId, runId, nowTs, maxAttempts);
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            ExecutionShard candidate = candidates.get(0);
            String claim = """
                    UPDATE execution_shard
                       SET state = 'LEASED', node_id = ?, lease_owner = ?, lease_until = ?,
                           fencing_token = fencing_token + 1, attempt = attempt + 1,
                           started_at = COALESCE(started_at, ?)
                     WHERE id = ? AND project_id = ? AND """ + CLAIMABLE;
            TenantGuard.requireProjectScope(claim, "execution_shard");
            int affected = jdbc.update(claim, nodeId, leaseOwner, Timestamp.from(leaseUntil),
                    nowTs, candidate.id(), projectId, nowTs, maxAttempts);
            if (affected == 1) {
                // 回读拿到新的 fencing_token / attempt / lease_until
                return find(projectId, runId, candidate.shardIndex());
            }
            // 被别的节点抢先，重查下一片
        }
        return Optional.empty();
    }

    @Override
    public int markDone(long projectId, String runId, int shardIndex, long fencingToken,
                        String state, int testCount, int failCount, Instant finishedAt) {
        String sql = """
                UPDATE execution_shard
                   SET state = ?, test_count = ?, fail_count = ?, finished_at = ?,
                       lease_owner = NULL, lease_until = NULL
                 WHERE project_id = ? AND run_id = ? AND shard_index = ?
                   AND fencing_token = ? AND state = 'LEASED'
                """;
        TenantGuard.requireProjectScope(sql, "execution_shard");
        return jdbc.update(sql, state, testCount, failCount, Timestamp.from(finishedAt),
                projectId, runId, shardIndex, fencingToken);
    }

    @Override
    public int expireLeases(long projectId, String runId, Instant now) {
        String sql = """
                UPDATE execution_shard
                   SET state = 'LOST', finished_at = ?
                 WHERE project_id = ? AND run_id = ? AND state = 'LEASED'
                   AND lease_until IS NOT NULL AND lease_until < ?
                """;
        TenantGuard.requireProjectScope(sql, "execution_shard");
        return jdbc.update(sql, Timestamp.from(now), projectId, runId, Timestamp.from(now));
    }

    @Override
    public List<AffectedRun> markLostForNodes(List<Long> nodeIds, Instant now) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(nodeIds.size(), "?"));
        String selectSql = """
                SELECT DISTINCT project_id, run_id
                  FROM execution_shard
                 WHERE state = 'LEASED' AND node_id IN (""" + placeholders + ")";
        TenantGuard.requireProjectScope(selectSql, "execution_shard");
        List<Object> args = new ArrayList<>(nodeIds);
        List<AffectedRun> affected = jdbc.query(selectSql, (rs, i) -> new AffectedRun(
                rs.getLong("project_id"), rs.getString("run_id")), args.toArray());

        String updateSql = """
                UPDATE execution_shard
                   SET state = 'LOST', finished_at = ?
                 WHERE state = 'LEASED' AND node_id IN (""" + placeholders + ") AND project_id > 0";
        TenantGuard.requireProjectScope(updateSql, "execution_shard");
        List<Object> updateArgs = new ArrayList<>();
        updateArgs.add(Timestamp.from(now));
        updateArgs.addAll(nodeIds);
        jdbc.update(updateSql, updateArgs.toArray());
        return affected;
    }

    @Override
    public List<AffectedRun> markLostAllLeased(Instant now) {
        String selectSql = """
                SELECT DISTINCT project_id, run_id
                  FROM execution_shard
                 WHERE state = 'LEASED'
                """;
        TenantGuard.requireProjectScope(selectSql, "execution_shard");
        List<AffectedRun> affected = jdbc.query(selectSql,
                (rs, i) -> new AffectedRun(rs.getLong("project_id"), rs.getString("run_id")));

        String updateSql = """
                UPDATE execution_shard
                   SET state = 'LOST', finished_at = ?
                 WHERE state = 'LEASED' AND project_id > 0
                """;
        TenantGuard.requireProjectScope(updateSql, "execution_shard");
        jdbc.update(updateSql, Timestamp.from(now));
        return affected;
    }

    @Override
    public java.util.Map<String, Integer> countByState(long projectId) {
        String sql = """
                SELECT state, COUNT(*) AS n FROM execution_shard WHERE project_id = ? GROUP BY state
                """;
        TenantGuard.requireProjectScope(sql, "execution_shard");
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            counts.put(rs.getString("state"), rs.getInt("n"));
        }, projectId);
        return counts;
    }

    @Override
    public int recoveredAttempts(long projectId) {
        String sql = """
                SELECT COALESCE(SUM(attempt - 1), 0) AS n FROM execution_shard WHERE project_id = ? AND attempt > 1
                """;
        TenantGuard.requireProjectScope(sql, "execution_shard");
        Integer n = jdbc.queryForObject(sql, Integer.class, projectId);
        return n == null ? 0 : n;
    }

    private static ExecutionShard map(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        Timestamp leaseUntil = rs.getTimestamp("lease_until");
        Timestamp startedAt = rs.getTimestamp("started_at");
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        Long nodeId = rs.getObject("node_id") == null ? null : rs.getLong("node_id");
        String paths = rs.getString("suite_paths");
        List<String> suitePaths = paths == null || paths.isBlank()
                ? List.of()
                : Arrays.stream(paths.split("\n")).filter(s -> !s.isBlank()).toList();
        return new ExecutionShard(rs.getLong("id"), rs.getString("run_id"),
                rs.getLong("project_id"), nodeId, rs.getInt("shard_index"), suitePaths,
                rs.getString("state"), rs.getString("lease_owner"),
                leaseUntil == null ? null : leaseUntil.toInstant(),
                rs.getLong("fencing_token"), rs.getInt("attempt"),
                rs.getInt("test_count"), rs.getInt("fail_count"),
                startedAt == null ? null : startedAt.toInstant(),
                finishedAt == null ? null : finishedAt.toInstant());
    }
}
