package com.qa.autotest.application.execution;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 环形缓冲：容量、丢弃计数、最近 n 行顺序。 */
class LogRingBufferTest {

    private static RunLogService.LogLine line(long seq) {
        return new RunLogService.LogLine(seq, Instant.parse("2026-11-03T02:00:00Z"),
                "pabot", "INFO", "line-" + seq);
    }

    @Test
    void 超出容量丢最旧并计数() {
        LogRingBuffer ring = new LogRingBuffer(3);
        for (long seq = 1; seq <= 5; seq++) {
            ring.add(line(seq));
        }
        assertEquals(5, ring.total());
        assertEquals(2, ring.dropped());
        assertEquals(3, ring.capacity());
    }

    @Test
    void 最近_n_行保持顺序且只返回尾部() {
        LogRingBuffer ring = new LogRingBuffer(10);
        for (long seq = 1; seq <= 10; seq++) {
            ring.add(line(seq));
        }
        var recent = ring.recent(3);
        assertEquals(3, recent.size());
        assertEquals(8, recent.get(0).seq());
        assertEquals(10, recent.get(2).seq());
    }

    @Test
    void 请求行数大于实际时返回全部() {
        LogRingBuffer ring = new LogRingBuffer(10);
        ring.add(line(1));
        assertTrue(ring.recent(200).size() == 1);
        assertTrue(ring.recent(0).isEmpty());
    }
}
