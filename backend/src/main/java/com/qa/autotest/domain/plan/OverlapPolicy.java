package com.qa.autotest.domain.plan;

import java.util.Locale;

/**
 * 重叠策略：计划上一轮仍在排队/执行时，本次触发点如何处理。
 *
 * <p>v0.6 只实现 {@link #SKIP}：跳过本次并记录 skipped_overlap，等下一个触发点。
 */
public enum OverlapPolicy {
    SKIP;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OverlapPolicy fromDb(String value) {
        if (value == null || value.isBlank()) {
            return SKIP;
        }
        return OverlapPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
