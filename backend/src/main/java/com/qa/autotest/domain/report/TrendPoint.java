package com.qa.autotest.domain.report;

import java.time.Instant;

/**
 * 一次运行里某条用例的趋势点（RPT-04）。
 *
 * @param runId      RUN_ID
 * @param startedAt  运行开始时间
 * @param verdict    该用例结论
 * @param durationMs 该用例耗时（毫秒）
 * @param envKey     环境键（DUT + LINK，见 {@link TrendEnv#envKey(String, String)}）
 */
public record TrendPoint(String runId,
                         Instant startedAt,
                         String verdict,
                         long durationMs,
                         String envKey) {
}
