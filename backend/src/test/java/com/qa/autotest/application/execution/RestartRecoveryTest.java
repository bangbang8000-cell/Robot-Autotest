package com.qa.autotest.application.execution;

import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.Lease;
import com.qa.autotest.domain.execution.RunState;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 重启恢复：活跃运行一律 FAILED(interrupted)，租约必须回收；再跑一次应为幂等空操作。 */
class RestartRecoveryTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;

    private final InMemoryRunRepository runsRepo = new InMemoryRunRepository();
    private final InMemoryLeaseRepository leaseRepo = new InMemoryLeaseRepository();

    private final RestartRecoveryService service = new RestartRecoveryService(runsRepo,
            ExecutionTestSupport.runService(runsRepo), new ProcessRegistry(),
            new ProcessTreeKiller(),
            new LeaseService(leaseRepo, ExecutionTestSupport.CLOCK,
                    Duration.ofSeconds(120), Duration.ofSeconds(40), d -> { }),
            ExecutionTestSupport.audit(), ExecutionTestSupport.CLOCK);

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void 恢复把活跃运行置为_interrupted_并释放租约() {
        runsRepo.seed(ExecutionTestSupport.run("RUN-P", RunState.PREPARING,
                ExecutionTestSupport.CLOCK.instant()));
        runsRepo.seed(ExecutionTestSupport.run("RUN-R", RunState.RUNNING,
                ExecutionTestSupport.CLOCK.instant()));
        leaseRepo.seed(new Lease("GPU-01#0", 1L, "RUN-P", PROJECT,
                ExecutionTestSupport.CLOCK.instant(),
                ExecutionTestSupport.CLOCK.instant().plusSeconds(120)));

        RestartRecoveryService.RecoveryReport report = service.recover();

        assertEquals(2, report.scanned());
        assertEquals(2, report.recovered());
        assertEquals(1, report.leasesReleased());
        assertEquals(RunState.FAILED, runsRepo.peek("RUN-P").orElseThrow().state());
        assertEquals("interrupted", runsRepo.peek("RUN-P").orElseThrow().failureReason());
        assertTrue(leaseRepo.peek("GPU-01#0").isEmpty());
    }

    @Test
    void 再次恢复不产生重复收尾() {
        runsRepo.seed(ExecutionTestSupport.run("RUN-P", RunState.PREPARING,
                ExecutionTestSupport.CLOCK.instant()));
        service.recover();

        RestartRecoveryService.RecoveryReport second = service.recover();

        assertEquals(0, second.scanned());
        assertEquals(0, second.recovered());
    }
}
