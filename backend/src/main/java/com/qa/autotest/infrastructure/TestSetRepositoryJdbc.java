package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.TestSetRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestSet;
import com.qa.autotest.domain.common.PageResult;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 用例集仓储实现。 */
@Repository
public class TestSetRepositoryJdbc implements TestSetRepository {

    private static final String SELECT = """
            SELECT s.id, s.project_id, s.set_code, s.name, s.description, s.owner_id, s.set_status,
                   (SELECT COUNT(*) FROM test_set_item i WHERE i.test_set_id = s.id) AS item_count
              FROM test_set s
            """;

    private static final RowMapper<TestSet> MAPPER = (rs, rowNum) -> new TestSet(
            rs.getLong("id"), rs.getLong("project_id"), rs.getString("set_code"),
            rs.getString("name"), rs.getString("description"),
            rs.getObject("owner_id") == null ? null : rs.getLong("owner_id"),
            rs.getString("set_status"), rs.getInt("item_count"));

    private final JdbcTemplate jdbc;

    public TestSetRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<TestSet> page(long projectId, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE s.project_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.set_code LIKE ? OR s.name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        String countSql = "SELECT COUNT(*) FROM test_set s" + where;
        TenantGuard.requireProjectScope(countSql, "test_set");
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        String listSql = SELECT + where + " ORDER BY s.id DESC LIMIT ? OFFSET ?";
        TenantGuard.requireProjectScope(listSql, "test_set");
        List<TestSet> rows = jdbc.query(listSql, MAPPER, args.toArray());
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<TestSet> find(long projectId, long id) {
        String sql = SELECT + " WHERE s.project_id = ? AND s.id = ?";
        TenantGuard.requireProjectScope(sql, "test_set");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, id));
    }

    @Override
    public long insert(TestSet testSet, Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO test_set
                      (project_id, set_code, name, description, owner_id, set_status,
                       created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, testSet.projectId());
            ps.setString(2, testSet.setCode());
            ps.setString(3, testSet.name());
            ps.setString(4, testSet.description());
            ps.setObject(5, testSet.ownerId(), java.sql.Types.BIGINT);
            ps.setString(6, testSet.setStatus() == null ? "active" : testSet.setStatus());
            ps.setObject(7, operatorId, java.sql.Types.BIGINT);
            ps.setObject(8, operatorId, java.sql.Types.BIGINT);
            return ps;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0 : keyHolder.getKey().longValue();
    }

    @Override
    public void update(long projectId, long id, String name, String description, Long operatorId) {
        String sql = """
                UPDATE test_set SET name = ?, description = ?, updated_by = ?
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "test_set");
        jdbc.update(sql, name, description, operatorId, projectId, id);
    }

    @Override
    public void delete(long projectId, long id) {
        String delItems = """
                DELETE FROM test_set_item WHERE test_set_id = ?
                  AND test_set_id IN (SELECT id FROM test_set WHERE project_id = ? AND id = ?)
                """;
        TenantGuard.requireProjectScope(delItems, "test_set_item");
        jdbc.update(delItems, id, projectId, id);
        String delSet = "DELETE FROM test_set WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(delSet, "test_set");
        jdbc.update(delSet, projectId, id);
    }

    @Override
    public List<TestCase> items(long projectId, long testSetId) {
        String sql = """
                SELECT c.id, c.project_id, c.case_code, c.title, c.case_kind, c.priority,
                       c.precondition, c.expected, c.domain_tag, c.review_status, c.source,
                       c.capability, c.description
                  FROM test_set_item i
                  JOIN test_case c ON c.id = i.test_case_id
                  JOIN test_set s ON s.id = i.test_set_id
                 WHERE s.project_id = ? AND i.test_set_id = ?
                 ORDER BY i.seq_no
                """;
        TenantGuard.requireProjectScope(sql, "test_set_item");
        return jdbc.query(sql, (rs, rowNum) -> new TestCase(
                rs.getLong("id"), rs.getLong("project_id"), rs.getString("case_code"),
                rs.getString("title"), rs.getString("case_kind"), rs.getString("priority"),
                rs.getString("precondition"), rs.getString("expected"), rs.getString("domain_tag"),
                rs.getString("review_status"), rs.getString("source"), rs.getString("capability"),
                rs.getString("description")), projectId, testSetId);
    }

    @Override
    public void addItem(long projectId, long testSetId, long testCaseId, int seqNo,
                        Long operatorId) {
        Integer maxSeq = jdbc.queryForObject("""
                SELECT COALESCE(MAX(seq_no), 0) FROM test_set_item
                 WHERE test_set_id IN (SELECT id FROM test_set WHERE project_id = ? AND id = ?)
                """, Integer.class, projectId, testSetId);
        int next = seqNo > 0 ? seqNo : (maxSeq == null ? 1 : maxSeq + 1);
        jdbc.update("""
                INSERT INTO test_set_item (test_set_id, test_case_id, seq_no, item_source,
                                           created_by, updated_by)
                VALUES (?, ?, ?, 'manual', ?, ?)
                """, testSetId, testCaseId, next, operatorId, operatorId);
    }

    @Override
    public void removeItem(long projectId, long testSetId, long testCaseId) {
        String sql = """
                DELETE FROM test_set_item WHERE test_set_id = ? AND test_case_id = ?
                  AND test_set_id IN (SELECT id FROM test_set WHERE project_id = ? AND id = ?)
                """;
        TenantGuard.requireProjectScope(sql, "test_set_item");
        jdbc.update(sql, testSetId, testCaseId, projectId, testSetId);
    }

    @Override
    public void reorder(long projectId, long testSetId, List<Long> caseIds, Long operatorId) {
        int seq = 1;
        for (Long caseId : caseIds) {
            String sql = """
                    UPDATE test_set_item SET seq_no = ?, updated_by = ?
                     WHERE test_set_id = ? AND test_case_id = ?
                       AND test_set_id IN (SELECT id FROM test_set WHERE project_id = ? AND id = ?)
                    """;
            TenantGuard.requireProjectScope(sql, "test_set_item");
            jdbc.update(sql, seq, operatorId, testSetId, caseId, projectId, testSetId);
            seq++;
        }
    }

    @Override
    public boolean codeExists(long projectId, String setCode, Long excludeId) {
        String sql = """
                SELECT COUNT(*) FROM test_set
                 WHERE project_id = ? AND set_code = ? AND id <> ?
                """;
        TenantGuard.requireProjectScope(sql, "test_set");
        Integer count = jdbc.queryForObject(sql, Integer.class, projectId, setCode,
                excludeId == null ? -1L : excludeId);
        return count != null && count > 0;
    }
}
