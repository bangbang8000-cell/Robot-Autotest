package com.qa.autotest.domain.report;

/**
 * 套件树重建用的扁平关键字行（RPT-02）。
 *
 * @param id           关键字结果主键（用于父子挂接）
 * @param testResultId 所属 test_result 主键
 * @param caseKey      所属用例连接键
 * @param parentId     父关键字主键，顶层为 null
 * @param name         关键字名
 * @param status       PASS/FAIL/SKIP/UNKNOWN
 * @param depth        嵌套深度
 */
public record KeywordRow(long id,
                         long testResultId,
                         String caseKey,
                         Long parentId,
                         String name,
                         String status,
                         int depth) {
}
