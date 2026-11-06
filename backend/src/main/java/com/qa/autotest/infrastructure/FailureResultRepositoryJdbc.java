package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.FailureResultRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 失败行读取实现（ORCH-03）。 */
@Repository
public class FailureResultRepositoryJdbc implements FailureResultRepository {

    /**
     * 用例级失败文本只有 {@code test_result.failure_msg} 这一列：
     * {@code failure_reason} / {@code failure_message} / {@code failure_class} 都在 {@code run} 表上，
     * 是**整轮**的结论，不是每条用例的。簇必须按用例聚合，所以只能读 failure_msg
     * （PRD 原文写的是 run 表字段，实现按真实列订正，见 §修订记录）。
     */
    private static final String SELECT_FAILED = """
            SELECT t.id           AS result_id,
                   t.case_id      AS case_id,
                   t.verdict      AS verdict,
                   t.failure_msg  AS failure_msg,
                   c.case_code    AS case_code,
                   c.title        AS case_title
              FROM test_result t
              JOIN run r ON r.id = t.run_id
              LEFT JOIN test_case c ON c.id = t.case_id
             WHERE r.project_id = ?
               AND r.run_id = ?
               AND t.verdict IN ('FAIL', 'ERROR')
             ORDER BY t.id ASC
            """;

    private static final String SELECT_RUN = """
            SELECT run_id, run_status, failure_reason, failure_class
              FROM run
             WHERE project_id = ? AND run_id = ?
            """;

    private final JdbcTemplate jdbc;

    public FailureResultRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FailureRow> failedRows(long projectId, String runId) {
        // 租户条件由 JOIN 的 run.project_id 提供：test_result 本身没有 project_id 列，
        // 因此这里登记的守卫表是 run 而不是 test_result（后者直接登记会要求一条它永远写不出的条件）。
        TenantGuard.requireProjectScope(SELECT_FAILED, "run");
        return jdbc.query(SELECT_FAILED, (rs, rowNum) -> new FailureRow(
                rs.getLong("result_id"),
                rs.getObject("case_id") == null ? null : rs.getLong("case_id"),
                rs.getString("case_code"),
                rs.getString("case_title"),
                rs.getString("verdict"),
                rs.getString("failure_msg")), projectId, runId);
    }

    @Override
    public RunFailureContext runContext(long projectId, String runId) {
        TenantGuard.requireProjectScope(SELECT_RUN, "run");
        List<RunFailureContext> rows = jdbc.query(SELECT_RUN, (rs, rowNum) -> new RunFailureContext(
                rs.getString("run_id"),
                rs.getString("run_status"),
                rs.getString("failure_reason"),
                rs.getString("failure_class")), projectId, runId);
        return rows.isEmpty() ? new RunFailureContext(runId, null, null, null) : rows.get(0);
    }
}
