package com.qa.autotest.application.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 产物保留策略配置（RPT-06），前缀 {@code report.retention}。
 *
 * <p>缺省值即 DEV-PLAN §6 的口径：90 天 / 项目 50 GB / 最近 20 次保护；
 * 定时清理默认关闭，dryRun 由接口默认 true，避免任何环境被误清。
 */
@Component
@ConfigurationProperties(prefix = "report.retention")
public class RetentionProperties {

    public static final int DEFAULT_MAX_DAYS = 90;
    public static final int DEFAULT_MAX_PROJECT_GB = 50;
    public static final int DEFAULT_KEEP_RECENT_RUNS = 20;
    public static final String DEFAULT_CRON = "0 3 * * *";

    private boolean enabled = false;
    private String cron = DEFAULT_CRON;
    private int maxDays = DEFAULT_MAX_DAYS;
    private int maxProjectGb = DEFAULT_MAX_PROJECT_GB;
    private int keepRecentRuns = DEFAULT_KEEP_RECENT_RUNS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }

    public int getMaxProjectGb() {
        return maxProjectGb;
    }

    public void setMaxProjectGb(int maxProjectGb) {
        this.maxProjectGb = maxProjectGb;
    }

    public int getKeepRecentRuns() {
        return keepRecentRuns;
    }

    public void setKeepRecentRuns(int keepRecentRuns) {
        this.keepRecentRuns = keepRecentRuns;
    }
}
