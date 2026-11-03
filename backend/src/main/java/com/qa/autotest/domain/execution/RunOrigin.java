package com.qa.autotest.domain.execution;

import java.time.Instant;

/**
 * 运行来源：手工触发还是计划调度。落库到 run.trigger_type / run.plan_id /
 * run.triggered_by_plan_at，并在报告入库时合并进 metadata（PLAN_ID / TRIGGER_TYPE）。
 *
 * @param triggerType       manual / schedule / ci（库内小写）
 * @param planId            关联的执行计划；手工运行可为空
 * @param triggeredByPlanAt 计划触发点，用于反查本次 run 对应哪一次 cron 时间
 */
public record RunOrigin(String triggerType, Long planId, Instant triggeredByPlanAt) {

    public static final String MANUAL = "manual";
    public static final String SCHEDULE = "schedule";
    public static final String CI = "ci";

    public RunOrigin {
        if (triggerType == null || triggerType.isBlank()) {
            triggerType = MANUAL;
        }
        triggerType = triggerType.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static RunOrigin manual() {
        return new RunOrigin(MANUAL, null, null);
    }

    public static RunOrigin scheduled(long planId, Instant triggerPoint) {
        return new RunOrigin(SCHEDULE, planId, triggerPoint);
    }

    public static RunOrigin manualOnPlan(long planId) {
        return new RunOrigin(MANUAL, planId, null);
    }

    /** CI 触发（PLN-03）：plan_id 保留用于反查，没有 cron 触发点。 */
    public static RunOrigin ci(long planId) {
        return new RunOrigin(CI, planId, null);
    }
}
