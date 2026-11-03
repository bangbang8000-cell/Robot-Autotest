package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.RetentionRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.report.RetentionArtifact;
import com.qa.autotest.domain.report.RetentionCandidate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 保留策略仓储 JDBC 实现（RPT-06）。
 *
 * <p>只读候选与产物行、只把产物行置 purged；<b>没有也不允许出现删除 run / test_result 的 SQL</b>。
 * 产物路径来自 {@code path} 列，不按目录扫描，避免误删未登记文件。
 */
@Repository
public class RetentionRepositoryJdbc implements RetentionRepository {

    private static final String CANDIDATES = """
            SELECT r.id AS run_db_id,
                   r.run_id,
                   r.started_at,
                   COALESCE(SUM(CASE WHEN a.availability = 'ok' THEN a.size_bytes ELSE 0 END), 0) AS bytes,
                   COUNT(CASE WHEN a.availability = 'ok' THEN 1 END) AS artifact_count,
                   COALESCE(MAX(CASE WHEN a.pinned = 1 THEN 1 ELSE 0 END), 0) AS pinned,
                   COALESCE(MAX(CASE WHEN a.note LIKE '%p0_evidence%' THEN 1 ELSE 0 END), 0) AS p0_evidence,
                   (SELECT COUNT(*) FROM export_record e
                     WHERE e.project_id = r.project_id AND e.run_key = r.run_id
                       AND e.export_status <> 'failed') AS export_count
              FROM `run` r
              LEFT JOIN run_artifact a ON a.run_id = r.id
             WHERE r.project_id = ?
             GROUP BY r.id, r.run_id, r.started_at, r.project_id
             ORDER BY r.started_at DESC, r.id DESC
            """;

    // 通过 run 关联取租户条件，兼容 V4_2 之前 project_id 为 NULL 的历史产物行。
    private static final String ARTIFACTS = """
            SELECT a.id, a.path, COALESCE(a.size_bytes, 0) AS size_bytes, a.pinned, a.note
              FROM run_artifact a
              JOIN `run` r ON r.id = a.run_id
             WHERE r.project_id = ? AND a.run_id = ? AND a.availability = 'ok'
             ORDER BY a.id
            """;

    private static final String TOTAL_BYTES = """
            SELECT COALESCE(SUM(a.size_bytes), 0)
              FROM run_artifact a
              JOIN `run` r ON r.id = a.run_id
             WHERE r.project_id = ? AND a.availability = 'ok'
            """;

    private static final String ENABLED_PROJECTS = "SELECT id FROM `project` WHERE status = 1 ORDER BY id";

    private final JdbcTemplate jdbc;

    public RetentionRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RetentionCandidate> listCandidates(long projectId) {
        TenantGuard.requireProjectScope(CANDIDATES, "run_artifact");
        return jdbc.query(CANDIDATES, (rs, rowNum) -> new RetentionCandidate(
                rs.getLong("run_db_id"),
                rs.getString("run_id"),
                JdbcSupport.instant(rs.getTimestamp("started_at")),
                rs.getLong("bytes"),
                rs.getInt("artifact_count"),
                rs.getInt("pinned") == 1,
                rs.getInt("p0_evidence") == 1,
                rs.getInt("export_count") > 0), projectId);
    }

    @Override
    public List<RetentionArtifact> listArtifacts(long projectId, long runDbId) {
        TenantGuard.requireProjectScope(ARTIFACTS, "run_artifact");
        return jdbc.query(ARTIFACTS, (rs, rowNum) -> new RetentionArtifact(
                rs.getLong("id"),
                rs.getString("path"),
                rs.getLong("size_bytes"),
                rs.getInt("pinned") == 1,
                rs.getString("note")), projectId, runDbId);
    }

    @Override
    public long totalBytes(long projectId) {
        TenantGuard.requireProjectScope(TOTAL_BYTES, "run_artifact");
        Long total = jdbc.queryForObject(TOTAL_BYTES, Long.class, projectId);
        return total == null ? 0L : total;
    }

    @Override
    public void markPurged(long projectId, long runDbId, List<Long> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return;
        }
        String placeholders = artifactIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "UPDATE run_artifact SET availability = 'purged'"
                + " WHERE run_id = ? AND id IN (" + placeholders + ")"
                + " AND run_id IN (SELECT id FROM `run` WHERE project_id = ?)";
        TenantGuard.requireProjectScope(sql, "run_artifact");
        Object[] args = new Object[artifactIds.size() + 2];
        args[0] = runDbId;
        for (int i = 0; i < artifactIds.size(); i++) {
            args[i + 1] = artifactIds.get(i);
        }
        args[artifactIds.size() + 1] = projectId;
        jdbc.update(sql, args);
    }

    @Override
    public List<Long> listProjectIds() {
        // project 是租户根表，这里刻意做全局查询（定时任务按项目逐个切上下文）。
        return jdbc.queryForList(ENABLED_PROJECTS, Long.class);
    }
}
