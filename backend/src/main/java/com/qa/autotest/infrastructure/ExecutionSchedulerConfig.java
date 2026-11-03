package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.LeaseService;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.application.execution.RunTimeoutMonitor;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.Lease;
import com.qa.autotest.domain.execution.RunState;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * 执行域定时任务：租约心跳、超时回收。
 *
 * <p>测试环境不启动：单测用固定时钟，不能让真实调度在后台改状态。
 */
@Configuration
@EnableScheduling
@Profile("!test")
@ConditionalOnProperty(name = "execution.scheduling.enabled", havingValue = "true",
        matchIfMissing = true)
public class ExecutionSchedulerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionSchedulerConfig.class);

    private final LeaseService leases;
    private final RunService runs;
    private final RunTimeoutMonitor timeouts;
    private final Clock clock;

    public ExecutionSchedulerConfig(LeaseService leases, RunService runs,
                                    RunTimeoutMonitor timeouts, Clock executionClock) {
        this.leases = leases;
        this.runs = runs;
        this.timeouts = timeouts;
        this.clock = executionClock;
    }

    /** 心跳 40s：只续自己的 job，过期即交给回收任务收尾。 */
    @Scheduled(fixedDelayString = "${execution.lease-heartbeat-seconds:40}", timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        int renewed = leases.heartbeatAll();
        if (renewed > 0) {
            LOG.debug("租约心跳续期 {} 行", renewed);
        }
    }

    /** 回收 60s：删除过期租约并把对应 run 标 FAILED(lease_expired)。 */
    @Scheduled(fixedDelayString = "${execution.lease-reclaim-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void reclaimExpiredLeases() {
        List<Lease> reclaimed = leases.reclaimExpired(clock.instant());
        for (Lease lease : reclaimed) {
            TenantContext.runAs(null, "system", lease.projectId(), () -> {
                try {
                    runs.transition(lease.runId(), RunState.FAILED,
                            new RunService.TransitionContext(null, null,
                                    FailureClass.INFRA.name(), "lease_expired",
                                    "租约超时未续期，已被回收", false, false));
                } catch (BusinessException alreadyTerminal) {
                    LOG.debug("run {} 已非活跃，跳过租约回收收尾", lease.runId());
                }
            });
        }
        if (!reclaimed.isEmpty()) {
            LOG.info("回收过期租约 {} 条", reclaimed.size());
        }
    }

    /** 超时检查 60s：running 且超过 runTimeout 的运行收尾为 FAILED(timeout)。 */
    @Scheduled(fixedDelayString = "${execution.lease-reclaim-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void checkRunTimeouts() {
        List<String> expired = timeouts.check(clock.instant());
        if (!expired.isEmpty()) {
            LOG.warn("运行超时收尾 {} 条: {}", expired.size(), expired);
        }
    }
}
