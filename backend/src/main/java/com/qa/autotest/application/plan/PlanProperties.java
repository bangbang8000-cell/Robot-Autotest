package com.qa.autotest.application.plan;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 计划编排参数（PLN-01）。默认值按 DEV-PLAN §2.2 与任务书。
 *
 * <p><b>调度默认关闭</b>：{@code planning.scheduler-enabled=false} 时不注册 PlanScheduler，
 * 避免开发/联调环境被动触发真实 pabot。生产与验收时显式配 true。
 */
@Component
@ConfigurationProperties(prefix = "planning")
public class PlanProperties {

    private int tickSeconds = 30;
    private int maxConcurrentPerProject = 2;
    private int lockBatch = 50;
    private boolean schedulerEnabled = false;

    public int getTickSeconds() {
        return tickSeconds;
    }

    public void setTickSeconds(int tickSeconds) {
        this.tickSeconds = tickSeconds;
    }

    public int getMaxConcurrentPerProject() {
        return maxConcurrentPerProject;
    }

    public void setMaxConcurrentPerProject(int maxConcurrentPerProject) {
        this.maxConcurrentPerProject = maxConcurrentPerProject;
    }

    public int getLockBatch() {
        return lockBatch;
    }

    public void setLockBatch(int lockBatch) {
        this.lockBatch = lockBatch;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }
}
