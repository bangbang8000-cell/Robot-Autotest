package com.qa.autotest.application.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-08：租约等待登记只用于运行期展示与耗时回写，这里锁住「登记/结束/重复结束」三个语义。
 */
class LeaseWaitRegistryTest {

    @Test
    void 等待中可查询_结束时返回实际耗时且不重复计时() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T00:00:00Z"));
        LeaseWaitRegistry registry = new LeaseWaitRegistry(clock);

        assertFalse(registry.waiting("RUN-1"));
        registry.begin("RUN-1");
        assertTrue(registry.waiting("RUN-1"));

        clock.advanceMillis(2500);
        assertEquals(2500L, registry.end("RUN-1"));
        assertFalse(registry.waiting("RUN-1"));
        assertEquals(0L, registry.end("RUN-1"), "重复结束不应叠加计时");
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
