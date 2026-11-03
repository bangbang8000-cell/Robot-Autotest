package com.qa.autotest.application.execution;

import java.nio.file.Path;

/**
 * 路径展开：把配置里的 ~ 展开为当前用户主目录。
 */
public final class ExecutionPaths {

    private ExecutionPaths() {
    }

    public static Path resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("路径为空");
        }
        String value = raw.trim();
        if (value.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home"), value.substring(2));
        }
        return Path.of(value);
    }
}
