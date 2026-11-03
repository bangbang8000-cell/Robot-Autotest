package com.qa.autotest.domain.report;

import java.util.List;

/**
 * 测试用例节点（RPT-01 解析结果的最小结构）。
 *
 * <p>RPT-02 追加日志定位：{@code logOffset} 为该用例首条消息在 output.xml 中的序号，
 * {@code logSpan} 为 {@code 'start:end'} 左闭右开区间。日志正文不进库（DEV-PLAN §2.3），
 * 报告页按偏移从文件流式读取。无消息时 offset 为 -1、span 为 null。
 *
 * @param name           用例名
 * @param status         PASS/FAIL/SKIP/UNKNOWN
 * @param durationMs     耗时（毫秒）
 * @param failureMessage 失败消息摘要，无失败时为 null
 * @param keywords       顶层关键字
 * @param logOffset      首条消息序号（无消息为 -1）
 * @param logSpan        日志序号区间 {@code 'start:end'}，无消息为 null
 */
public record CaseNode(String name,
                       String status,
                       long durationMs,
                       String failureMessage,
                       List<KeywordNode> keywords,
                       long logOffset,
                       String logSpan) {

    public CaseNode {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    /** 兼容 RPT-01 的五参写法（无日志定位时使用）。 */
    public CaseNode(String name, String status, long durationMs,
                    String failureMessage, List<KeywordNode> keywords) {
        this(name, status, durationMs, failureMessage, keywords, -1L, null);
    }
}
