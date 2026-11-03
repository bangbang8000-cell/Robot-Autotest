package com.qa.autotest.application.execution;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 推送限流：每批 ≤50 行；待发堆积超 20000 行时丢最旧并补提示行。 */
class LogBatcherTest {

    private final LogRingBuffer ring = new LogRingBuffer(2000);
    private final LogBatcher batcher = new LogBatcher(ring);

    private static RunLogService.LogLine line(long seq) {
        return new RunLogService.LogLine(seq, Instant.parse("2026-11-03T02:00:00Z"),
                "pabot", "INFO", "line-" + seq);
    }

    @Test
    void 单批最多五十行() {
        for (long seq = 1; seq <= 100; seq++) {
            batcher.offer(line(seq));
        }
        assertEquals(50, batcher.drain(50).size());
        assertEquals(50, batcher.drain(50).size());
    }

    @Test
    void 超过上限丢最旧并生成省略提示() {
        int total = LogBatcher.MAX_PENDING + 5;
        for (long seq = 1; seq <= total; seq++) {
            batcher.offer(line(seq));
        }
        assertEquals(LogBatcher.MAX_PENDING, batcher.pendingCount());

        List<RunLogService.LogLine> first = batcher.drain(1);
        assertEquals(1, first.size());
        assertTrue(first.get(0).text().startsWith("已省略 5 行"));
    }

    @Test
    void 重连补最近两百行() {
        for (long seq = 1; seq <= 300; seq++) {
            batcher.offer(line(seq));
        }
        assertEquals(200, batcher.reconnect(200).size());
        assertEquals(101, batcher.reconnect(200).get(0).seq());
    }
}
