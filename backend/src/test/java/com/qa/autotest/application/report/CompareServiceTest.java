package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.ReportReadRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.report.CaseLogRef;
import com.qa.autotest.domain.report.CaseVerdict;
import com.qa.autotest.domain.report.KeywordRow;
import com.qa.autotest.domain.report.RunCaseRow;
import com.qa.autotest.domain.report.RunSnapshot;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 双列并置契约（RPT-03 / DEV-PLAN §4.2）的行为测试。
 *
 * <p>不连库：{@link ReportReadRepository} 用内存伪造实现，环境匹配与「最近一次」由伪造实现
 * 按真实 SQL 语义执行，从而验证服务传入的 projectId / DUT / LINK / started_at / excludeRunId 是否正确。
 */
class CompareServiceTest {

    private static final long PROJECT = 7L;
    private static final long OTHER_PROJECT = 8L;
    private static final String CURRENT = "RUN-CUR";
    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-10T00:00:00Z");

    private FakeReadRepository read;
    private CompareService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
        read = new FakeReadRepository();
        service = new CompareService(read, new AccessControlService(adminUsers(), emptyProjects()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("自动基线：同项目同环境且 started_at 早于本轮，取最近一条")
    void 自动基线取最近一条() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "OLD", "GPU-01", "mlx5_0", T0);
        read.addRun(PROJECT, "NEAR", "GPU-01", "mlx5_0", T1);
        read.addRun(PROJECT, "OTHER-ENV", "GPU-02", "mlx5_0", T1.plusSeconds(60));
        read.addRun(PROJECT, "LATER", "GPU-01", "mlx5_0", T2.plusSeconds(60));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);

        assertNotNull(result.baseline());
        assertEquals("NEAR", result.baseline().runId());
        assertEquals(T1, result.baseline().startedAt());
    }

    @Test
    @DisplayName("找不到基线：baseline=null，不报错")
    void 找不到基线不报错() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);

        assertNull(result.baseline());
        assertEquals(0, result.summary().notRun());
        assertTrue(result.rows().isEmpty());
    }

    @Test
    @DisplayName("手动基线跨项目：400 中文原因，不静默回落自动基线")
    void 手动基线跨项目拒绝() {
        read.addRun(OTHER_PROJECT, "FOREIGN", "GPU-01", "mlx5_0", T1);
        read.addRun(PROJECT, "AUTO", "GPU-01", "mlx5_0", T1);
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.compare(PROJECT, CURRENT, "FOREIGN"));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("不属于当前项目"));
        assertFalse(read.baselineCalls > 0, "跨项目拒绝后不得再走自动基线");
    }

    @Test
    @DisplayName("手动基线跨环境：N4 放行并显式标记 crossEnvironment=true")
    void 手动基线跨环境放行并标记() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "DIFF-DUT", "GPU-02", "mlx5_0", T1);
        read.addRun(PROJECT, "DIFF-LINK", "GPU-01", "mlx5_1", T1);

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, "DIFF-DUT");

        assertEquals("DIFF-DUT", result.baseline().runId());
        assertTrue(result.crossEnvironment(), "跨环境必须显式标记，不能静默当同环境");
        assertTrue(result.baseline().crossEnvironment());
        assertEquals("GPU-02", result.baseline().dut());
        assertEquals(0, read.baselineCalls, "手动模式不应再调用自动基线查询");

        CompareService.CompareResult linkDiff = service.compare(PROJECT, CURRENT, "DIFF-LINK");
        assertEquals("mlx5_1", linkDiff.baseline().link());
        assertTrue(linkDiff.crossEnvironment());
    }

    @Test
    @DisplayName("自动基线永远是同环境：crossEnvironment=false")
    void 自动基线不跨环境() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "NEAR", "GPU-01", "mlx5_0", T1);

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);

        assertFalse(result.crossEnvironment());
        assertFalse(result.baseline().crossEnvironment());
    }

    @Test
    @DisplayName("手动基线候选：同环境优先，跨环境随后且逐条标记")
    void 手动基线候选含跨环境() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "OLD", "GPU-01", "mlx5_0", T0);
        read.addRun(PROJECT, "NEAR", "GPU-01", "mlx5_0", T1);
        read.addRun(PROJECT, "OTHER-DUT", "GPU-02", "mlx5_0", T1);
        read.addRun(PROJECT, "OTHER-LINK", "GPU-01", "mlx5_1", T1);
        read.addRun(OTHER_PROJECT, "FOREIGN", "GPU-01", "mlx5_0", T1);

        List<CompareService.BaselineCandidate> candidates = service.candidates(PROJECT, CURRENT);

        // 排序：同环境优先 → started_at DESC → id DESC。两条跨环境候选 started_at 同为 T1，
        // 按 id DESC 即插入倒序，所以 OTHER-LINK 在 OTHER-DUT 之前。
        assertEquals(List.of("NEAR", "OLD", "OTHER-LINK", "OTHER-DUT"),
                candidates.stream().map(CompareService.BaselineCandidate::runId).toList());
        assertEquals(T1, candidates.get(0).startedAt());
        assertFalse(candidates.get(0).crossEnvironment());
        assertTrue(candidates.get(2).crossEnvironment(), "跨 DUT 候选要带标记");
        assertTrue(candidates.get(3).crossEnvironment(), "跨 LINK 候选要带标记");
    }

    @Test
    @DisplayName("手动基线候选：运行不存在时 404")
    void 手动基线候选运行不存在() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.candidates(PROJECT, "MISSING"));

        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode());
    }

    @Test
    @DisplayName("关键：本轮未执行的用例 current 必须为 null，禁止用历史值填充")
    void current未执行必须为null() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "BASE", "GPU-01", "mlx5_0", T1);
        read.cases("BASE", new CaseVerdict("Suite.CASE-A", "PASS", 10));
        read.cases(CURRENT, new CaseVerdict("Suite.CASE-B", "PASS", 20));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);
        CompareService.CompareRow row = rowOf(result, "Suite.CASE-A");

        assertNull(row.current(), "本轮没跑 CASE-A，current 必须为 null");
        assertNotNull(row.history(), "历史侧保留基线值");
        assertEquals("PASS", row.history().verdict());
        assertEquals("NOT_RUN", row.change());
        assertEquals(1, result.summary().notRun());
    }

    @Test
    @DisplayName("历史未执行的用例 history 必须为 null，不得用本轮值填充")
    void history未执行为null() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "BASE", "GPU-01", "mlx5_0", T1);
        read.cases("BASE", new CaseVerdict("Suite.CASE-A", "PASS", 10));
        read.cases(CURRENT, new CaseVerdict("Suite.CASE-NEW", "FAIL", 20));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);
        CompareService.CompareRow row = rowOf(result, "Suite.CASE-NEW");

        assertNull(row.history(), "基线没跑 CASE-NEW，history 必须为 null");
        assertNotNull(row.current());
        assertEquals("FAIL", row.current().verdict());
        assertEquals("PASS_TO_FAIL", row.change());
    }

    @Test
    @DisplayName("PASS→FAIL 判为回归")
    void pass转fail() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "BASE", "GPU-01", "mlx5_0", T1);
        read.cases("BASE", new CaseVerdict("Suite.CASE-A", "PASS", 10));
        read.cases(CURRENT, new CaseVerdict("Suite.CASE-A", "FAIL", 20));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);
        CompareService.CompareRow row = rowOf(result, "Suite.CASE-A");

        assertEquals("FAIL", row.current().verdict());
        assertEquals("PASS", row.history().verdict());
        assertEquals("PASS_TO_FAIL", row.change());
        assertEquals(1, result.summary().newFail());
    }

    @Test
    @DisplayName("FAIL→PASS 判为修复")
    void fail转pass() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "BASE", "GPU-01", "mlx5_0", T1);
        read.cases("BASE", new CaseVerdict("Suite.CASE-A", "FAIL", 10));
        read.cases(CURRENT, new CaseVerdict("Suite.CASE-A", "PASS", 20));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);
        CompareService.CompareRow row = rowOf(result, "Suite.CASE-A");

        assertEquals("FAIL_TO_PASS", row.change());
        assertEquals(1, result.summary().newPass());
    }

    @Test
    @DisplayName("SAME 与 NOT_RUN：双列并集的边界")
    void same与notRun() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "BASE", "GPU-01", "mlx5_0", T1);
        read.cases("BASE", new CaseVerdict("Suite.KEEP", "PASS", 10),
                new CaseVerdict("Suite.GONE", "FAIL", 10));
        read.cases(CURRENT, new CaseVerdict("Suite.KEEP", "PASS", 20));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);

        assertEquals("SAME", rowOf(result, "Suite.KEEP").change());
        assertEquals("NOT_RUN", rowOf(result, "Suite.GONE").change());
        assertNull(rowOf(result, "Suite.GONE").current());
        assertEquals(1, result.summary().notRun());
    }

    @Test
    @DisplayName("summary 四类计数：newFail/newPass/bothFail/notRun")
    void summary计数() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "BASE", "GPU-01", "mlx5_0", T1);
        read.cases("BASE",
                new CaseVerdict("Suite.A", "FAIL", 1),
                new CaseVerdict("Suite.B", "PASS", 1),
                new CaseVerdict("Suite.C", "FAIL", 1),
                new CaseVerdict("Suite.D", "PASS", 1));
        read.cases(CURRENT,
                new CaseVerdict("Suite.A", "FAIL", 2),
                new CaseVerdict("Suite.B", "PASS", 2),
                new CaseVerdict("Suite.C", "PASS", 2),
                new CaseVerdict("Suite.E", "FAIL", 2));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, null);

        CompareService.CompareSummary summary = result.summary();
        assertEquals(1, summary.newFail(), "CASE-E 新增失败");
        assertEquals(1, summary.newPass(), "CASE-C 由失败转通过");
        assertEquals(1, summary.bothFail(), "CASE-A 持续失败");
        assertEquals(1, summary.notRun(), "CASE-D 本轮未执行");
    }

    @Test
    @DisplayName("手动基线同项目同环境时被采用（不退回自动基线）")
    void 手动基线被采用() {
        read.addRun(PROJECT, CURRENT, "GPU-01", "mlx5_0", T2);
        read.addRun(PROJECT, "AUTO", "GPU-01", "mlx5_0", T1);
        read.addRun(PROJECT, "MANUAL", "GPU-01", "mlx5_0", T0);
        read.cases("MANUAL", new CaseVerdict("Suite.A", "FAIL", 1));
        read.cases(CURRENT, new CaseVerdict("Suite.A", "PASS", 2));

        CompareService.CompareResult result = service.compare(PROJECT, CURRENT, "MANUAL");

        assertEquals("MANUAL", result.baseline().runId());
        assertEquals("FAIL_TO_PASS", rowOf(result, "Suite.A").change());
        assertEquals(0, read.baselineCalls, "手动模式不应再调用自动基线查询");
    }

    private static CompareService.CompareRow rowOf(CompareService.CompareResult result, String caseKey) {
        return result.rows().stream()
                .filter(row -> row.caseKey().equals(caseKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少对比行: " + caseKey));
    }

    private static UserRepository adminUsers() {
        return proxy(UserRepository.class, (proxy, method, args) ->
                "roleCodesOf".equals(method.getName()) ? List.of("admin") : defaultValue(method.getReturnType()));
    }

    private static ProjectRepository emptyProjects() {
        return proxy(ProjectRepository.class, (proxy, method, args) ->
                defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        return null;
    }

    /** 内存仓储：按真实 SQL 语义做「同项目 + finished + 同环境 + before + 排除本轮 + 最近一次」。 */
    private static final class FakeReadRepository implements ReportReadRepository {

        private final Map<Long, Map<String, RunSnapshot>> runs = new LinkedHashMap<>();
        private final Map<String, List<CaseVerdict>> cases = new LinkedHashMap<>();
        private int baselineCalls;

        private void addRun(long projectId, String runId, String dut, String link, Instant startedAt) {
            runs.computeIfAbsent(projectId, key -> new LinkedHashMap<>())
                    .put(runId, new RunSnapshot(projectId, runId, "finished", dut, link, startedAt));
        }

        private void cases(String runId, CaseVerdict... verdicts) {
            cases.put(runId, List.of(verdicts));
        }

        @Override
        public Optional<RunSnapshot> findRun(long projectId, String runId) {
            return Optional.ofNullable(runs.getOrDefault(projectId, Map.of()).get(runId));
        }

        @Override
        public Optional<RunSnapshot> findBaseline(long projectId, String dut, String link,
                                                  Instant before, String excludeRunId) {
            baselineCalls++;
            if (before == null) {
                return Optional.empty();
            }
            List<RunSnapshot> candidates = new ArrayList<>();
            for (RunSnapshot run : runs.getOrDefault(projectId, Map.of()).values()) {
                if (!"finished".equals(run.state())) {
                    continue;
                }
                if (!Objects.equals(dut == null ? "" : dut, run.dut() == null ? "" : run.dut())) {
                    continue;
                }
                if (!Objects.equals(link == null ? "" : link, run.link() == null ? "" : run.link())) {
                    continue;
                }
                if (run.startedAt() == null || run.startedAt().isAfter(before) || run.startedAt().equals(before)) {
                    continue;
                }
                if (Objects.equals(run.runId(), excludeRunId)) {
                    continue;
                }
                candidates.add(run);
            }
            return candidates.stream().max(Comparator.comparing(RunSnapshot::startedAt));
        }

        @Override
        public List<RunSnapshot> listBaselineCandidates(long projectId, String dut, String link,
                                                        String excludeRunId, int limit) {
            List<RunSnapshot> matched = new ArrayList<>();
            for (RunSnapshot run : runs.getOrDefault(projectId, Map.of()).values()) {
                if (!"finished".equals(run.state())) {
                    continue;
                }
                if (Objects.equals(run.runId(), excludeRunId)) {
                    continue;
                }
                matched.add(run);
            }
            // 与 SQL 对齐：同 DUT/LINK 优先，其次 started_at DESC，最后 id DESC（用插入顺序倒序模拟）。
            List<RunSnapshot> ordered = new ArrayList<>(matched);
            ordered.sort((left, right) -> {
                boolean sameLeft = sameEnv(dut, link, left);
                boolean sameRight = sameEnv(dut, link, right);
                if (sameLeft != sameRight) {
                    return sameLeft ? -1 : 1;
                }
                if (left.startedAt() == null || right.startedAt() == null) {
                    return left.startedAt() == null ? 1 : -1;
                }
                if (!left.startedAt().equals(right.startedAt())) {
                    return right.startedAt().compareTo(left.startedAt());
                }
                return Integer.compare(matched.indexOf(right), matched.indexOf(left));
            });
            return ordered.stream().limit(limit).toList();
        }

        private static boolean sameEnv(String dut, String link, RunSnapshot run) {
            return Objects.equals(dut == null ? "" : dut, run.dut() == null ? "" : run.dut())
                    && Objects.equals(link == null ? "" : link, run.link() == null ? "" : run.link());
        }

        @Override
        public List<CaseVerdict> listCaseVerdicts(long projectId, String runId) {
            return cases.getOrDefault(runId, List.of());
        }

        @Override
        public List<RunCaseRow> listCases(long projectId, String runId) {
            return List.of();
        }

        @Override
        public List<KeywordRow> listKeywords(long projectId, String runId) {
            return List.of();
        }

        @Override
        public Optional<CaseLogRef> findCaseLog(long projectId, String runId, String caseKey) {
            return Optional.empty();
        }
    }
}
