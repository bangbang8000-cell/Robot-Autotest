package com.qa.autotest.domain.caseasset;

import java.time.Instant;

/** 脚本版本快照（ADR-0005：版本是快照表，不是 Git）。 */
public record RobotFileVersion(
        Long id,
        String path,
        int versionNo,
        String content,
        String sha256,
        String comment,
        Instant createdAt) {
}
