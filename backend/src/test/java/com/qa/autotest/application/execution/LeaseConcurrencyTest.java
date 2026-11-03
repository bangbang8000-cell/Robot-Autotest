package com.qa.autotest.application.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租约互斥并发验证（TC-v0.4-101/102）。
 *
 * <p>仓储为内存实现，用 {@code putIfAbsent} 复刻唯一键语义；MariaDB 侧的同口径验证在
 * {@code docs/evidence/v0.4/lease-conflict-0.sql}，需在 149 上执行。
 */
class LeaseConcurrencyTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-03T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private static final long PROJECT = 7L;

    @Test
    void 同一_key_一百线程成功数恰为一() throws Exception {
        InMemoryLeaseRepository repo = new InMemoryLeaseRepository();
        LeaseService service = new LeaseService(repo, CLOCK,
                Duration.ofSeconds(120), Duration.ofSeconds(40), duration -> { });

        int success = runConcurrently(100, index ->
                service.acquire(new LeaseService.LeaseRequest("GPU-01#0", index, "RUN-" + index, PROJECT)));

        assertEquals(1, success);
        assertEquals(1, repo.size());
    }

    @Test
    void 十个_key_一百线程每个_key_成功数恰为一总计十() throws Exception {
        InMemoryLeaseRepository repo = new InMemoryLeaseRepository();
        LeaseService service = new LeaseService(repo, CLOCK,
                Duration.ofSeconds(120), Duration.ofSeconds(40), duration -> { });

        int success = runConcurrently(100, index ->
                service.acquire(new LeaseService.LeaseRequest(
                        "GPU-01#" + (index % 10), index, "RUN-" + index, PROJECT)));

        assertEquals(10, success);
        assertEquals(10, repo.size());
    }

    private int runConcurrently(int threads, Attempt attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                long jobId = index + 1L;
                futures.add(pool.submit(() -> {
                    start.await();
                    if (attempt.run(jobId)) {
                        success.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        return success.get();
    }

    @FunctionalInterface
    private interface Attempt {
        boolean run(long jobId) throws Exception;
    }
}
