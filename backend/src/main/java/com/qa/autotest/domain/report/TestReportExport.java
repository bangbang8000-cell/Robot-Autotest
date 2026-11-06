package com.qa.autotest.domain.report;

import java.time.Instant;

/** 导出记录（RPTX-05）：文件本体落 artifacts，表里只留元数据与水印。 */
public record TestReportExport(long id, long reportId, String format, String fileName,
                               long sizeBytes, String watermark, Instant createdAt) {
}
