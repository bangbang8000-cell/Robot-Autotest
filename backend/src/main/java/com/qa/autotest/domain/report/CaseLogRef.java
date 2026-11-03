package com.qa.autotest.domain.report;

/**
 * 用例级日志定位（RPT-02）。日志正文不进库，按序号区间从 output.xml 流式读取。
 *
 * @param caseKey   连接键
 * @param logOffset 首条消息序号（无消息为 -1）
 * @param logSpan   区间 {@code 'start:end'}，无消息为 null
 */
public record CaseLogRef(String caseKey, long logOffset, String logSpan) {
}
