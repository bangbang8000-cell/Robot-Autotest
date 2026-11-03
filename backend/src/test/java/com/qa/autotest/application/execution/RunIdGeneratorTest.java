package com.qa.autotest.application.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** RUN_ID 生成单测。 */
class RunIdGeneratorTest {

    @Test
    @DisplayName("格式固定 RUN-yyyyMMddHHmmss-xxxxxx（Asia/Shanghai）")
    void 格式与时区() {
        RunIdGenerator generator = new RunIdGenerator();
        String runId = generator.next("PERF-IPERF-200G-001",
                Instant.parse("2026-11-02T16:00:00Z"));
        assertTrue(runId.matches("RUN-20261103000000-[0-9a-f]{6}"), runId);
    }

    @Test
    @DisplayName("1000 次生成无重复")
    void 并发不重复() {
        RunIdGenerator generator = new RunIdGenerator();
        Set<String> ids = new HashSet<>();
        Instant now = Instant.parse("2026-11-03T02:00:00Z");
        for (int i = 0; i < 1000; i++) {
            ids.add(generator.next("case", now));
        }
        assertEquals(1000, ids.size());
    }
}
