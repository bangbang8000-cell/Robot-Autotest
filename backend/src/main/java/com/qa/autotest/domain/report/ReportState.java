package com.qa.autotest.domain.report;

import java.util.Locale;

/**
 * 测试报告状态机（RPTX-01）。
 *
 * <p>冻结是本域最关键的约束：一旦 FROZEN，快照 hash / 内容不可再变；
 * 只允许 REVOKED（作废）或 ARCHIVED（归档），不允许回到 DRAFT 重新编辑。
 */
public enum ReportState {
    DRAFT,
    FROZEN,
    REVOKED,
    ARCHIVED;

    public boolean canTransitionTo(ReportState target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == FROZEN;
            case FROZEN -> target == REVOKED || target == ARCHIVED;
            case REVOKED -> target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    public static ReportState parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("报告状态为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
