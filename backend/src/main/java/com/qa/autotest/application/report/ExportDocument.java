package com.qa.autotest.application.report;

import com.qa.autotest.domain.report.ExportMode;
import com.qa.autotest.domain.report.ReportRecord;
import java.time.Instant;

/**
 * 导出文档模型（RPT-05）：Word 与打印页共用的同源信息。
 *
 * @param report     报告视图（含十项 metadata 与缺失键）
 * @param tree       套件树
 * @param mode       外部版 / 内部版
 * @param exportedAt 导出时间
 * @param operator   操作人
 */
public record ExportDocument(ReportRecord report,
                             ReportTreeService.TreeSuite tree,
                             ExportMode mode,
                             Instant exportedAt,
                             String operator) {
}
