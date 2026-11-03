package com.qa.autotest.application.plan;

import java.util.List;

/**
 * 创建/修改执行计划的入参（PLN-01）。
 *
 * <p>依赖列表在保存时做环检测；成环直接 400，不写库（PLN-02）。
 */
public record PlanCommand(long projectId,
                          String name,
                          Long testSetId,
                          Long environmentId,
                          List<String> suitePaths,
                          String includeTag,
                          int concurrency,
                          int timeoutSeconds,
                          String cron,
                          String timezone,
                          String overlapPolicy,
                          List<Long> dependencyIds) {

    public PlanCommand {
        suitePaths = suitePaths == null ? List.of() : List.copyOf(suitePaths);
        dependencyIds = dependencyIds == null ? List.of() : List.copyOf(dependencyIds);
    }
}
