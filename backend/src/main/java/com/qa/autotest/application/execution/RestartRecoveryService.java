package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 重启恢复（EXE-05）：新进程里不可能还有上一轮的 pabot，但仍标 preparing/running 的
 * run 一律收尾为 FAILED(interrupted)，并释放它们占着的租约，否则设备永久被占。
 */
@Service
public class RestartRecoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(RestartRecoveryService.class);

    private final RunRepository repo;
    private final RunService runs;
    private final ProcessRegistry registry;
    private final ProcessTreeKiller killer;
    private final LeaseService leases;
    private final AuditService audit;
    private final Clock clock;

    public RestartRecoveryService(RunRepository repo, RunService runs, ProcessRegistry registry,
                                  ProcessTreeKiller killer, LeaseService leases, AuditService audit,
                                  Clock executionClock) {
        this.repo = repo;
        this.runs = runs;
        this.registry = registry;
        this.killer = killer;
        this.leases = leases;
        this.audit = audit;
        this.clock = executionClock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public RecoveryReport onReady() {
        RecoveryReport report = recover();
        if (report.scanned() > 0) {
            LOG.warn("重启恢复：扫描 {} 条活跃运行，收尾 {} 条，释放租约 {} 个",
                    report.scanned(), report.recovered(), report.leasesReleased());
        }
        return report;
    }

    public RecoveryReport recover() {
        List<RunRecord> active = repo.findActive();
        AtomicInteger recovered = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        for (RunRecord run : active) {
            TenantContext.runAs(null, "system", run.projectId(), () -> {
                registry.processes(run.runId())
                        .forEach(process -> killer.kill(process, java.time.Duration.ofSeconds(5)));
                registry.unregister(run.runId());
                released.addAndGet(leases.releaseRun(run.runId(), "interrupted"));
                try {
                    runs.transition(run.runId(), RunState.FAILED,
                            new RunService.TransitionContext(null, clock.instant(),
                                    FailureClass.INFRA.name(), "interrupted",
                                    "服务重启，运行被中断", false, true));
                    recovered.incrementAndGet();
                    audit.record("RUN_RECOVER", "run", run.runId(), "interrupted");
                } catch (BusinessException alreadyTerminal) {
                    LOG.debug("run {} 已非活跃，跳过恢复", run.runId());
                }
            });
        }
        return new RecoveryReport(active.size(), recovered.get(), released.get());
    }

    public record RecoveryReport(int scanned, int recovered, int leasesReleased) {
    }
}
