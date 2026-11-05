package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.ExecutionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXEC-10 派发语义：同项目按 priority 再按提交顺序、达 MAX_PARALLEL 排队不拒绝、跨项目互不阻塞、同 runId 在途去重。
 */
class ProjectRunDispatcherTest {

    private static final long PROJECT_A = 1L;
    private static final long PROJECT_B = 2L;

    private ProjectRunDispatcher dispatcher;

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    private ProjectRunDispatcher dispatcher(int maxParallel) {
        ExecutionProperties props = new ExecutionProperties();
        props.setMaxParallel(maxParallel);
        dispatcher = new ProjectRunDispatcher(props);
        return dispatcher;
    }

    @Test
    @DisplayName("同一项目按 priority 降序，同优先级按提交先后")
    void 同项目按优先级再按提交顺序() throws Exception {
        ProjectRunDispatcher queue = dispatcher(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch seedStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);

        queue.submit(PROJECT_A, null, "seed", 0, () -> {
            seedStarted.countDown();
            await(release);
            order.add("seed");
            done.countDown();
        });
        assertTrue(seedStarted.await(5, TimeUnit.SECONDS), "占位 run 应立刻占用唯一线程");

        queue.submit(PROJECT_A, null, "low", 2, () -> {
            order.add("low");
            done.countDown();
        });
        queue.submit(PROJECT_A, null, "high", 8, () -> {
            order.add("high");
            done.countDown();
        });
        queue.submit(PROJECT_A, null, "low-later", 2, () -> {
            order.add("low-later");
            done.countDown();
        });

        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "四条 run 应在超时前跑完");
        assertEquals(List.of("seed", "high", "low", "low-later"), order);
    }

    @Test
    @DisplayName("达到 MAX_PARALLEL 时排队而非拒绝")
    void 达到并发上限排队不拒绝() throws Exception {
        ProjectRunDispatcher queue = dispatcher(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            String runId = "run-" + i;
            queue.submit(PROJECT_A, null, runId, 0, () -> {
                peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                started.countDown();
                await(release);
                active.decrementAndGet();
                done.countDown();
            });
        }

        assertTrue(started.await(10, TimeUnit.SECONDS), "前两条应在上限内立即开跑");
        Thread.sleep(50);
        assertEquals(2, active.get(), "第三条应仍在队列中等待，只有 MAX_PARALLEL 条在跑");
        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "第三条应在释放后补位跑完");
        assertEquals(2, peak.get(), "单项目峰值并发等于 MAX_PARALLEL");
        assertEquals(0, queue.inFlightCount());
    }

    @Test
    @DisplayName("不同项目分道并行，互不阻塞")
    void 跨项目互不阻塞() throws Exception {
        ProjectRunDispatcher queue = dispatcher(1);
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        queue.submit(PROJECT_A, null, "a", 0, () -> {
            await(bStarted);
            done.countDown();
        });
        queue.submit(PROJECT_B, null, "b", 0, () -> {
            bStarted.countDown();
            done.countDown();
        });

        assertTrue(done.await(10, TimeUnit.SECONDS),
                "项目 A 占满自己的 1 个线程时，项目 B 仍应立即开跑");
        assertEquals(2, queue.laneCount());
    }

    @Test
    @DisplayName("同一 runId 在途重复提交只执行一次")
    void 在途去重() throws Exception {
        ProjectRunDispatcher queue = dispatcher(1);
        AtomicInteger count = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        queue.submit(PROJECT_A, null, "run-dup", 0, () -> {
            count.incrementAndGet();
            started.countDown();
            await(release);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        queue.submit(PROJECT_A, null, "run-dup", 0, count::incrementAndGet);
        release.countDown();

        Thread.sleep(200);
        assertEquals(1, count.get(), "重复提交必须被丢弃");
        assertEquals(0, queue.inFlightCount());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
