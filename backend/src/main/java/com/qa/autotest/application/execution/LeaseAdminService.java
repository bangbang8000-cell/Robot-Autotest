package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ResourceLeaseRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员强制释放租约（EXEC-07 / FR-v0.4-10）。
 *
 * <p>只读视图（FR-v0.4-09）已在 {@code GET /api/execution/leases}；本服务补上「卡住的租约怎么办」：
 * <ul>
 *   <li>只有管理员可调用，普通工程角色 403；</li>
 *   <li>二次确认：{@code confirm} 必须等于占用该资源的 runId，防止手滑释放别的任务；</li>
 *   <li>原任务若在 {@code RUNNING}，标记 {@code failed(lease_revoked)} 并留审计；</li>
 *   <li>释放动作复用 {@link LeaseService#forceReleaseRun}，历史表与取消/超时路径一致，另留强制释放审计。</li>
 * </ul>
 *
 * <p><b>顺序与取消一致</b>：先杀该 run 的进程树，再标记失败，最后摘租约。若只摘租约不杀进程，
 * pabot 会继续跑并占着设备，下一个任务抢到租约后仍会与僵尸进程互踩——所以释放前必须收尾。
 */
@Service
public class LeaseAdminService {

    private static final Duration KILL_GRACE = Duration.ofSeconds(5);

    private final ResourceLeaseRepository leases;
    private final LeaseService leaseService;
    private final RunService runs;
    private final ProcessRegistry registry;
    private final ProcessTreeKiller killer;
    private final AccessControlService access;
    private final Clock clock;

    public LeaseAdminService(ResourceLeaseRepository leases, LeaseService leaseService,
                             RunService runs, ProcessRegistry registry, ProcessTreeKiller killer,
                             AccessControlService access, Clock clock) {
        this.leases = leases;
        this.leaseService = leaseService;
        this.runs = runs;
        this.registry = registry;
        this.killer = killer;
        this.access = access;
        this.clock = clock;
    }

    /** 强制释放结果：便于前端提示「释放了谁的几条租约」。 */
    public record ForceReleaseResult(String runId, String runState, int releasedLeases) {
    }

    @Transactional
    public ForceReleaseResult forceRelease(long projectId, String resourceKey, String confirm) {
        access.bindProject(projectId);
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "只有管理员可以强制释放租约；当前用户不具备该权限");
        }
        LeaseView target = leases.listByProject(projectId).stream()
                .filter(lease -> lease.resourceKey().equals(resourceKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "该资源没有生效中的租约：" + resourceKey + "。可能已被释放或已过期，刷新列表再试"));
        if (confirm == null || !confirm.equals(target.runId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "二次确认不足：confirm 必须等于占用该资源的 runId（" + target.runId() + "）");
        }

        killProcesses(target.runId());
        String state = markFailedIfRunning(projectId, target.runId(), resourceKey);
        int released = leaseService.forceReleaseRun(target.runId(), "lease_revoked");
        return new ForceReleaseResult(target.runId(), state, released);
    }

    /** 与 {@link CancelService} 同构：先礼后兵杀进程树，再注销登记，避免僵尸进程继续占设备。 */
    private void killProcesses(String runId) {
        List<Process> active = registry.processes(runId);
        active.forEach(process -> killer.kill(process, KILL_GRACE));
        registry.unregister(runId);
    }

    private String markFailedIfRunning(long projectId, String runId, String resourceKey) {
        RunRecord run;
        try {
            run = runs.get(projectId, runId);
        } catch (BusinessException ex) {
            return "unknown";
        }
        if (run.state() != RunState.RUNNING) {
            return run.state().name();
        }
        RunRecord failed = runs.transition(runId, RunState.FAILED,
                new RunService.TransitionContext(null, clock.instant(),
                        "lease_revoked", "lease_revoked",
                        "管理员强制释放租约：" + resourceKey, false, false));
        return failed.state().name();
    }
}
