package com.qa.autotest.domain.report;

import java.util.List;
import java.util.Map;

/**
 * 套件节点。metadata 保留在节点上，便于 RPT-02 按套件展示来源；
 * 门禁使用的是 {@link ParsedReport#metadata()} 里递归合并后的结果。
 */
public record SuiteNode(String name,
                        String status,
                        Map<String, String> metadata,
                        List<SuiteNode> suites,
                        List<CaseNode> cases) {

    public SuiteNode {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        suites = suites == null ? List.of() : List.copyOf(suites);
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
