package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.ReportTemplateRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.report.ReportTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 报告模板仓储（TPLX-01）。字段单独一张表，随模板级联删除。 */
@Repository
public class ReportTemplateRepositoryJdbc implements ReportTemplateRepository {

    private static final RowMapper<ReportTemplate> MAPPER = (rs, rowNum) -> new ReportTemplate(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("version"),
            rs.getBoolean("is_default"),
            List.of(),
            rs.getObject("created_by") == null ? null : rs.getLong("created_by"),
            JdbcSupport.instant(rs.getTimestamp("created_at")));

    private static final String SELECT = """
            SELECT id, project_id, name, description, version, is_default, created_by, created_at
              FROM report_template
            """;

    private static final String SELECT_FIELDS = """
            SELECT field_key, sort_order, title_override
              FROM report_template_field
             WHERE project_id = ? AND template_id = ?
             ORDER BY sort_order ASC, id ASC
            """;

    private static final String INSERT = """
            INSERT INTO report_template (project_id, name, description, version, is_default)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_FIELD = """
            INSERT INTO report_template_field (project_id, template_id, field_key, sort_order,
                                               title_override, created_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public ReportTemplateRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ReportTemplate> listVisible(long projectId) {
        // 平台级（project_id = 0）在前：它是默认值来源，界面按这个顺序展示更自然
        String sql = SELECT + " WHERE project_id IN (?, 0) ORDER BY project_id ASC, id ASC";
        TenantGuard.requireProjectScope(sql, "report_template");
        List<ReportTemplate> templates = jdbc.query(sql, MAPPER, projectId);
        List<ReportTemplate> withFields = new ArrayList<>(templates.size());
        for (ReportTemplate template : templates) {
            withFields.add(withFields(template));
        }
        return withFields;
    }

    @Override
    public Optional<ReportTemplate> find(long projectId, long id) {
        String sql = SELECT + " WHERE project_id IN (?, 0) AND id = ?";
        TenantGuard.requireProjectScope(sql, "report_template");
        return jdbc.query(sql, MAPPER, projectId, id).stream().findFirst().map(this::withFields);
    }

    @Override
    public Optional<ReportTemplate> findByName(long projectId, String name) {
        String sql = SELECT + " WHERE project_id = ? AND name = ? ORDER BY id ASC";
        TenantGuard.requireProjectScope(sql, "report_template");
        return jdbc.query(sql, MAPPER, projectId, name).stream().findFirst().map(this::withFields);
    }

    @Override
    public boolean existsInOtherProject(long projectId, long id) {
        String sql = "SELECT COUNT(*) FROM report_template WHERE id = ? AND project_id <> ? AND project_id <> 0";
        TenantGuard.requireProjectScope(sql, "report_template");
        Integer count = jdbc.queryForObject(sql, Integer.class, id, projectId);
        return count != null && count > 0;
    }

    @Override
    public long insert(ReportTemplate draft, Long operatorId) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, draft.projectId());
            ps.setString(2, draft.name());
            ps.setString(3, draft.description());
            ps.setInt(4, draft.version());
            ps.setBoolean(5, draft.isDefault());
            return ps;
        }, holder);
        Number key = holder.getKey();
        if (key == null) {
            throw new IllegalStateException("report_template 插入未返回主键");
        }
        return key.longValue();
    }

    @Override
    public void update(long projectId, long id, String name, String description, Long operatorId) {
        String sql = """
                UPDATE report_template
                   SET name = ?, description = ?, version = version + 1, updated_by = ?
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "report_template");
        jdbc.update(sql, name, description, operatorId, projectId, id);
    }

    @Override
    public void delete(long projectId, long id) {
        String sql = "DELETE FROM report_template WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(sql, "report_template");
        jdbc.update(sql, projectId, id);
    }

    @Override
    public void replaceFields(long projectId, long templateId, List<ReportTemplate.Field> fields,
                              Long operatorId) {
        String del = "DELETE FROM report_template_field WHERE project_id = ? AND template_id = ?";
        TenantGuard.requireProjectScope(del, "report_template_field");
        jdbc.update(del, projectId, templateId);
        for (ReportTemplate.Field field : fields) {
            jdbc.update(INSERT_FIELD, projectId, templateId, field.fieldKey(),
                    field.sortOrder(), field.titleOverride(), operatorId);
        }
    }

    @Override
    public Optional<ReportTemplate> findDefault(long projectId) {
        // 项目级默认优先（project_id 降序：项目号 > 0 排在 0 前面）
        String sql = SELECT + """
                 WHERE project_id IN (?, 0) AND is_default = 1
                 ORDER BY project_id DESC, id ASC
                 LIMIT 1
                """;
        TenantGuard.requireProjectScope(sql, "report_template");
        return jdbc.query(sql, MAPPER, projectId).stream().findFirst().map(this::withFields);
    }

    @Override
    public void markDefault(long projectId, long id) {
        String clear = "UPDATE report_template SET is_default = 0 WHERE project_id IN (?, 0)";
        TenantGuard.requireProjectScope(clear, "report_template");
        jdbc.update(clear, projectId);
        String set = "UPDATE report_template SET is_default = 1 WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(set, "report_template");
        jdbc.update(set, projectId, id);
    }

    private ReportTemplate withFields(ReportTemplate template) {
        TenantGuard.requireProjectScope(SELECT_FIELDS, "report_template_field");
        List<ReportTemplate.Field> fields = jdbc.query(SELECT_FIELDS,
                (rs, rowNum) -> new ReportTemplate.Field(
                        rs.getString("field_key"),
                        rs.getInt("sort_order"),
                        rs.getString("title_override")),
                template.projectId(), template.id());
        return new ReportTemplate(template.id(), template.projectId(), template.name(),
                template.description(), template.version(), template.isDefault(), fields,
                template.createdBy(), template.createdAt());
    }
}
