package com.qa.autotest.domain.caseasset;

/**
 * 脚本文件（ADR-0005）：{@code content} 是唯一真源，不存 AST；版本快照另表。
 */
public record RobotFile(
        Long id,
        Long projectId,
        String path,
        String content,
        String sha256,
        int currentVersion) {
}
