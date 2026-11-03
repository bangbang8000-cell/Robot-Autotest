package com.qa.autotest.domain.report;

/**
 * 通过率聚合的原始计数（RPT-04），按天 / 周的时间桶汇总。
 *
 * <p>通过率由应用层计算，领域记录只承载数据库聚合出来的四个数。
 *
 * @param bucket 时间桶：按天为 {@code YYYY-MM-DD}，按周为 {@code YYYY-WW}
 * @param pass   PASS 数
 * @param fail   FAIL 数
 * @param skip   SKIP 数
 */
public record PassRateCount(String bucket, int pass, int fail, int skip) {
}
