package com.qa.autotest.domain.ci;

import java.time.Instant;

/**
 * CI 触发记录（对应 {@code ci_trigger} 表）。
 *
 * <p>{@code (planId, commitSha)} 是幂等键：同一计划同一提交无论触发多少次只会有一行。
 *
 * @param runId 平台 RUN_ID；建 run 后回填，未回填时为 null
 */
public record CiTriggerRecord(long id,
                              long projectId,
                              long planId,
                              String commitSha,
                              String ref,
                              String build,
                              String repo,
                              String runId,
                              CiWritebackStatus writebackStatus,
                              String writebackError,
                              Instant createdAt) {
}
