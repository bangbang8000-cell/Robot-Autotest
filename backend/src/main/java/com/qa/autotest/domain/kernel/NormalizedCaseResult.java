package com.qa.autotest.domain.kernel;

/** 归一后的单条用例结论。nodeId 对 pytest 是 nodeid（含参数化后缀），对 RF 是 longname。 */
public record NormalizedCaseResult(String nodeId, String name, CaseStatus status,
                                   double durationSeconds, String message) {
}
