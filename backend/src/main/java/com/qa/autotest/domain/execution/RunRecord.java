package com.qa.autotest.domain.execution;

import java.time.Instant;

/**
 * 运行记录视图。字段与 run 表一一对应，数值未产出时为 0。
 */
public record RunRecord(long id,
                        String runId,
                        long projectId,
                        Long testSetId,
                        RunState state,
                        int priority,
                        int concurrency,
                        int attemptNo,
                        int totalTests,
                        int passedTests,
                        int failedTests,
                        String failureClass,
                        String failureReason,
                        String failureMessage,
                        String artifactsDir,
                        Instant enqueuedAt,
                        Instant startedAt,
                        Instant finishedAt) {
}
