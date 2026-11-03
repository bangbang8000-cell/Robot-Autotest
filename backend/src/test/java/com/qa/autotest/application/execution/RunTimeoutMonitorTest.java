package com.qa.autotest.application.execution;

import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.Lease;
import com.qa.autotest.domain.execution.RunState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 超时监控：只看 running 且 startedAt 已超阈值的运行。 */
class RunTimeoutMonitorTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;

    private final InMemoryRunRepository runsRepo = new InMemoryRunRepository();
    private final InMemoryLeaseRepository leaseRepo = new InMemoryLeaseRepository();

    private final RunTimeoutMonitor monitor = new RunTimeoutMonitor(runsRepo,
            ExecutionTestSupport.runService(runsRepo), new ProcessRegistry(),
            new ProcessTreeKiller(),
            new LeaseService(leaseRepo, ExecutionTestSupport.CLOCK,
                    Duration.ofSeconds(120), Duration.ofSeconds(40), d -> { }),
            ExecutionTestSupport.props(), ExecutionTestSupport.CLOCK);

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void 超过阈值的运行被收尾为_FAILED_timeout() {
        Instant now = ExecutionTestSupport.CLOCK.instant();
        long timeoutSeconds = ExecutionTestSupport.props().getRunTimeoutSeconds();
        runsRepo.seed(ExecutionTestSupport.run("RUN-OLD", RunState.RUNNING,
                now.minus(Duration.ofSeconds(timeoutSeconds + 60))));
        leaseRepo.seed(new Lease("GPU-01#0", 1L, "RUN-OLD", PROJECT,
                now.minusSeconds(600), now.plusSeconds(60)));

        java.util.List<String> expired = monitor.check(now);
        assertEquals(java.util.List.of("RUN-OLD"), expired);
        assertEquals("timeout", runsRepo.peek("RUN-OLD").orElseThrow().failureReason());
        assertTrue(leaseRepo.peek("GPU-01#0").isEmpty());
    }

    @Test
    void 未到阈值不处理() {
        Instant now = ExecutionTestSupport.CLOCK.instant();
        runsRepo.seed(ExecutionTestSupport.run("RUN-NEW", RunState.RUNNING, now.minusSeconds(10)));

        assertTrue(monitor.check(now).isEmpty());
        assertEquals(RunState.RUNNING, runsRepo.peek("RUN-NEW").orElseThrow().state());
    }

    @Test
    void 只处理_running_不处理排队() {
        runsRepo.seed(ExecutionTestSupport.run("RUN-QUEUED", RunState.QUEUED,
                ExecutionTestSupport.CLOCK.instant().minusSeconds(7200)));
        assertTrue(monitor.check(ExecutionTestSupport.CLOCK.instant()).isEmpty());
    }
}
