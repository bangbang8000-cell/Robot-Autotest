package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.TrendRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.report.PassRateCount;
import com.qa.autotest.domain.report.RunSnapshot;
import com.qa.autotest.domain.report.TrendEnv;
import com.qa.autotest.domain.report.TrendPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 趋势与通过率只读仓储 JDBC 实现（RPT-04）。
 *
 * <p>test_result 没有 project_id，全部 join {@code run} 取租户条件；每条 SQL 都显式带
 * {@code project_id}，满足 {@link TenantGuard} 第 2 层要求，杜绝跨项目趋势泄漏。
 */
@Repository
public class TrendRepositoryJdbc implements TrendRepository {

    private static final String RUN_SNAPSHOT = """
            SELECT project_id, run_id, run_status, `dut`, `link`, started_at
              FROM `run`
             WHERE project_id = ? AND run_id = ?
            """;

    private static final String CASE_POINTS = """
            SELECT r.run_id, r.started_at, t.verdict, COALESCE(t.duration_ms, 0) AS duration_ms,
                   COALESCE(r.`dut`, '') AS dut, COALESCE(r.`link`, '') AS link
              FROM test_result t
              JOIN `run` r ON r.id = t.run_id
             WHERE r.project_id = ? AND t.case_key = ? AND r.started_at IS NOT NULL
             ORDER BY r.started_at DESC, r.id DESC
             LIMIT ?
            """;

    private static final String CURRENT_CASE_POINT = """
            SELECT r.run_id, r.started_at, t.verdict, COALESCE(t.duration_ms, 0) AS duration_ms,
                   COALESCE(r.`dut`, '') AS dut, COALESCE(r.`link`, '') AS link
              FROM test_result t
              JOIN `run` r ON r.id = t.run_id
             WHERE r.project_id = ? AND r.run_id = ? AND t.case_key = ?
             ORDER BY t.id
             LIMIT 1
            """;

    private static final String CASE_DURATIONS = """
            SELECT COALESCE(t.duration_ms, 0) AS duration_ms
              FROM test_result t
              JOIN `run` r ON r.id = t.run_id
             WHERE r.project_id = ?
               AND t.case_key = ?
               AND COALESCE(r.`dut`, '') = ?
               AND COALESCE(r.`link`, '') = ?
               AND r.run_id <> ?
               AND r.started_at IS NOT NULL
             ORDER BY r.started_at DESC, r.id DESC
             LIMIT ?
            """;

    private static final String PASS_RATE_DAY = """
            SELECT DATE_FORMAT(COALESCE(started_at, created_at), '%Y-%m-%d') AS bucket,
                   COALESCE(SUM(pass_count), 0) AS pass_count,
                   COALESCE(SUM(fail_count), 0) AS fail_count,
                   COALESCE(SUM(skip_count), 0) AS skip_count
              FROM `run`
             WHERE project_id = ? AND run_status IN ('passed', 'failed')
             GROUP BY bucket
             ORDER BY bucket DESC
             LIMIT ?
            """;

    private static final String PASS_RATE_WEEK = """
            SELECT DATE_FORMAT(COALESCE(started_at, created_at), '%x-%v') AS bucket,
                   COALESCE(SUM(pass_count), 0) AS pass_count,
                   COALESCE(SUM(fail_count), 0) AS fail_count,
                   COALESCE(SUM(skip_count), 0) AS skip_count
              FROM `run`
             WHERE project_id = ? AND run_status IN ('passed', 'failed')
             GROUP BY bucket
             ORDER BY bucket DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public TrendRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RunSnapshot> findRun(long projectId, String runId) {
        TenantGuard.requireProjectScope(RUN_SNAPSHOT, "run");
        return JdbcSupport.first(jdbc.query(RUN_SNAPSHOT, (rs, rowNum) -> new RunSnapshot(
                rs.getLong("project_id"),
                rs.getString("run_id"),
                rs.getString("run_status"),
                rs.getString("dut"),
                rs.getString("link"),
                JdbcSupport.instant(rs.getTimestamp("started_at"))), projectId, runId));
    }

    @Override
    public Optional<TrendPoint> findCasePoint(long projectId, String runId, String caseKey) {
        TenantGuard.requireProjectScope(CURRENT_CASE_POINT, "run");
        return JdbcSupport.first(jdbc.query(CURRENT_CASE_POINT, (rs, rowNum) -> mapPoint(rs),
                projectId, runId, caseKey));
    }

    @Override
    public List<TrendPoint> listCasePoints(long projectId, String caseKey, int limit) {
        TenantGuard.requireProjectScope(CASE_POINTS, "run");
        int effectiveLimit = Math.max(1, limit);
        List<TrendPoint> rows = jdbc.query(CASE_POINTS, (rs, rowNum) -> mapPoint(rs),
                projectId, caseKey, effectiveLimit);
        // SQL 按时间倒序取最近 N 条，接口契约要求升序返回。
        List<TrendPoint> ascending = new ArrayList<>(rows);
        Collections.reverse(ascending);
        return ascending;
    }

    @Override
    public List<Long> listCaseDurations(long projectId, String caseKey, String dut, String link,
                                        String excludeRunId, int limit) {
        TenantGuard.requireProjectScope(CASE_DURATIONS, "run");
        return jdbc.queryForList(CASE_DURATIONS, Long.class, projectId, caseKey,
                dut == null ? "" : dut, link == null ? "" : link,
                excludeRunId == null ? "" : excludeRunId, Math.max(1, limit));
    }

    @Override
    public List<PassRateCount> listPassRate(long projectId, String granularity, int limit) {
        String sql = "week".equals(granularity) ? PASS_RATE_WEEK : PASS_RATE_DAY;
        TenantGuard.requireProjectScope(sql, "run");
        int effectiveLimit = Math.max(1, limit);
        List<PassRateCount> rows = jdbc.query(sql, (rs, rowNum) -> new PassRateCount(
                rs.getString("bucket"),
                rs.getInt("pass_count"),
                rs.getInt("fail_count"),
                rs.getInt("skip_count")), projectId, effectiveLimit);
        List<PassRateCount> ascending = new ArrayList<>(rows);
        Collections.reverse(ascending);
        return ascending;
    }

    private static TrendPoint mapPoint(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TrendPoint(
                rs.getString("run_id"),
                JdbcSupport.instant(rs.getTimestamp("started_at")),
                rs.getString("verdict"),
                rs.getLong("duration_ms"),
                TrendEnv.envKey(rs.getString("dut"), rs.getString("link")));
    }
}
