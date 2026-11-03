package com.qa.autotest.application.repository;

import com.qa.autotest.application.execution.LeaseView;
import com.qa.autotest.domain.execution.Lease;

import java.time.Instant;
import java.util.List;

/**
 * 资源租约仓储。互斥由 resource_key 唯一约束保证，tryAcquire 不做 SELECT 预判。
 */
public interface ResourceLeaseRepository {

    boolean tryAcquire(Lease lease);

    int renew(long jobId, Instant expiresAt);

    int release(long jobId, String reason);

    int releaseOne(String resourceKey, long jobId, String reason);

    List<Lease> findByJob(long jobId);

    /** 取消/超时按 run 释放：run 与 job 的关联是 job_no=runId，仓储侧按 run_id 列反查。 */
    List<Lease> findByRunId(String runId);

    List<Lease> findExpired(Instant now);

    /** 定时心跳的枚举入口：当前处于 held 状态的 job 集合。 */
    List<Long> findHeldJobIds();

    List<LeaseView> listByProject(long projectId);

    void recordHistory(Lease lease, Instant releasedAt, String state, String reason);
}
