package com.qa.autotest.domain.caseasset;

import java.util.Locale;

/** 评审状态（FR-v0.2-24）：draft → pending → approved / rejected。 */
public enum ReviewStatus {

    DRAFT("draft"),
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String code;

    ReviewStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReviewStatus from(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ReviewStatus s : values()) {
            if (s.code.equals(normalized)) {
                return s;
            }
        }
        throw new IllegalArgumentException("非法评审状态: " + value);
    }
}
