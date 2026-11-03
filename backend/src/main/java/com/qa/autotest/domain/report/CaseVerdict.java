package com.qa.autotest.domain.report;

/**
 * 一次运行里某条用例的结论（RPT-03 双列并置的数据单元）。
 *
 * @param caseKey    连接键：suitePath.caseName
 * @param verdict    PASS/FAIL/SKIP/UNKNOWN
 * @param durationMs 耗时（毫秒）
 */
public record CaseVerdict(String caseKey, String verdict, long durationMs) {
}
