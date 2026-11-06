package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.RunMetadataRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 运行记录补录 metadata 仓储实现。 */
@Repository
public class RunMetadataRepositoryJdbc implements RunMetadataRepository {

    private static final String SELECT = """
            SELECT meta_key, meta_value
              FROM run_metadata
             WHERE project_id = ? AND run_id = ?
             ORDER BY id
            """;

    private static final String UPSERT = """
            INSERT INTO run_metadata (project_id, run_id, meta_key, meta_value, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE meta_value = ?, updated_by = ?
            """;

    private static final String DELETE = """
            DELETE FROM run_metadata
             WHERE project_id = ? AND run_id = ? AND meta_key = ?
            """;

    private final JdbcTemplate jdbc;

    public RunMetadataRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> findAll(long projectId, String runId) {
        TenantGuard.requireProjectScope(SELECT, "run_metadata");
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query(SELECT, rs -> {
            result.put(rs.getString("meta_key"), rs.getString("meta_value"));
        }, projectId, runId);
        return result;
    }

    @Override
    @Transactional
    public void save(long projectId, String runId, Map<String, String> values,
                     Collection<String> removed, Long operatorId) {
        for (String key : removed) {
            TenantGuard.requireProjectScope(DELETE, "run_metadata");
            jdbc.update(DELETE, projectId, runId, key);
        }
        values.forEach((key, value) -> {
            TenantGuard.requireProjectScope(UPSERT, "run_metadata");
            jdbc.update(UPSERT, projectId, runId, key, value, operatorId, operatorId, value, operatorId);
        });
    }
}
