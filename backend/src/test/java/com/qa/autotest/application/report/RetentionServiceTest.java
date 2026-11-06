package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.application.repository.RetentionRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.RetentionArtifact;
import com.qa.autotest.domain.report.RetentionCandidate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 产物清理编排（RPT-06）：dry-run 不落手、执行时删文件并置 purged、
 * 「置 purged + 写审计」同一事务、文件已缺失时下一次自愈。
 *
 * <p>候选集筛选规则本身由 {@link RetentionPlannerTest} 覆盖，这里只验证编排副作用。
 */
class RetentionServiceTest {

    private static final long PROJECT = 9L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private FakeRetentionRepository retention;
    private FakeFileStore files;
    private FakeAuditRepository audit;
    private RetentionProperties properties;
    private CountingTxManager txManager;

    @BeforeEach
    void setUp() {
        TenantContext.set(2L, "ops", PROJECT);
        retention = new FakeRetentionRepository();
        files = new FakeFileStore();
        audit = new FakeAuditRepository();
        properties = new RetentionProperties();
        // 最近 N 次保护由 RetentionPlannerTest 覆盖；这里只留 1 条候选，放开保护便于断言
        properties.setKeepRecentRuns(0);
        txManager = new CountingTxManager();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("dry-run 只出清单：不删文件、不改行、不写审计")
    void dryRunTouchesNothing() {
        seedOldRun(31L, "RPT06-DRY-001", 150L);
        seedOldRun(32L, "RPT06-DRY-002", 40L);

        RetentionService.RetentionResult result = service(true).run(PROJECT, true);

        assertTrue(result.dryRun());
        assertEquals(2, result.runCount());
        assertEquals(190L, result.totalBytes());
        assertEquals(0, result.deletedFiles());
        assertTrue(files.deleted.isEmpty(), "dry-run 不能删文件");
        assertTrue(retention.purged.isEmpty(), "dry-run 不能改 availability");
        assertTrue(audit.entries.isEmpty(), "dry-run 不能写审计");
        assertEquals(0, txManager.begins, "dry-run 不应开启事务");
    }

    @Test
    @DisplayName("执行：删文件 → 置 purged → 写审计，二者同一事务")
    void executeDeletesFilesAndWritesAuditInOneTransaction() {
        seedOldRun(41L, "RPT06-RUN-001", 160L);

        RetentionService.RetentionResult result = service(true).run(PROJECT, false);

        assertFalse(result.dryRun());
        assertEquals(1, result.runCount());
        assertEquals(2, result.deletedFiles());
        assertEquals(2, files.deleted.size());
        assertEquals(Set.of(101L, 102L), retention.purged);
        assertEquals(1, audit.entries.size());
        assertTrue(audit.entries.get(0).contains("artifact_purge"));
        assertTrue(audit.entries.get(0).contains("RPT06-RUN-001"));
        assertEquals(1, txManager.commits, "库侧两处写入必须在一个事务里提交");
        assertEquals(0, txManager.rollbacks);
    }

    @Test
    @DisplayName("审计失败 → 库侧整条回滚，不留「已 purged 无审计」的中间态")
    void auditFailureRollsBackPurge() {
        seedOldRun(51L, "RPT06-FAIL-001", 80L);
        audit.fail = true;

        assertThrows(IllegalStateException.class, () -> service(true).run(PROJECT, false));

        assertEquals(1, txManager.rollbacks);
        assertEquals(0, txManager.commits);
        assertEquals(2, files.deleted.size(), "文件删除在事务之前，已不可回滚");
    }

    @Test
    @DisplayName("文件已不存在 → 仍置 purged 并补审计（下一次清理自愈）")
    void missingFileIsHealedOnNextRun() {
        seedOldRun(61L, "RPT06-HEAL-001", 70L);
        files.present.clear();
        files.deleted.clear();

        RetentionService.RetentionResult result = service(true).run(PROJECT, false);

        assertEquals(0, result.deletedFiles(), "文件本就不存在，不重复计数");
        assertEquals(Set.of(101L, 102L), retention.purged, "行仍要置 purged，否则占用永远算不清");
        assertEquals(1, audit.entries.size(), "补写审计，保证 purged 有据可查");
    }

    @Test
    @DisplayName("非管理员 → 403，且不碰文件")
    void nonAdminRejected() {
        seedOldRun(71L, "RPT06-DENY-001", 10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(false).run(PROJECT, false));

        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
        assertTrue(files.deleted.isEmpty());
        assertTrue(retention.purged.isEmpty());
    }

    private RetentionService service(boolean admin) {
        AccessControlService access = new AccessControlService(null, null) {
            @Override
            public void bindProject(long projectId) {
                TenantContext.setProjectId(projectId);
            }

            @Override
            public void require(PermissionResource resource, PermissionAction action) {
                // 权限矩阵由 AuthorizationBoundaryTest 覆盖，这里只区分管理员/非管理员
            }

            @Override
            public boolean isAdmin() {
                return admin;
            }
        };
        return new RetentionService(retention, files, audit, properties, access, CLOCK,
                new TransactionTemplate(txManager));
    }

    /** 一条 200 天前的运行 + 两个产物文件（101/102 各 100 字节登记占用的一半）。 */
    private void seedOldRun(long runDbId, String runId, long bytes) {
        Instant startedAt = CLOCK.instant().minus(Duration.ofDays(200));
        retention.candidates.add(new RetentionCandidate(runDbId, runId, startedAt, bytes,
                2, false, false, false));
        String dir = "/tmp/rpt06/" + runId.toString();
        retention.artifacts.put(runDbId, List.of(
                new RetentionArtifact(101L, dir + "/output.xml", bytes / 2, false, null),
                new RetentionArtifact(102L, dir + "/log.html", bytes - bytes / 2, false, null)));
        files.present.add(dir + "/output.xml");
        files.present.add(dir + "/log.html");
    }

    /** 候选与产物行：只实现清理编排用到的方法。 */
    static final class FakeRetentionRepository implements RetentionRepository {

        final List<RetentionCandidate> candidates = new ArrayList<>();
        final Map<Long, List<RetentionArtifact>> artifacts = new LinkedHashMap<>();
        final Set<Long> purged = new LinkedHashSet<>();

        @Override
        public List<RetentionCandidate> listCandidates(long projectId) {
            return candidates;
        }

        @Override
        public List<RetentionArtifact> listArtifacts(long projectId, long runDbId) {
            return new ArrayList<>(artifacts.getOrDefault(runDbId, List.of()));
        }

        @Override
        public long totalBytes(long projectId) {
            return candidates.stream().mapToLong(RetentionCandidate::bytes).sum();
        }

        @Override
        public void markPurged(long projectId, long runDbId, List<Long> artifactIds) {
            purged.addAll(artifactIds);
        }

        @Override
        public List<Long> listProjectIds() {
            return List.of(PROJECT);
        }
    }

    /** 内存文件表：present 里有才会真删，删过的进 deleted。 */
    static final class FakeFileStore implements ArtifactFileStore {

        final Set<String> present = new LinkedHashSet<>();
        final Set<String> deleted = new LinkedHashSet<>();

        @Override
        public boolean delete(String path) {
            if (present.remove(path)) {
                deleted.add(path);
                return true;
            }
            return false;
        }
    }

    /** 审计伪造：fail=true 时模拟审计库不可用。 */
    static final class FakeAuditRepository implements AuditRepository {

        final List<String> entries = new ArrayList<>();
        boolean fail;

        @Override
        public void insert(Long projectId, Long actorId, String action, String resourceType,
                           String resourceId, String detail) {
            if (fail) {
                throw new IllegalStateException("审计库不可用");
            }
            entries.add(action + "|" + resourceType + "|" + resourceId + "|" + detail);
        }

        @Override
        public List<String> recentActions(Long projectId, int limit) {
            return List.of();
        }
    }

    /** 事务计数：只验证回调是否被事务包裹、异常时是否走回滚。 */
    static final class CountingTxManager implements PlatformTransactionManager {

        int begins;
        int commits;
        int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begins++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
