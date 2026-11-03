package com.qa.autotest.application.execution;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.Lease;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 取消语义：非终态可取消、终态拒绝、租约与进程一并收尾。 */
class CancelServiceTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;

    private final InMemoryRunRepository runsRepo = new InMemoryRunRepository();
    private final InMemoryLeaseRepository leaseRepo = new InMemoryLeaseRepository();
    private final ProcessRegistry registry = new ProcessRegistry();

    private final CancelService service = new CancelService(
            ExecutionTestSupport.runService(runsRepo), ExecutionTestSupport.access(),
            registry, new ProcessTreeKiller(),
            new LeaseService(leaseRepo, ExecutionTestSupport.CLOCK,
                    java.time.Duration.ofSeconds(120), java.time.Duration.ofSeconds(40), d -> { }),
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
    void 取消运行释放租约并落_CANCELED() {
        runsRepo.seed(ExecutionTestSupport.run("RUN-1", RunState.RUNNING,
                ExecutionTestSupport.CLOCK.instant()));
        leaseRepo.seed(new Lease("GPU-01#0", 1L, "RUN-1", PROJECT,
                ExecutionTestSupport.CLOCK.instant(),
                ExecutionTestSupport.CLOCK.instant().plusSeconds(120)));

        RunRecord canceled = service.cancel(PROJECT, "RUN-1", "人工取消");

        assertEquals(RunState.CANCELED, canceled.state());
        assertEquals("CANCELED", canceled.failureClass());
        assertEquals("canceled", canceled.failureReason());
        assertTrue(leaseRepo.peek("GPU-01#0").isEmpty());
        assertEquals("released", leaseRepo.history().get(0).state());
    }

    @Test
    void 终态运行拒绝取消() {
        runsRepo.seed(ExecutionTestSupport.run("RUN-1", RunState.FINISHED,
                ExecutionTestSupport.CLOCK.instant()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(PROJECT, "RUN-1", "人工取消"));
        assertEquals(ErrorCode.CONFLICT, ex.errorCode());
        assertFalse(runsRepo.peek("RUN-1").orElseThrow().state().active());
    }
}
