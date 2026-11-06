package com.qa.autotest.domain.kernel;

import java.util.Locale;

/** 双内核统一状态（KRN-01）。pytest 六态与 RF 三态都归一到这里，绝不各算一列。 */
public enum CaseStatus {
    PASS,
    FAIL,
    SKIP,
    XFAIL,
    XPASS,
    ERROR;

    public static CaseStatus fromPytest(String outcome) {
        if (outcome == null) {
            return ERROR;
        }
        return switch (outcome.trim().toLowerCase(Locale.ROOT)) {
            case "passed", "pass" -> PASS;
            case "failed", "fail", "failure" -> FAIL;
            case "skipped", "skip" -> SKIP;
            case "xfailed", "xfail" -> XFAIL;
            case "xpassed", "xpass" -> XPASS;
            default -> ERROR;
        };
    }

    /** 是否算「未通过」：对外通过率只把 PASS / XFAIL / SKIP 当非失败。 */
    public boolean isFailure() {
        return this == FAIL || this == ERROR || this == XPASS;
    }
}
