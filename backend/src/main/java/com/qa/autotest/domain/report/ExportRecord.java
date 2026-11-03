package com.qa.autotest.domain.report;

import java.time.Instant;

/**
 * 导出记录视图（RPT-05），与 {@code export_record} 表一一对应。
 *
 * @param id           export_record.id
 * @param projectId    项目
 * @param runId        RUN_ID（弱引用，不建外键）
 * @param format       导出格式
 * @param mode         导出模式
 * @param fileName     下载文件名
 * @param filePath     产物绝对路径
 * @param sha256       产物校验和（小写十六进制）
 * @param sizeBytes    产物字节数
 * @param status       created / failed / expired
 * @param requestedBy  操作人
 * @param createdAt    导出时间
 */
public record ExportRecord(long id,
                           long projectId,
                           String runId,
                           ExportFormat format,
                           ExportMode mode,
                           String fileName,
                           String filePath,
                           String sha256,
                           long sizeBytes,
                           String status,
                           Long requestedBy,
                           Instant createdAt) {
}
