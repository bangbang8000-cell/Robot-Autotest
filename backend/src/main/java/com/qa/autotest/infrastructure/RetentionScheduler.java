package com.qa.autotest.infrastructure;

import com.qa.autotest.application.report.RetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日产物清理定时任务（RPT-06）。
 *
 * <p>默认关闭（{@code report.retention.enabled=false}），只在显式开启时装配；
 * 测试环境（profile=test）与单元测试永远不会触发真实文件删除。
 */
@Component
@EnableScheduling
@Profile("!test")
@ConditionalOnProperty(prefix = "report.retention", name = "enabled", havingValue = "true")
public class RetentionScheduler {

    private final RetentionService retentionService;

    public RetentionScheduler(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    /** 默认每天 03:00 执行，cron 由 {@code report.retention.cron} 覆盖。 */
    @Scheduled(cron = "${report.retention.cron:0 3 * * *}")
    public void purgeDaily() {
        retentionService.executeAllProjects();
    }
}
