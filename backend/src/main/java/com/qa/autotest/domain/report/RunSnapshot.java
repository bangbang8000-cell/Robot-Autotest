package com.qa.autotest.domain.report;

import java.time.Instant;

/**
 * 一次运行在报告域的只读快照（RPT-02 / RPT-03 共用）。
 *
 * @param projectId 项目
 * @param runId     RUN_ID 字符串
 * @param state     run.run_status 小写值（created/running/finished/...）
 * @param dut       metadata.DUT
 * @param link      metadata.LINK
 * @param startedAt 开始时间
 */
public record RunSnapshot(long projectId,
                          String runId,
                          String state,
                          String dut,
                          String link,
                          Instant startedAt) {
}
