package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.RunQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 执行队列状态回写（EXEC-10 残余）。
 *
 * <p>不直接按 {@code run_id} 写 {@code execution_queue}：该表只有 {@code job_id} 外键，
 * 走 {@code execution_job.job_no = run_id} 反查，顺带把 project 作用域带进 SQL
 * （{@code execution_queue} 本身不在 {@code TenantGuard.TENANT_TABLES} 里，
 * 用 join 保证不会跨项目改到别人的队列行）。
 */
@Repository
public class RunQueueRepositoryJdbc implements RunQueueRepository {

    private static final Logger LOG = LoggerFactory.getLogger(RunQueueRepositoryJdbc.class);

    private final JdbcTemplate jdbc;

    public RunQueueRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(long projectId, String runId, int priority) {
        String insert = """
                INSERT INTO execution_queue (job_id, priority, queue_state, enqueued_at)
                SELECT id, ?, 'waiting', CURRENT_TIMESTAMP(3)
                  FROM execution_job
                 WHERE project_id = ? AND job_no = ?
                """;
        try {
            int rows = jdbc.update(insert, priority, projectId, runId);
            if (rows == 0) {
                LOG.warn("队列回写跳过：run {} 在项目 {} 下没有 execution_job 记录", runId, projectId);
            }
        } catch (DuplicateKeyException resubmitted) {
            // 同一 run 幂等重放：回到 waiting，并清掉上一次的 started_at，避免位次统计出现「已开始又在排队」
            String update = """
                    UPDATE execution_queue eq
                      JOIN execution_job ej ON eq.job_id = ej.id
                       SET eq.priority = ?, eq.queue_state = 'waiting', eq.started_at = NULL,
                           eq.enqueued_at = CURRENT_TIMESTAMP(3),
                           eq.updated_at = CURRENT_TIMESTAMP(3)
                     WHERE ej.project_id = ? AND ej.job_no = ?
                    """;
            jdbc.update(update, priority, projectId, runId);
        }
    }

    @Override
    public void markRunning(long projectId, String runId) {
        changeState(projectId, runId, "running", true);
    }

    @Override
    public void markDone(long projectId, String runId) {
        changeState(projectId, runId, "done", false);
    }

    private void changeState(long projectId, String runId, String state, boolean keepStartedAt) {
        String sql = keepStartedAt
                ? """
                  UPDATE execution_queue eq
                    JOIN execution_job ej ON eq.job_id = ej.id
                     SET eq.queue_state = ?,
                         eq.started_at = COALESCE(eq.started_at, CURRENT_TIMESTAMP(3)),
                         eq.updated_at = CURRENT_TIMESTAMP(3)
                   WHERE ej.project_id = ? AND ej.job_no = ?
                  """
                : """
                  UPDATE execution_queue eq
                    JOIN execution_job ej ON eq.job_id = ej.id
                     SET eq.queue_state = ?, eq.updated_at = CURRENT_TIMESTAMP(3)
                   WHERE ej.project_id = ? AND ej.job_no = ?
                  """;
        jdbc.update(sql, state, projectId, runId);
    }
}
