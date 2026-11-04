package com.qa.autotest.application.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * RUN_ID 生成单测（EXE-09 / WBS #77）。
 *
 * <p>2026-11-04 F1-1 前的版本用「固定时间戳 + 随机末段生成 1000 次，断言 Set 大小为 1000」
 * 验证唯一性 —— 这是<b>用概率验证唯一性</b>，约 2.9% 概率 flaky，且当晚确实命中。
 * 本版全部改为确定性断言（精确 ID 值），另补真并发与守门用例。
 */
class RunIdGeneratorTest {

    /** 上海时间 2026-11-03 10:00:00。 */
    private static final Instant NOW = Instant.parse("2026-11-03T02:00:00Z");

    @Test
    @DisplayName("格式固定 RUN-yyyyMMddHHmmss-xxxxxx（Asia/Shanghai），序列从 0000 起")
    void 格式与时区() {
        RunIdGenerator generator = new RunIdGenerator("a1");
        assertEquals("RUN-20261103000000-a10000", generator.next("PERF-IPERF-200G-001",
                Instant.parse("2026-11-02T16:00:00Z")));
        assertTrue(generator.next("case", NOW).matches("RUN-20261103100000-a1[0-9a-f]{4}"));
    }

    @Test
    @DisplayName("同秒 1000 次确定性无重复（逐条精确断言，不靠概率）")
    void 并发不重复() {
        RunIdGenerator generator = new RunIdGenerator("a1");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String id = generator.next("case", NOW);
            assertEquals("RUN-20261103100000-a1" + String.format(Locale.ROOT, "%04x", i), id);
            ids.add(id);
        }
        assertEquals(1000, ids.size());
    }

    @Test
    @DisplayName("跨秒序列重置，不沿用上一秒的计数")
    void 跨秒重置() {
        RunIdGenerator generator = new RunIdGenerator("b2");
        assertEquals("RUN-20261103100000-b20000", generator.next("case", NOW));
        assertEquals("RUN-20261103100000-b20001", generator.next("case", NOW));
        assertEquals("RUN-20261103100001-b20000", generator.next("case", NOW.plusSeconds(1)));
    }

    @Test
    @DisplayName("真并发：8 线程 × 250 次同秒生成，2000 个 ID 全唯一")
    void 真并发不重复() throws Exception {
        RunIdGenerator generator = new RunIdGenerator("c3");
        int threads = 8;
        int perThread = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<List<String>>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                start.await();
                List<String> out = new ArrayList<>(perThread);
                for (int i = 0; i < perThread; i++) {
                    out.add(generator.next("case", NOW));
                }
                return out;
            });
        }
        List<Future<List<String>>> futures = new ArrayList<>();
        for (Callable<List<String>> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        Set<String> all = new HashSet<>();
        for (Future<List<String>> f : futures) {
            all.addAll(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertEquals(threads * perThread, all.size(), "并发生成出现重复 RUN_ID");
        assertTrue(all.stream().allMatch(s -> s.startsWith("RUN-20261103100000-c3")));
    }

    @Test
    @DisplayName("同秒序列溢出明确失败，不静默复用旧值")
    void 序列溢出() {
        RunIdGenerator generator = new RunIdGenerator("d4");
        for (int i = 0; i <= 0xFFFF; i++) {
            generator.next("case", NOW);
        }
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> generator.next("case", NOW));
        assertTrue(ex.getMessage().contains("序列溢出"), ex.getMessage());
    }

    @Test
    @DisplayName("固定前缀构造器拒绝非法值")
    void 前缀校验() {
        assertThrows(IllegalArgumentException.class, () -> new RunIdGenerator(null));
        assertThrows(IllegalArgumentException.class, () -> new RunIdGenerator("ZZ"));
        assertThrows(IllegalArgumentException.class, () -> new RunIdGenerator("abc"));
    }

    @Test
    @DisplayName("数据库唯一索引 uk_run_run_id 必须存在（生成侧之外的最后一道兜底）")
    void 唯一索引守门() throws Exception {
        Path baseline = locateRoot().resolve("db/migration/V1__baseline.sql");
        String sql = Files.readString(baseline, StandardCharsets.UTF_8);
        assertTrue(sql.contains("UNIQUE KEY uk_run_run_id (run_id)"),
                "run 表唯一索引被移除，跨实例碰撞将失去兜底");
    }

    private static Path locateRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            if (Files.exists(p.resolve("db/migration/V1__baseline.sql"))) {
                return p;
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        throw new IllegalStateException("找不到项目根目录");
    }
}
