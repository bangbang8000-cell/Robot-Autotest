package com.qa.autotest.domain.report;

/**
 * 日志序号区间 {@code start:end}（左闭右开），与 keyword_result / test_result 的 log_span 同义。
 *
 * <p>解析失败或空区间返回 {@link #EMPTY}，调用方按「无日志」处理，不抛异常打断请求。
 */
public record LogSpan(long start, long endExclusive) {

    public static final LogSpan EMPTY = new LogSpan(-1L, -1L);

    public boolean empty() {
        return start < 0 || endExclusive <= start;
    }

    public long length() {
        return empty() ? 0L : endExclusive - start;
    }

    /** 解析 {@code 'start:end'}；非法输入返回 {@link #EMPTY}。 */
    public static LogSpan parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        String value = raw.trim();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon >= value.length() - 1) {
            return EMPTY;
        }
        try {
            long start = Long.parseLong(value.substring(0, colon).trim());
            long end = Long.parseLong(value.substring(colon + 1).trim());
            return end > start ? new LogSpan(start, end) : EMPTY;
        } catch (NumberFormatException ex) {
            return EMPTY;
        }
    }
}
