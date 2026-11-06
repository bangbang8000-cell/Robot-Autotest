package com.qa.autotest.domain.report;

import java.time.Instant;

/** 测试报告实体（RPTX-01）。snapshot_* 三列即「冻结后自包含」的全部依据。 */
public record TestReport(long id,
                         long projectId,
                         String runId,
                         String title,
                         ReportState state,
                         int version,
                         String snapshotHash,
                         String snapshotJson,
                         String snapshotHtml,
                         String sourceSummary,
                         Instant createdAt,
                         Instant frozenAt) {
}
