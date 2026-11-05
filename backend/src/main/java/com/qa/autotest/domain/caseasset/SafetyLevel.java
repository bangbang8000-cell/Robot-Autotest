package com.qa.autotest.domain.caseasset;

import java.util.Locale;

/**
 * 执行安全级（ADR-0013）：L0 只读 / L1 可逆写 / L2 高危不可逆 / LX 未判定。
 *
 * <p>LX 在人工补判前一律按 L2 对待，因此 {@link #blocksExecution()} 同时拦 L2 与 LX——
 * 宁可少放，不可错放。导入台账没有安全级列时落 LX，须由人工补判后再执行。
 */
public enum SafetyLevel {

    L0,
    L1,
    L2,
    LX;

    /** 执行入口是否拒绝该档（L2 高危、LX 未判定）。 */
    public boolean blocksExecution() {
        return this == L2 || this == LX;
    }

    /** 解析安全级：空值即 LX（未判定），非法值报错不猜测。 */
    public static SafetyLevel from(String value) {
        if (value == null || value.isBlank()) {
            return LX;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (SafetyLevel level : values()) {
            if (level.name().equals(normalized)) {
                return level;
            }
        }
        throw new IllegalArgumentException("非法安全级: " + value + "（只允许 L0/L1/L2/LX）");
    }
}
