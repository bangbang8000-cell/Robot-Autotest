package com.qa.autotest.domain.report;

import java.util.Locale;
import java.util.Optional;

/**
 * 导出模式（RPT-05）。
 *
 * <p>{@link #EXTERNAL} 是对外交付版：metadata 有缺项时拒绝导出；
 * {@link #INTERNAL} 是内部版：允许缺项，但产物必须带醒目的红色内部标识。
 */
public enum ExportMode {

    /** 外部版。 */
    EXTERNAL("external", false),
    /** 内部版。 */
    INTERNAL("internal", true);

    private final String wireName;
    private final boolean internalMark;

    ExportMode(String wireName, boolean internalMark) {
        this.wireName = wireName;
        this.internalMark = internalMark;
    }

    public String wireName() {
        return wireName;
    }

    /** 产物是否需要红色内部标识。 */
    public boolean needsInternalMark() {
        return internalMark;
    }

    public static Optional<ExportMode> fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ExportMode value : values()) {
            if (value.wireName.equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
