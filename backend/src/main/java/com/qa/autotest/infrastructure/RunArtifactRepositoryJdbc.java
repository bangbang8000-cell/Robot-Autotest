package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.RunArtifact;
import com.qa.autotest.application.repository.RunArtifactRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/** 产物登记。同一 (project, run, kind, shard) 覆盖更新，避免重复行。 */
@Repository
public class RunArtifactRepositoryJdbc implements RunArtifactRepository {

    private final JdbcTemplate jdbc;

    public RunArtifactRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(long projectId, long runDbId, String kind, String path,
                       String sha256, long sizeBytes, int shardNo, String note) {
        String find = """
                SELECT id FROM run_artifact
                 WHERE project_id = ? AND run_id = ? AND kind = ? AND shard_no = ?
                """;
        TenantGuard.requireProjectScope(find, "run_artifact");
        List<Long> ids = jdbc.query(find,
                (rs, rowNum) -> rs.getLong("id"), projectId, runDbId, kind, shardNo);
        if (!ids.isEmpty()) {
            String update = """
                    UPDATE run_artifact
                       SET path = ?, sha256 = ?, size_bytes = ?, note = ?,
                           availability = 'ok', updated_at = CURRENT_TIMESTAMP(3)
                     WHERE id = ? AND project_id = ?
                    """;
            TenantGuard.requireProjectScope(update, "run_artifact");
            jdbc.update(update, path, sha256, sizeBytes, note, ids.get(0), projectId);
            return;
        }
        String insert = """
                INSERT INTO run_artifact (project_id, run_id, kind, path, sha256, size_bytes,
                                          shard_no, note, availability)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ok')
                """;
        TenantGuard.requireProjectScope(insert, "run_artifact");
        jdbc.update(insert, projectId, runDbId, kind, path, sha256, sizeBytes, shardNo, note);
    }

    @Override
    public List<RunArtifact> list(long projectId, long runDbId) {
        String sql = """
                SELECT id, project_id, run_id, kind, path, sha256, size_bytes, shard_no,
                       availability, note, created_at
                  FROM run_artifact
                 WHERE project_id = ? AND run_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "run_artifact");
        return jdbc.query(sql, (rs, rowNum) -> new RunArtifact(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getLong("run_id"),
                rs.getString("kind"),
                rs.getString("path"),
                rs.getString("sha256"),
                rs.getLong("size_bytes"),
                rs.getInt("shard_no"),
                rs.getString("availability"),
                rs.getString("note"),
                instant(rs.getTimestamp("created_at"))), projectId, runDbId);
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
