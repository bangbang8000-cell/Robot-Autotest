package com.qa.autotest.application.execution;

import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 幂等（TC-v0.4-301）：同一 runRequestId 并发创建只允许落一条。 */
class IdempotencyTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;

    @Test
    void 十并发同_requestId_只落一条运行() throws Exception {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        RunService service = ExecutionTestSupport.runService(repo);
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    TenantContext.set(1L, "admin", PROJECT);
                    try {
                        start.await();
                        return service.create(command()).runId();
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }
            start.countDown();
            List<String> runIds = new ArrayList<>();
            for (Future<String> future : futures) {
                runIds.add(future.get(15, TimeUnit.SECONDS));
            }
            assertEquals(1, runIds.stream().distinct().count());
            assertEquals(1, repo.insertCount());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void 顺序重复提交返回同一条() {
        InMemoryRunRepository repo = new InMemoryRunRepository();
        RunService service = ExecutionTestSupport.runService(repo);
        TenantContext.set(1L, "admin", PROJECT);
        try {
            RunRecord first = service.create(command());
            RunRecord second = service.create(command());
            assertEquals(first.runId(), second.runId());
            assertEquals(1, repo.insertCount());
        } finally {
            TenantContext.clear();
        }
    }

    private RunService.CreateRunCommand command() {
        return new RunService.CreateRunCommand(PROJECT, null,
                List.of("suites/a.robot"), 1, null, List.of(),
                "REQ-1", 0, 600, null, null);
    }
}
