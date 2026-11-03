package com.qa.autotest.application.ci;

/**
 * CI 触发结果（HTTP 202 的 data 体）。
 *
 * @param duplicated  true 表示命中 (plan_id, commit_sha) 幂等键，未新建 run
 * @param writeback   本次触发的回写预期：pending / disabled / not_supported
 */
public record CiTriggerResult(String runId, boolean duplicated, String writeback) {
}
