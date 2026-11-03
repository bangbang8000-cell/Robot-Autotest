package com.qa.autotest.application.execution;

import java.time.Instant;

/**
 * 租约只读视图（FR-v0.4-09）。
 */
public record LeaseView(String resourceKey,
                        String runId,
                        long jobId,
                        long projectId,
                        Instant acquiredAt,
                        Instant expiresAt,
                        long remainingSeconds,
                        String state) {
}
