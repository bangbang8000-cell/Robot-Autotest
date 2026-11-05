package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** EXEC-07 / FR-v0.4-10：管理员强制释放租约。 */
class LeaseAdminServiceTest {

    private static final long PROJECT = ExecutionTestSupport.PROJECT;
    private static final String RUN = "RUN-LEASE-1";
    private static final String KEY = "GPU-01#mlx5_0";

    private InMemoryLeaseRepository leases;
    private InMemoryRunRepository runRepo;
    private LeaseService leaseService;
    private final ProcessRegistry registry = new ProcessRegistry();

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
        leases = new InMemoryLeaseRepository();
        runRepo = new InMemoryRunRepository();
        runRepo.insert(ExecutionTestSupport.run(RUN, RunState.RUNNING,
                ExecutionTestSupport.CLOCK.instant()), "JOB-1", null, 1L);
        leaseService = new LeaseService(leases, ExecutionTestSupport.CLOCK,
                Duration.ofSeconds(30), Duration.ofSeconds(10), millis -> { }, null);
        leaseService.acquire(new LeaseService.LeaseRequest(KEY, 1L, RUN, PROJECT));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("非管理员：403，且租约不被释放")
    void 非管理员被拒() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(viewerAccess()).forceRelease(PROJECT, KEY, RUN));

        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        assertTrue(leases.peek(KEY).isPresent(), "越权请求不应摘掉租约");
    }

    @Test
    @DisplayName("资源没有生效租约：404")
    void 租约不存在() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(ExecutionTestSupport.access())
                        .forceRelease(PROJECT, "GPU-99#mlx5_9", "RUN-X"));

        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode());
        assertTrue(ex.getMessage().contains("GPU-99#mlx5_9"), ex.getMessage());
    }

    @Test
    @DisplayName("confirm 不匹配 runId：400，租约仍在")
    void 二次确认不足() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(ExecutionTestSupport.access()).forceRelease(PROJECT, KEY, "RUN-OTHER"));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains(RUN), ex.getMessage());
        assertTrue(leases.peek(KEY).isPresent(), "确认失败不应摘掉租约");
    }

    @Test
    @DisplayName("释放成功：租约消失、历史留 lease_revoked、运行标记 failed(lease_revoked)")
    void 释放成功() {
        LeaseAdminService.ForceReleaseResult result = service(ExecutionTestSupport.access())
                .forceRelease(PROJECT, KEY, RUN);

        assertEquals(RUN, result.runId());
        assertEquals(RunState.FAILED.name(), result.runState());
        assertEquals(1, result.releasedLeases());
        assertFalse(leases.peek(KEY).isPresent());
        assertTrue(leases.history().stream().anyMatch(row -> "lease_revoked".equals(row.reason())));
        RunRecord run = runRepo.findByRunId(PROJECT, RUN).orElseThrow();
        assertEquals(RunState.FAILED, run.state());
        assertEquals("lease_revoked", run.failureClass());
        assertEquals("lease_revoked", run.failureReason());
    }

    @Test
    @DisplayName("强制释放：杀掉该 run 的进程树并注销登记，不给下一个任务留僵尸进程")
    void 强制释放杀进程树() throws Exception {
        Process process = new ProcessBuilder(longRunning()).start();
        registry.register(RUN, process);
        assertTrue(process.isAlive(), "前置：子进程应当已启动");

        service(ExecutionTestSupport.access()).forceRelease(PROJECT, KEY, RUN);

        assertFalse(process.isAlive(), "强制释放后原子进程必须已退出");
        assertTrue(registry.processes(RUN).isEmpty(), "释放后必须注销进程登记");
    }

    private static List<String> longRunning() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows");
        return windows
                ? List.of("cmd", "/c", "ping -n 60 127.0.0.1")
                : List.of("sh", "-c", "sleep 60");
    }

    private LeaseAdminService service(AccessControlService access) {
        return new LeaseAdminService(leases, leaseService,
                ExecutionTestSupport.runService(runRepo), registry, new ProcessTreeKiller(),
                access, ExecutionTestSupport.CLOCK);
    }

    private static AccessControlService viewerAccess() {
        UserRepository users = (UserRepository) java.lang.reflect.Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> "roleCodesOf".equals(method.getName())
                        ? List.of("viewer") : defaultValue(method.getReturnType()));
        ProjectRepository projects = (ProjectRepository) java.lang.reflect.Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(), new Class<?>[]{ProjectRepository.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new AccessControlService(users, projects);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == java.util.Optional.class) {
            return java.util.Optional.empty();
        }
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return 0;
    }
}
