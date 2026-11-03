package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.TrendRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.report.PassRateCount;
import com.qa.autotest.domain.report.RunSnapshot;
import com.qa.autotest.domain.report.TrendEnv;
import com.qa.autotest.domain.report.TrendPoint;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * 趋势与性能基线（RPT-04）的行为测试。
 *
 * <p>不连库：{@link TrendRepository} 用内存实现，模拟真实 SQL 的「同项目 + 同 case_key +
 * 最近 N 条 + 升序」语义，从而验证服务对排序、同环境过滤、断线分组、中位数与偏离边界的处理。
 */
class TrendServiceTest {

    private static final long PROJECT = 9L;
    private static final long OTHER_PROJECT = 10L;
    private static final String CASE = "Suite.RDMA-BW-001";
    private static final String CURRENT = "RUN-CUR";
    private static final String DUT = "GPU-01";
    private static final String LINK = "mlx5_0";

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    private FakeTrendRepository trend;
    private TrendService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, "admin", PROJECT);
        trend = new FakeTrendRepository();
        service = new TrendService(trend, new AccessControlService(adminUsers(), emptyProjects()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("最近 5 次：只返回最后 5 个点且按时间升序")
    void 最近五次排序() {
        for (int i = 0; i < 7; i++) {
            addPoint(PROJECT, "R" + i, CASE, T0.plusSeconds(i * 3600), "PASS", 100 + i, DUT, LINK);
        }
        trend.addRun(PROJECT, "R6", DUT, LINK, T0.plusSeconds(6 * 3600));

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, "R6", CASE, 5);

        assertEquals(5, result.points().size());
        assertEquals("R2", result.points().get(0).runId());
        assertEquals("R6", result.points().get(4).runId());
        for (int i = 1; i < result.points().size(); i++) {
            assertTrue(result.points().get(i - 1).startedAt()
                    .isBefore(result.points().get(i).startedAt()), "点必须按时间升序");
        }
    }

    @Test
    @DisplayName("同环境过滤：基线只取当前 DUT+LINK 的历史，其他环境不参与中位数")
    void 同环境过滤() {
        addPoint(PROJECT, "SAME-1", CASE, T0, "PASS", 100, DUT, LINK);
        addPoint(PROJECT, "SAME-2", CASE, T0.plusSeconds(3600), "PASS", 300, DUT, LINK);
        addPoint(PROJECT, "OTHER-ENV", CASE, T0.plusSeconds(7200), "FAIL", 900, "GPU-02", LINK);
        addPoint(PROJECT, CURRENT, CASE, T0.plusSeconds(10800), "PASS", 200, DUT, LINK);
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0.plusSeconds(10800));

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, CURRENT, CASE, 5);

        assertEquals(200L, result.baselineMs(), "仅同环境 100/300 的中位数为 200");
        assertEquals(TrendEnv.envKey(DUT, LINK), result.currentEnvKey());
    }

    @Test
    @DisplayName("换环境断线：不同 envKey 分成多条 series，不混成一条线")
    void 换环境断线() {
        addPoint(PROJECT, "A1", CASE, T0, "PASS", 100, DUT, LINK);
        addPoint(PROJECT, "A2", CASE, T0.plusSeconds(3600), "PASS", 120, DUT, LINK);
        addPoint(PROJECT, "B1", CASE, T0.plusSeconds(7200), "PASS", 200, "GPU-02", LINK);
        addPoint(PROJECT, "B2", CASE, T0.plusSeconds(10800), "PASS", 220, "GPU-02", LINK);
        trend.addRun(PROJECT, "B2", "GPU-02", LINK, T0.plusSeconds(10800));

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, "B2", CASE, 5);

        assertEquals(2, result.series().size(), "两个环境必须是两条线");
        for (TrendService.TrendSeries series : result.series()) {
            for (TrendService.TrendPointView point : series.points()) {
                assertEquals(series.envKey(), point.envKey(), "同一条线内环境必须一致");
            }
        }
        assertEquals(TrendEnv.envKey("GPU-02", LINK), result.currentEnvKey());
    }

    @Test
    @DisplayName("中位数（奇数）：取中间值")
    void 中位数奇数() {
        addPoint(PROJECT, "H1", CASE, T0, "PASS", 100, DUT, LINK);
        addPoint(PROJECT, "H2", CASE, T0.plusSeconds(3600), "PASS", 200, DUT, LINK);
        addPoint(PROJECT, "H3", CASE, T0.plusSeconds(7200), "PASS", 300, DUT, LINK);
        addPoint(PROJECT, CURRENT, CASE, T0.plusSeconds(10800), "PASS", 1000, DUT, LINK);
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0.plusSeconds(10800));

        assertEquals(200L, service.caseTrend(PROJECT, CURRENT, CASE, 5).baselineMs());
    }

    @Test
    @DisplayName("中位数（偶数）：取中间两个的平均值")
    void 中位数偶数() {
        addPoint(PROJECT, "H1", CASE, T0, "PASS", 100, DUT, LINK);
        addPoint(PROJECT, "H2", CASE, T0.plusSeconds(3600), "PASS", 200, DUT, LINK);
        addPoint(PROJECT, "H3", CASE, T0.plusSeconds(7200), "PASS", 300, DUT, LINK);
        addPoint(PROJECT, "H4", CASE, T0.plusSeconds(10800), "PASS", 400, DUT, LINK);
        addPoint(PROJECT, CURRENT, CASE, T0.plusSeconds(14400), "PASS", 1000, DUT, LINK);
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0.plusSeconds(14400));

        assertEquals(250L, service.caseTrend(PROJECT, CURRENT, CASE, 5).baselineMs());
    }

    @Test
    @DisplayName("偏离边界：等于 1.5 倍不算偏离")
    void 偏离等于不算() {
        addPoint(PROJECT, "H1", CASE, T0, "PASS", 100, DUT, LINK);
        addPoint(PROJECT, CURRENT, CASE, T0.plusSeconds(3600), "PASS", 150, DUT, LINK);
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0.plusSeconds(3600));

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, CURRENT, CASE, 5);

        assertEquals(100L, result.baselineMs());
        assertFalse(result.deviated());
    }

    @Test
    @DisplayName("偏离边界：超过 1.5 倍判为偏离，且当前点标记 deviated")
    void 偏离超过算() {
        addPoint(PROJECT, "H1", CASE, T0, "PASS", 100, DUT, LINK);
        addPoint(PROJECT, CURRENT, CASE, T0.plusSeconds(3600), "PASS", 151, DUT, LINK);
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0.plusSeconds(3600));

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, CURRENT, CASE, 5);

        assertTrue(result.deviated());
        TrendService.TrendPointView current = result.points().stream()
                .filter(point -> point.runId().equals(CURRENT))
                .findFirst().orElseThrow();
        assertTrue(current.deviated());
    }

    @Test
    @DisplayName("通过率按天聚合：total 与 passRate 由服务计算")
    void 通过率按天聚合() {
        trend.dayRows = List.of(
                new PassRateCount("2026-10-01", 5, 3, 2),
                new PassRateCount("2026-10-02", 4, 1, 0));

        TrendService.PassRateResult result = service.passRate(PROJECT, "day", 12);

        assertEquals("day", result.granularity());
        assertEquals(2, result.buckets().size());
        assertEquals("2026-10-01", result.buckets().get(0).bucket());
        assertEquals(10, result.buckets().get(0).total());
        assertEquals(0.5, result.buckets().get(0).passRate());
        assertEquals(5, result.buckets().get(1).total());
        assertEquals(0.8, result.buckets().get(1).passRate());
    }

    @Test
    @DisplayName("通过率按周聚合：granularity=week 原样透传仓储")
    void 通过率按周聚合() {
        trend.weekRows = List.of(new PassRateCount("2026-44", 20, 4, 1));

        TrendService.PassRateResult result = service.passRate(PROJECT, "week", 12);

        assertEquals("week", result.granularity());
        assertEquals("2026-44", result.buckets().get(0).bucket());
        assertEquals(25, result.buckets().get(0).total());
        assertEquals(0.8, result.buckets().get(0).passRate());
        assertEquals("week", trend.lastPassRateGranularity);
    }

    @Test
    @DisplayName("空数据：points 为空、baseline=null、deviated=false、series 为空")
    void 空数据() {
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0);

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, CURRENT, CASE, 5);

        assertTrue(result.points().isEmpty());
        assertTrue(result.series().isEmpty());
        assertNull(result.baselineMs());
        assertFalse(result.deviated());
        assertNotNull(result.currentEnvKey());
    }

    @Test
    @DisplayName("跨项目不泄漏：只返回当前 project 的点和桶")
    void 跨项目不泄漏() {
        addPoint(OTHER_PROJECT, "FOREIGN", CASE, T0, "PASS", 999, DUT, LINK);
        addPoint(PROJECT, CURRENT, CASE, T0.plusSeconds(3600), "PASS", 100, DUT, LINK);
        trend.addRun(PROJECT, CURRENT, DUT, LINK, T0.plusSeconds(3600));
        trend.foreignDayRows = List.of(new PassRateCount("2026-10-01", 99, 0, 0));

        TrendService.CaseTrendResult result = service.caseTrend(PROJECT, CURRENT, CASE, 5);
        TrendService.PassRateResult passRate = service.passRate(PROJECT, "day", 12);

        assertEquals(1, result.points().size());
        assertEquals(CURRENT, result.points().get(0).runId());
        assertNull(result.baselineMs(), "跨项目点不得进入基线");
        assertTrue(passRate.buckets().isEmpty());
        assertEquals(PROJECT, trend.lastProjectId);
    }

    @Test
    @DisplayName("非法 granularity：400 中文原因")
    void 非法粒度() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.passRate(PROJECT, "month", 12));

        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
        assertTrue(ex.getMessage().contains("day / week"));
    }

    private void addPoint(long projectId, String runId, String caseKey, Instant startedAt,
                          String verdict, long durationMs, String dut, String link) {
        trend.points.computeIfAbsent(projectId, key -> new LinkedHashMap<>())
                .computeIfAbsent(caseKey, key -> new ArrayList<>())
                .add(new TrendPoint(runId, startedAt, verdict, durationMs, TrendEnv.envKey(dut, link)));
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

    /** 内存仓储：按项目隔离，返回最近 limit 条并升序；通过率按 granularity 取对应桶。 */
    private static final class FakeTrendRepository implements TrendRepository {

        private final Map<Long, Map<String, List<TrendPoint>>> points = new HashMap<>();
        private final Map<String, RunSnapshot> runs = new HashMap<>();
        private List<PassRateCount> dayRows = List.of();
        private List<PassRateCount> weekRows = List.of();
        private List<PassRateCount> foreignDayRows = List.of();
        private String lastPassRateGranularity;
        private long lastProjectId;

        void addRun(long projectId, String runId, String dut, String link, Instant startedAt) {
            runs.put(projectId + "|" + runId, new RunSnapshot(projectId, runId, "passed", dut, link, startedAt));
        }

        @Override
        public Optional<RunSnapshot> findRun(long projectId, String runId) {
            lastProjectId = projectId;
            return Optional.ofNullable(runs.get(projectId + "|" + runId));
        }

        @Override
        public Optional<TrendPoint> findCasePoint(long projectId, String runId, String caseKey) {
            lastProjectId = projectId;
            return casePoints(projectId, caseKey).stream()
                    .filter(point -> point.runId().equals(runId))
                    .findFirst();
        }

        @Override
        public List<TrendPoint> listCasePoints(long projectId, String caseKey, int limit) {
            lastProjectId = projectId;
            List<TrendPoint> all = casePoints(projectId, caseKey).stream()
                    .sorted(Comparator.comparing(TrendPoint::startedAt))
                    .toList();
            int from = Math.max(0, all.size() - limit);
            return new ArrayList<>(all.subList(from, all.size()));
        }

        @Override
        public List<Long> listCaseDurations(long projectId, String caseKey, String dut, String link,
                                            String excludeRunId, int limit) {
            lastProjectId = projectId;
            String envKey = TrendEnv.envKey(dut, link);
            return casePoints(projectId, caseKey).stream()
                    .filter(point -> !point.runId().equals(excludeRunId))
                    .filter(point -> Objects.equals(point.envKey(), envKey))
                    .sorted(Comparator.comparing(TrendPoint::startedAt).reversed())
                    .limit(limit)
                    .map(TrendPoint::durationMs)
                    .toList();
        }

        @Override
        public List<PassRateCount> listPassRate(long projectId, String granularity, int limit) {
            lastProjectId = projectId;
            lastPassRateGranularity = granularity;
            if (projectId != PROJECT) {
                return new ArrayList<>(foreignDayRows);
            }
            return new ArrayList<>(("week".equals(granularity) ? weekRows : dayRows));
        }

        private List<TrendPoint> casePoints(long projectId, String caseKey) {
            return points.getOrDefault(projectId, Map.of()).getOrDefault(caseKey, List.of());
        }
    }
}
