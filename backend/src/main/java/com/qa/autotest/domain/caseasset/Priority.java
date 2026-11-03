package com.qa.autotest.domain.caseasset;

import java.util.Locale;

/** 用例优先级（受控枚举，非法值报错不猜测，FR-v0.2-17）。 */
public enum Priority {
    P0, P1, P2, P3;

    public static Priority from(String value) {
        if (value == null || value.isBlank()) {
            return P2;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (Priority p : values()) {
            if (p.name().equals(normalized)) {
                return p;
            }
        }
        throw new IllegalArgumentException("非法优先级: " + value + "（只允许 P0/P1/P2/P3）");
    }
}
