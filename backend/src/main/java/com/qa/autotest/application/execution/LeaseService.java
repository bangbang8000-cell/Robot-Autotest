package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.Audited;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.ResourceLeaseRepository;
import com.qa.autotest.domain.execution.Lease;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 租约服务（EXE-02）。
 *
 * <p>互斥由仓储的唯一约束保证，本层只负责策略：
 * <ul>
 *   <li>{@link #acquireAll} 全有或全无——任一资源拿不到就释放本批已拿到的；</li>
 *   <li>{@link #heartbeat} 只续自己的 job；</li>
 *   <li>{@link #reclaimExpired} 回收超时租约并返回清单，调用方据此收尾对应 run。</li>
 * </ul>
 */
public class LeaseService {

    /** 申请入参。jobId 由 execution_job 主键给出，runId 用于三方关联。 */
    public record LeaseRequest(String resourceKey, long jobId, String runId, long projectId) {
    }

    /** 重试间隔：足够短以免拖慢排队，又不会把数据库打成热循环。 */
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(250);

    private final ResourceLeaseRepository repo;
    private final Clock clock;
    private final Duration ttl;
    private final Duration heartbeatInterval;
    private final Sleeper sleeper;
    private final AuditService audit;

    public LeaseService(ResourceLeaseRepository repo, Clock clock,
                        Duration ttl, Duration heartbeat, Sleeper sleeper) {
        this(repo, clock, ttl, heartbeat, sleeper, null);
    }

    /** 生产装配用：额外注入审计，强制释放租约时留痕。 */
    public LeaseService(ResourceLeaseRepository repo, Clock clock, Duration ttl, Duration heartbeat,
                        Sleeper sleeper, AuditService audit) {
        this.repo = repo;
        this.clock = clock;
        this.ttl = ttl;
        this.heartbeatInterval = heartbeat;
        this.sleeper = sleeper;
        this.audit = audit;
    }

    public Duration ttl() {
        return ttl;
    }

    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    public boolean acquire(LeaseRequest request) {
        Instant now = clock.instant();
        return repo.tryAcquire(new Lease(request.resourceKey(), request.jobId(), request.runId(),
                request.projectId(), now, now.plus(ttl)));
    }

    /**
     * 按批申请：全有或全无。允许在 maxWait 内重试；用尝试次数约束，
     * 避免固定时钟下因 deadline 永不到来而死循环。
     */
    public boolean acquireAll(List<LeaseRequest> requests, Duration maxWait) {
        if (requests == null || requests.isEmpty()) {
            return true;
        }
        long waitMillis = maxWait == null ? 0 : Math.max(0, maxWait.toMillis());
        int maxAttempts = 1 + (int) (waitMillis / RETRY_INTERVAL.toMillis());
        List<LeaseRequest> acquired = new ArrayList<>();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                sleeper.sleep(RETRY_INTERVAL);
            }
            acquired.clear();
            boolean complete = true;
            for (LeaseRequest request : requests) {
                if (!acquire(request)) {
                    complete = false;
                    break;
                }
                acquired.add(request);
            }
            if (complete) {
                return true;
            }
            releaseAcquired(acquired, "partial_failure");
        }
        return false;
    }

    public int heartbeat(long jobId) {
        return repo.renew(jobId, clock.instant().plus(ttl));
    }

    /** 定时心跳入口：对当前所有 held 的 job 续期。 */
    public int heartbeatAll() {
        int renewed = 0;
        for (Long jobId : repo.findHeldJobIds()) {
            renewed += heartbeat(jobId);
        }
        return renewed;
    }

    public void release(long jobId, String reason) {
        repo.release(jobId, reason);
    }

    /** 按 run 释放：取消/超时/重启恢复三条路径共用，避免调用方各自拼 job_id。 */
    @Audited(action = "lease:force-release", resource = "resource_lease")
    public int releaseRun(String runId, String reason) {
        int released = 0;
        for (Lease lease : repo.findByRunId(runId)) {
            released += repo.releaseOne(lease.resourceKey(), lease.jobId(), reason);
        }
        if (audit != null && released > 0) {
            audit.recordChange("lease:force-release", "resource_lease", runId,
                    "released=" + released, "reason=" + reason);
        }
        return released;
    }

    public List<Lease> reclaimExpired(Instant now) {
        List<Lease> reclaimed = new ArrayList<>();
        for (Lease lease : repo.findExpired(now)) {
            if (repo.releaseOne(lease.resourceKey(), lease.jobId(), "lease_expired") > 0) {
                reclaimed.add(lease);
            }
        }
        return reclaimed;
    }

    private void releaseAcquired(List<LeaseRequest> acquired, String reason) {
        if (acquired.isEmpty()) {
            return;
        }
        release(acquired.get(0).jobId(), reason);
    }
}
