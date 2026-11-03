package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.CiTriggerRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.ci.CiTriggerRecord;
import com.qa.autotest.domain.ci.CiWritebackStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** CI 触发记录仓储实现（PLN-03）。 */
@Repository
public class CiTriggerRepositoryJdbc implements CiTriggerRepository {

    private static final RowMapper<CiTriggerRecord> MAPPER = (rs, rowNum) -> new CiTriggerRecord(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getLong("plan_id"),
            rs.getString("commit_sha"),
            rs.getString("ref"),
            rs.getString("build"),
            rs.getString("repo"),
            rs.getString("run_id"),
            CiWritebackStatus.fromDb(rs.getString("writeback_status")),
            rs.getString("writeback_error"),
            JdbcSupport.instant(rs.getTimestamp("created_at")));

    private static final String SELECT = """
            SELECT id, project_id, plan_id, commit_sha, ref, build, repo, run_id,
                   writeback_status, writeback_error, created_at
              FROM ci_trigger
            """;

    private final JdbcTemplate jdbc;

    public CiTriggerRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CiTriggerRecord> findByPlanAndCommit(long projectId, long planId, String commitSha) {
        String sql = SELECT + " WHERE project_id = ? AND plan_id = ? AND commit_sha = ?";
        TenantGuard.requireProjectScope(sql, "ci_trigger");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, planId, commitSha));
    }

    @Override
    public Optional<CiTriggerRecord> findByRunId(long projectId, String runId) {
        String sql = SELECT + " WHERE project_id = ? AND run_id = ?";
        TenantGuard.requireProjectScope(sql, "ci_trigger");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, runId));
    }

    @Override
    public long insert(CiTriggerRecord draft, Long operatorId) {
        String sql = """
                INSERT INTO ci_trigger (project_id, plan_id, commit_sha, ref, build, repo,
                                        run_id, writeback_status, writeback_error,
                                        created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "ci_trigger");
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, draft.projectId());
            ps.setLong(2, draft.planId());
            ps.setString(3, draft.commitSha());
            ps.setString(4, draft.ref());
            ps.setString(5, draft.build());
            ps.setString(6, draft.repo());
            ps.setString(7, draft.runId());
            ps.setString(8, draft.writebackStatus().dbValue());
            ps.setString(9, draft.writebackError());
            ps.setObject(10, operatorId, java.sql.Types.BIGINT);
            ps.setObject(11, operatorId, java.sql.Types.BIGINT);
            return ps;
        }, key);
        return key.getKey() == null ? 0 : key.getKey().longValue();
    }

    @Override
    public int updateRunId(long projectId, long id, String runId) {
        String sql = "UPDATE ci_trigger SET run_id = ? WHERE id = ? AND project_id = ?";
        TenantGuard.requireProjectScope(sql, "ci_trigger");
        return jdbc.update(sql, runId, id, projectId);
    }

    @Override
    public int updateWriteback(long projectId, long id, CiWritebackStatus status, String error,
                               Long operatorId) {
        String sql = """
                UPDATE ci_trigger
                   SET writeback_status = ?, writeback_error = ?, updated_by = ?
                 WHERE id = ? AND project_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "ci_trigger");
        return jdbc.update(sql, status.dbValue(), error, operatorId, id, projectId);
    }
}
