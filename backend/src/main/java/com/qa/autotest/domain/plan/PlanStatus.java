package com.qa.autotest.domain.plan;

import java.util.Locale;

/** 执行计划状态。库内小写，暂停的计划不得再被调度器触发。 */
public enum PlanStatus {
    ENABLED,
    PAUSED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlanStatus fromDb(String value) {
        if (value == null || value.isBlank()) {
            return ENABLED;
        }
        return PlanStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean triggersAllowed() {
        return this == ENABLED;
    }
}
