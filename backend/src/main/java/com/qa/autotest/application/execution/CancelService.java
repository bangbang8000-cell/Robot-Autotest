package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取消（EXE-05）：只有非终态可取消；先杀进程树再释放租约，最后落 CANCELED。
 *
 * <p>顺序不能反：先置状态再杀进程会出现「状态已取消但 pabot 仍在跑」的窗口，
 * 且那时释放的租约可能被下一个任务抢走，设备被上一个僵尸进程继续占用。
 */
@Service
public class CancelService {

    private static final Duration KILL_GRACE = Duration.ofSeconds(5);

    private final RunService runs;
    private final AccessControlService access;
    private final ProcessRegistry registry;
    private final ProcessTreeKiller killer;
    private final LeaseService leases;
    private final AuditService audit;
    private final Clock clock;

    public CancelService(RunService runs, AccessControlService access, ProcessRegistry registry,
                         ProcessTreeKiller killer, LeaseService leases, AuditService audit,
                         Clock executionClock) {
        this.runs = runs;
        this.access = access;
        this.registry = registry;
        this.killer = killer;
        this.leases = leases;
        this.audit = audit;
        this.clock = executionClock;
    }

    @Transactional
    public RunRecord cancel(long projectId, String runId, String operatorReason) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.EXECUTE);

        RunRecord run = runs.get(projectId, runId);
        if (run.state().terminal()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "运行已处于终态 " + run.state().dbValue() + "，不可取消");
        }

        killProcesses(runId);
        leases.releaseRun(runId, "canceled");

        RunRecord canceled = runs.transition(runId, RunState.CANCELED,
                new RunService.TransitionContext(null, clock.instant(),
                        FailureClass.CANCELED.name(), "canceled",
                        operatorReason == null ? "操作人取消" : operatorReason, false, true));
        audit.record("RUN_CANCEL", "run", runId,
                operatorReason == null ? "" : operatorReason);
        return canceled;
    }

    private void killProcesses(String runId) {
        List<Process> active = registry.processes(runId);
        active.forEach(process -> killer.kill(process, KILL_GRACE));
        registry.unregister(runId);
    }
}
