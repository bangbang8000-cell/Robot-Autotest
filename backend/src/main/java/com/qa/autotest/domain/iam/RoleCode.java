package com.qa.autotest.domain.iam;

import java.util.Locale;

/** 全局角色（PRD §3.1 权限矩阵的四个内置角色，不可删除）。 */
public enum RoleCode {

    ADMIN("admin", "管理员"),
    PM("pm", "项目经理"),
    ENGINEER("engineer", "测试工程师"),
    VIEWER("viewer", "访客");

    private final String code;
    private final String label;

    RoleCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 解析；非法值抛 {@link IllegalArgumentException}（由上层翻译成 400）。 */
    public static RoleCode from(String code) {
        if (code == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (RoleCode value : values()) {
            if (value.code.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知角色: " + code);
    }
}
