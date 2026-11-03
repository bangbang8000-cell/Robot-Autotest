package com.qa.autotest.application.execution;

import java.time.Instant;

/**
 * 运行产物登记视图。
 */
public record RunArtifact(long id,
                          long projectId,
                          long runDbId,
                          String kind,
                          String path,
                          String sha256,
                          long sizeBytes,
                          int shardNo,
                          String availability,
                          String note,
                          Instant createdAt) {
}
