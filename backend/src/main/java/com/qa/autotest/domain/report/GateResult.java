package com.qa.autotest.domain.report;

import java.util.List;

/**
 * metadata 门禁结果。
 *
 * @param exportable 十项齐全且无超长时为 true，直接落库 run.exportable
 * @param missing    缺失（或空白）的键名，按 {@link MetadataGate#REQUIRED} 顺序
 * @param tooLong    超长的键名，按 {@link MetadataGate#REQUIRED} 顺序
 */
public record GateResult(boolean exportable, List<String> missing, List<String> tooLong) {

    /**
     * 生成 run.metadata_missing 的逗号分隔值；无缺失时返回 null（列写 NULL，而不是空串）。
     */
    public String missingCsv() {
        return missing == null || missing.isEmpty() ? null : String.join(",", missing);
    }
}
