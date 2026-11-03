package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 运行超时监控（EXE-05）：{@code startedAt + runTimeout} 已到 → 杀进程、释放租约、置 FAILED(timeout)。
 *
 * <p>超时与取消的区别只在终态与失败分类：一个是时间到，一个是人喊停。
 */
@Service
public class RunTimeoutMonitor {

    private final RunRepository repo;
    private final RunService runs;
    private final ProcessRegistry registry;
    private final ProcessTreeKiller killer;
    private final LeaseService leases;
    private final Duration runTimeout;
    private final Clock clock;

    public RunTimeoutMonitor(RunRepository repo, RunService runs, ProcessRegistry registry,
                             ProcessTreeKiller killer, LeaseService leases,
                             ExecutionProperties props, Clock executionClock) {
        this.repo = repo;
        this.runs = runs;
        this.registry = registry;
        this.killer = killer;
        this.leases = leases;
        this.runTimeout = Duration.ofSeconds(props.getRunTimeoutSeconds());
        this.clock = executionClock;
    }

    public List<String> check(Instant now) {
        List<String> expired = new ArrayList<>();
        for (RunRecord run : repo.findActive()) {
            if (run.state() != RunState.RUNNING || run.startedAt() == null) {
                continue;
            }
            if (now.isBefore(run.startedAt().plus(runTimeout))) {
                continue;
            }
            if (expire(run, now)) {
                expired.add(run.runId());
            }
        }
        return expired;
    }

    private boolean expire(RunRecord run, Instant now) {
        return runAs(null, "system", run.projectId(), () -> {
            for (Process process : registry.processes(run.runId())) {
                killer.kill(process, Duration.ofSeconds(5));
            }
            registry.unregister(run.runId());
            leases.releaseRun(run.runId(), "timeout");
            try {
                runs.transition(run.runId(), RunState.FAILED,
                        new RunService.TransitionContext(null, now,
                                FailureClass.INFRA.name(), "timeout",
                                "运行超过 " + runTimeout.toSeconds() + " 秒未结束", false, true));
                return true;
            } catch (BusinessException alreadyTerminal) {
                return false;
            }
        });
    }

    private static boolean runAs(Long userId, String username, long projectId,
                                 java.util.function.BooleanSupplier action) {
        boolean[] result = {false};
        TenantContext.runAs(userId, username, projectId, () -> result[0] = action.getAsBoolean());
        return result[0];
    }
}
