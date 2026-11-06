package com.qa.autotest.domain.importing;

import java.util.List;
import java.util.Map;

/** 预检报告（IMP-05）：可执行项、敏感扫描命中、与上一批的 diff 三件事一次给全。 */
public record PreflightReport(String sourceType, String checksum, int fileCount,
                              List<CaseSource> cases, List<String> commands,
                              List<String> sensitiveFindings, Map<String, List<String>> diff,
                              boolean executable) {
}
