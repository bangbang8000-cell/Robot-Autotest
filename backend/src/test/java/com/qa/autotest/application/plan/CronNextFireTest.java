package com.qa.autotest.application.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cron 解析与未来触发点（PLN-01）。真实输入/输出，不依赖数据库。 */
class CronNextFireTest {

    private static final Instant BASE = Instant.parse("2026-11-03T02:00:00Z");

    @Test
    @DisplayName("正常六段表达式：5 分钟一次，未来 3 个点严格递增")
    void 正常表达式() {
        CronSchedule schedule = CronSchedule.parse("0 */5 * * * *", "Asia/Shanghai");
        List<Instant> times = schedule.nextTimes(BASE, 3);

        assertEquals(3, times.size());
        assertEquals(Instant.parse("2026-11-03T02:05:00Z"), times.get(0));
        assertEquals(Instant.parse("2026-11-03T02:10:00Z"), times.get(1));
        assertEquals(Instant.parse("2026-11-03T02:15:00Z"), times.get(2));
    }

    @Test
    @DisplayName("跨天：每天 02:15（Asia/Shanghai）连续两天")
    void 跨天() {
        CronSchedule schedule = CronSchedule.parse("0 15 2 * * *", "Asia/Shanghai");
        // BASE = 北京时间 10:00，下一次是次日 02:15 = 前一日 18:15Z
        List<Instant> times = schedule.nextTimes(BASE, 3);

        assertEquals(Instant.parse("2026-11-03T18:15:00Z"), times.get(0));
        assertEquals(Instant.parse("2026-11-04T18:15:00Z"), times.get(1));
        assertEquals(Instant.parse("2026-11-05T18:15:00Z"), times.get(2));
    }

    @Test
    @DisplayName("时区：同一 cron 在上海与 UTC 得到不同时刻")
    void 时区_Asia_Shanghai() {
        Instant shanghai = CronSchedule.parse("0 0 9 * * *", "Asia/Shanghai")
                .nextAfter(BASE);
        Instant utc = CronSchedule.parse("0 0 9 * * *", "UTC").nextAfter(BASE);

        assertEquals(Instant.parse("2026-11-04T01:00:00Z"), shanghai);
        assertEquals(Instant.parse("2026-11-03T09:00:00Z"), utc);
        assertTrue(shanghai.isAfter(utc));
    }

    @Test
    @DisplayName("非法表达式：5 段与 7 段（含年字段）均拒绝")
    void 非法表达式拒绝() {
        IllegalArgumentException five = assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.parse("*/5 * * * *", "Asia/Shanghai"));
        assertTrue(five.getMessage().contains("cron 表达式非法"));

        IllegalArgumentException seven = assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.parse("0 0 9 * * * 2027", "Asia/Shanghai"));
        assertTrue(seven.getMessage().contains("不支持年字段"));

        assertFalse(CronSchedule.isValid("0 0 9 * *"));
        assertTrue(CronSchedule.isValid("0 0 9 * * *"));
    }

    @Test
    @DisplayName("非法时区拒绝，默认时区为 Asia/Shanghai")
    void 时区拒绝与默认() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CronSchedule.parse("0 0 9 * * *", "Mars/Olympus"));
        assertTrue(ex.getMessage().contains("时区非法"));

        CronSchedule defaultZone = CronSchedule.parse("0 0 9 * * *", null);
        assertEquals("Asia/Shanghai", defaultZone.zone().getId());
    }

    @Test
    @DisplayName("错过的触发点计数：最多数到 cap，用于判断是否需要补一次")
    void 错过触发点计数() {
        CronSchedule hourly = CronSchedule.parse("0 0 * * * *", "UTC");
        Instant threeHoursAgo = Instant.parse("2026-11-02T23:00:00Z");

        assertEquals(3, hourly.countFirePoints(threeHoursAgo, BASE, 5));
        assertEquals(2, hourly.countFirePoints(threeHoursAgo, BASE, 2));
        assertEquals(0, hourly.countFirePoints(BASE, BASE, 5));
    }
}
