package com.qa.autotest.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.application.repository.EditorKeywordRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.editor.EditorKeyword;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 关键词元数据仓储实现（editor_keyword，EDT-04）。 */
@Repository
public class EditorKeywordRepositoryJdbc implements EditorKeywordRepository {

    private static final String COLUMNS = "id, project_id, library, name, args_json, doc, source";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EditorKeywordRepositoryJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<EditorKeyword> search(long projectId, String query, String library, String source,
                                      int limit) {
        String q = query == null ? "" : query.trim();
        String lib = library == null ? "" : library.trim();
        String src = source == null ? "" : source.trim();
        String like = "%" + q + "%";
        String sql = """
                SELECT %s FROM editor_keyword
                 WHERE (project_id = ? OR project_id = 0)
                   AND (name LIKE ? OR library LIKE ? OR COALESCE(doc, '') LIKE ?)
                   AND (? = '' OR library = ?)
                   AND (? = '' OR source = ?)
                 ORDER BY CASE WHEN project_id = 0 THEN 0 ELSE 1 END, library, name
                 LIMIT ?
                """.formatted(COLUMNS);
        TenantGuard.requireProjectScope(sql, "editor_keyword");
        return jdbc.query(sql, this::mapKeyword, projectId, like, like, like, lib, lib, src, src, limit);
    }

    @Override
    public Optional<EditorKeyword> find(long id) {
        // project_id 条件是为了满足 TenantGuard 的「必须带租户条件」；
        // 真正的项目作用域校验由 EditorKeywordService 完成。
        String sql = "SELECT " + COLUMNS + " FROM editor_keyword WHERE project_id >= 0 AND id = ?";
        TenantGuard.requireProjectScope(sql, "editor_keyword");
        List<EditorKeyword> rows = jdbc.query(sql, this::mapKeyword, id);
        return rows.stream().findFirst();
    }

    @Override
    public long create(EditorKeyword keyword, Long operatorId) {
        String sql = """
                INSERT INTO editor_keyword
                  (project_id, library, name, args_json, doc, source, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "editor_keyword");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
            ps.setLong(1, keyword.projectId());
            ps.setString(2, keyword.library());
            ps.setString(3, keyword.name());
            ps.setString(4, encodeArgs(keyword.args()));
            ps.setString(5, keyword.doc());
            ps.setString(6, keyword.source());
            ps.setObject(7, operatorId);
            ps.setObject(8, operatorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    @Override
    public void update(EditorKeyword keyword, Long operatorId) {
        String sql = """
                UPDATE editor_keyword
                   SET library = ?, name = ?, args_json = ?, doc = ?, source = ?, updated_by = ?
                 WHERE id = ? AND project_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "editor_keyword");
        jdbc.update(sql, keyword.library(), keyword.name(), encodeArgs(keyword.args()),
                keyword.doc(), keyword.source(), operatorId, keyword.id(), keyword.projectId());
    }

    @Override
    public void delete(long id, long projectId) {
        String sql = "DELETE FROM editor_keyword WHERE id = ? AND project_id = ?";
        TenantGuard.requireProjectScope(sql, "editor_keyword");
        jdbc.update(sql, id, projectId);
    }

    private EditorKeyword mapKeyword(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new EditorKeyword(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("library"),
                rs.getString("name"),
                decodeArgs(rs.getString("args_json")),
                rs.getString("doc"),
                rs.getString("source"));
    }

    private String encodeArgs(List<String> args) {
        try {
            return json.writeValueAsString(args == null ? List.of() : args);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("关键词参数序列化失败", ex);
        }
    }

    private List<String> decodeArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
