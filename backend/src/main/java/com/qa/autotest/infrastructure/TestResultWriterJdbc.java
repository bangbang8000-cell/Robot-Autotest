package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.TestResultWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 用例结果写库。v0.4 最小闭环只写一条运行级汇总（case_id 为空），逐例结果由 v0.5 报告域补。
 */
@Repository
public class TestResultWriterJdbc implements TestResultWriter {

    private final JdbcTemplate jdbc;

    public TestResultWriterJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void write(long projectId, long runDbId, String caseKey, String verdict,
                      long durationMs, String failureMsg, int roundNo) {
        String sql = """
                INSERT INTO test_result (run_id, case_id, round_no, verdict, duration_ms,
                                         failure_msg, created_by, updated_by)
                VALUES (?, NULL, ?, ?, ?, ?, NULL, NULL)
                """;
        jdbc.update(sql, runDbId, roundNo, verdict, durationMs, failureMsg);
        // projectId 用于将来按项目汇总；当前 test_result 通过 run_id 间接归属，保留参数避免调用方改签名。
    }
}
