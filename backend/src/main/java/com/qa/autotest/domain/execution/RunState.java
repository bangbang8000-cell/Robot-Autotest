package com.qa.autotest.domain.execution;

import java.util.Locale;

/**
 * 运行状态。状态唯一落点为 run.run_status，库内存小写值。
 */
public enum RunState {
    QUEUED,
    PREPARING,
    RUNNING,
    FINISHED,
    FAILED,
    CANCELED;

    public boolean terminal() {
        return this == FINISHED || this == FAILED || this == CANCELED;
    }

    public boolean active() {
        return this == PREPARING || this == RUNNING;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RunState fromDb(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("运行状态不能为空");
        }
        return RunState.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
