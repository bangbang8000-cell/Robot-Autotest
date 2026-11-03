package com.qa.autotest.domain.report;

/**
 * 套件树重建用的扁平用例行（RPT-02）。
 *
 * @param caseKey     连接键：suitePath.caseName
 * @param verdict     PASS/FAIL/SKIP/UNKNOWN
 * @param durationMs  耗时（毫秒）
 * @param failureMsg  失败摘要，无失败为 null
 */
public record RunCaseRow(String caseKey, String verdict, long durationMs, String failureMsg) {
}
