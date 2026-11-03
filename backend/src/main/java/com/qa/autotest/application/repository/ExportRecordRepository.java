package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.ExportFormat;
import com.qa.autotest.domain.report.ExportMode;
import com.qa.autotest.domain.report.ExportRecord;

import java.util.List;
import java.util.Optional;

/**
 * 导出记录仓储（RPT-05）。
 *
 * <p>所有查询都必须带 project_id：export_record 已登记在 TenantGuard 业务表名单内，
 * 跨项目只能查不到，不能靠接口层兜底。
 */
public interface ExportRecordRepository {

    /** 插入一条导出记录，返回自增主键。 */
    long insert(long projectId, String runId, ExportFormat format, ExportMode mode,
                String fileName, String filePath, String sha256, long sizeBytes,
                Long requestedBy, String status);

    /** 某次运行的导出历史（按导出时间倒序）。 */
    List<ExportRecord> list(long projectId, String runId);

    /** 按主键 + 项目查单条记录。 */
    Optional<ExportRecord> find(long projectId, long exportId);
}
