package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.Lease;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 租约策略：全有或全无、释放可重抢、越权释放影响 0 行、回收写历史。 */
class LeaseServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-03T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private static final long PROJECT = 7L;

    private final InMemoryLeaseRepository repo = new InMemoryLeaseRepository();

    private final LeaseService leases = new LeaseService(repo, CLOCK,
            Duration.ofSeconds(120), Duration.ofSeconds(40), duration -> { });

    @Test
    void 释放后同一资源可再次申请() {
        assertTrue(leases.acquire(request("GPU-01#0", 1L)));
        leases.release(1L, "finished");

        assertEquals(0, repo.size());
        assertTrue(leases.acquire(request("GPU-01#0", 2L)));
        assertEquals(2L, repo.peek("GPU-01#0").orElseThrow().jobId());
        assertEquals(1, repo.history().size());
    }

    @Test
    void 释放他人租约影响行为零() {
        assertTrue(leases.acquire(request("GPU-01#0", 1L)));
        leases.release(2L, "finished");

        assertEquals(1, repo.size());
        assertEquals(1L, repo.peek("GPU-01#0").orElseThrow().jobId());
        assertFalse(leases.acquire(request("GPU-01#0", 2L)));
    }

    @Test
    void 部分失败会释放本批已拿到的全部() {
        repo.seed(new Lease("GPU-01#1", 99L, "RUN-OTHER", PROJECT,
                CLOCK.instant(), CLOCK.instant().plusSeconds(120)));

        boolean acquired = leases.acquireAll(
                List.of(request("GPU-01#0", 1L), request("GPU-01#1", 1L)), Duration.ZERO);

        assertFalse(acquired);
        assertTrue(repo.peek("GPU-01#0").isEmpty());
        assertTrue(repo.peek("GPU-01#1").isPresent());
        assertEquals(99L, repo.peek("GPU-01#1").orElseThrow().jobId());
        assertEquals(1, repo.history().size());
        assertEquals("partial_failure", repo.history().get(0).reason());
    }

    @Test
    void 心跳只续期自己的_job() {
        assertTrue(leases.acquire(request("GPU-01#0", 1L)));

        assertEquals(1, leases.heartbeat(1L));
        assertEquals(0, leases.heartbeat(2L));
        Instant expires = repo.peek("GPU-01#0").orElseThrow().expiresAt();
        assertEquals(CLOCK.instant().plusSeconds(120), expires);
    }

    @Test
    void 回收过期租约并写入历史() {
        Instant now = CLOCK.instant();
        repo.seed(new Lease("GPU-01#0", 1L, "RUN-X", PROJECT,
                now.minusSeconds(200), now.minusSeconds(10)));

        List<Lease> reclaimed = leases.reclaimExpired(now);

        assertEquals(1, reclaimed.size());
        assertEquals("GPU-01#0", reclaimed.get(0).resourceKey());
        assertEquals("RUN-X", reclaimed.get(0).runId());
        assertTrue(repo.peek("GPU-01#0").isEmpty());
        assertEquals("expired", repo.history().get(0).state());
        assertEquals("lease_expired", repo.history().get(0).reason());
    }

    private static LeaseService.LeaseRequest request(String key, long jobId) {
        return new LeaseService.LeaseRequest(key, jobId, "RUN-" + jobId, PROJECT);
    }
}
