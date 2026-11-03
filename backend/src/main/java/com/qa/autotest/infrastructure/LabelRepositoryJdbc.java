package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.LabelRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 技术域标签仓储实现（project_label，label_key 固定为 'domain'）。 */
@Repository
public class LabelRepositoryJdbc implements LabelRepository {

    private final JdbcTemplate jdbc;

    public LabelRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> values(long projectId, String labelKey) {
        String sql = """
                SELECT label_value FROM project_label
                 WHERE project_id = ? AND label_key = ? ORDER BY label_value
                """;
        TenantGuard.requireProjectScope(sql, "project_label");
        return jdbc.query(sql, (rs, rowNum) -> rs.getString(1), projectId, labelKey);
    }

    @Override
    public void add(long projectId, String labelKey, String labelValue, String description,
                    Long operatorId) {
        jdbc.update("""
                INSERT INTO project_label
                  (project_id, label_key, label_value, description, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE description = VALUES(description), updated_by = VALUES(updated_by)
                """, projectId, labelKey, labelValue, description, operatorId, operatorId);
    }

    @Override
    public void remove(long projectId, String labelKey, String labelValue) {
        String sql = """
                DELETE FROM project_label WHERE project_id = ? AND label_key = ? AND label_value = ?
                """;
        TenantGuard.requireProjectScope(sql, "project_label");
        jdbc.update(sql, projectId, labelKey, labelValue);
    }

    @Override
    public boolean exists(long projectId, String labelKey, String labelValue) {
        String sql = """
                SELECT COUNT(*) FROM project_label
                 WHERE project_id = ? AND label_key = ? AND label_value = ?
                """;
        TenantGuard.requireProjectScope(sql, "project_label");
        Integer count = jdbc.queryForObject(sql, Integer.class, projectId, labelKey, labelValue);
        return count != null && count > 0;
    }
}
