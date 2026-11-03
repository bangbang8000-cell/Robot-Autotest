package com.qa.autotest.domain.report;

import java.util.List;

/**
 * 关键词节点（RPT-02 套件树的最小结构）。
 *
 * <p>RPT-02 追加日志定位：{@code logOffset} 为该关键字子树首条消息在 output.xml 中的序号，
 * {@code logSpan} 为 {@code 'start:end'} 左闭右开区间。日志正文不进库（DEV-PLAN §2.3）。
 *
 * @param name      关键字名
 * @param status    PASS/FAIL/SKIP/UNKNOWN
 * @param durationMs 耗时（毫秒）
 * @param messages  该关键字下的消息文本（已去首尾空白；仅内存态，不入库）
 * @param children  嵌套关键字
 * @param logOffset 首条消息序号（无消息为 -1）
 * @param logSpan   日志序号区间 {@code 'start:end'}，无消息为 null
 */
public record KeywordNode(String name,
                          String status,
                          long durationMs,
                          List<String> messages,
                          List<KeywordNode> children,
                          long logOffset,
                          String logSpan) {

    public KeywordNode {
        messages = messages == null ? List.of() : List.copyOf(messages);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** 兼容 RPT-01 的五参写法（无日志定位时使用）。 */
    public KeywordNode(String name, String status, long durationMs,
                       List<String> messages, List<KeywordNode> children) {
        this(name, status, durationMs, messages, children, -1L, null);
    }
}
