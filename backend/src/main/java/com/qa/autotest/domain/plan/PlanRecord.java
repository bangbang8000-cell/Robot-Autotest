package com.qa.autotest.domain.plan;

import java.time.Instant;
import java.util.List;

/**
 * 执行计划视图。字段与 execution_plan 表一一对应。
 *
 * @param suitePaths 空列表表示未配置（调度时会跳过并告警，不会产出无套件的 run）
 * @param lastFireAt 最近一次处理过的触发点（触发 / 跳过 / 延迟都会推进）
 * @param nextFireAt 已计算的下一次触发时间；暂停时为 null
 */
public record PlanRecord(long id,
                         long projectId,
                         String name,
                         Long testSetId,
                         Long environmentId,
                         List<String> suitePaths,
                         String includeTag,
                         int concurrency,
                         int timeoutSeconds,
                         String cron,
                         String timezone,
                         PlanStatus status,
                         OverlapPolicy overlapPolicy,
                         Instant lastFireAt,
                         Instant nextFireAt,
                         Long createdBy,
                         Instant createdAt) {

    public PlanRecord {
        suitePaths = suitePaths == null ? List.of() : List.copyOf(suitePaths);
        status = status == null ? PlanStatus.ENABLED : status;
        overlapPolicy = overlapPolicy == null ? OverlapPolicy.SKIP : overlapPolicy;
        timezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
    }
}
