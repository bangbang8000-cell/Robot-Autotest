package com.qa.autotest.domain.execution;

import java.time.Instant;

/**
 * 资源租约。resourceKey 在库内有唯一约束，INSERT 冲突即「被占用」。
 */
public record Lease(String resourceKey,
                    long jobId,
                    String runId,
                    long projectId,
                    Instant acquiredAt,
                    Instant expiresAt) {
}
