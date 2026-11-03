package com.qa.autotest.domain.ci;

/**
 * CI 回写状态（PLN-03）。
 *
 * <p>字符串口径与 {@code ci_trigger.writeback_status} 一致：
 * <ul>
 *   <li>{@code pending} 已触发、尚未到终态；</li>
 *   <li>{@code success} / {@code failed} GitHub commit status 已提交 / 提交失败；</li>
 *   <li>{@code skipped} 未启用回写或缺令牌，未发请求；</li>
 *   <li>{@code not_supported} 目标仓库明确不支持（群晖 git server 只有 SSH，无 HTTP API）。</li>
 * </ul>
 */
public enum CiWritebackStatus {

    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    SKIPPED("skipped"),
    NOT_SUPPORTED("not_supported");

    private final String dbValue;

    CiWritebackStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static CiWritebackStatus fromDb(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (CiWritebackStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return PENDING;
    }
}
