package com.qa.autotest.domain.plan;

import java.time.Instant;

/** 计划最近一次运行结果，用于列表「最近结果」列。 */
public record PlanLastRun(String runId, String state, Instant finishedAt) {
}
