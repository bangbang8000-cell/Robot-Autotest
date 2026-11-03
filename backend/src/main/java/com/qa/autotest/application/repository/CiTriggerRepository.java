package com.qa.autotest.application.repository;

import com.qa.autotest.domain.ci.CiTriggerRecord;
import com.qa.autotest.domain.ci.CiWritebackStatus;
import java.util.Optional;

/**
 * CI 触发记录仓储端口（PLN-03）。所有 SQL 都带 project_id 条件（TenantGuard 口径）。
 */
public interface CiTriggerRepository {

    /** 幂等查询：(plan_id, commit_sha) 唯一。 */
    Optional<CiTriggerRecord> findByPlanAndCommit(long projectId, long planId, String commitSha);

    /** 运行结束回写时按 RUN_ID 反查。 */
    Optional<CiTriggerRecord> findByRunId(long projectId, String runId);

    /**
     * 插入一行（run_id 先为 null）。唯一键冲突时抛出
     * {@link org.springframework.dao.DataIntegrityViolationException}，由调用方回读兜底。
     */
    long insert(CiTriggerRecord draft, Long operatorId);

    int updateRunId(long projectId, long id, String runId);

    int updateWriteback(long projectId, long id, CiWritebackStatus status, String error,
                        Long operatorId);
}
