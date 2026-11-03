package com.qa.autotest.domain.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 运行报告视图（GET /api/reports 与详情接口共用）。
 *
 * @param projectId       项目
 * @param runId           RUN_ID 字符串（run.run_id）
 * @param state           运行状态（run.run_status）
 * @param exportable      是否可对外交付
 * @param metadataMissing 缺失键名列表（空列表表示不缺失）
 * @param metadata        完整 metadata 十项键值
 * @param passCount       PASS 数
 * @param failCount       FAIL 数
 * @param skipCount       SKIP 数
 * @param startedAt       起始时间（metadata.STARTED_AT，缺失时取 run.started_at）
 * @param finishedAt      结束时间
 */
public record ReportRecord(long projectId,
                           String runId,
                           String state,
                           boolean exportable,
                           List<String> metadataMissing,
                           Map<String, String> metadata,
                           int passCount,
                           int failCount,
                           int skipCount,
                           Instant startedAt,
                           Instant finishedAt) {

    public ReportRecord {
        metadataMissing = metadataMissing == null ? List.of() : List.copyOf(metadataMissing);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
