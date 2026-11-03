package com.qa.autotest.domain.report;

import java.util.Map;

/**
 * 一次 output.xml 解析的完整结果。
 *
 * @param root      根套件树
 * @param metadata  递归合并后的 metadata（父级先写、子套件覆盖/补充）
 * @param passCount 状态为 PASS 的用例数
 * @param failCount 状态为 FAIL 的用例数
 * @param skipCount 状态为 SKIP 的用例数
 */
public record ParsedReport(SuiteNode root,
                           Map<String, String> metadata,
                           int passCount,
                           int failCount,
                           int skipCount) {

    public ParsedReport {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 三类结论之和，用于 total_tests 展示。 */
    public int totalCount() {
        return passCount + failCount + skipCount;
    }

    /** 读取某个 metadata 键，不存在时返回 null。 */
    public String metadataValue(String key) {
        return key == null ? null : metadata.get(key);
    }
}
