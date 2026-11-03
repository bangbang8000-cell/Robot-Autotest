package com.qa.autotest.application.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 打印页数据（RPT-05）：与 Word 同源，供浏览器渲染成 PDF。
 *
 * @param runId           运行号
 * @param mode            external / internal
 * @param internal        是否内部版（true 时页面必须显示红色内部标识）
 * @param exportable      是否可对外
 * @param metadataMissing 缺失键
 * @param metadata        十项 metadata
 * @param passCount       PASS 数
 * @param failCount       FAIL 数
 * @param skipCount       SKIP 数
 * @param startedAt       开始时间
 * @param finishedAt      结束时间
 * @param tree            套件树
 */
public record PrintReportView(String runId,
                              String mode,
                              boolean internal,
                              boolean exportable,
                              List<String> metadataMissing,
                              Map<String, String> metadata,
                              int passCount,
                              int failCount,
                              int skipCount,
                              Instant startedAt,
                              Instant finishedAt,
                              ReportTreeService.TreeSuite tree) {
}
