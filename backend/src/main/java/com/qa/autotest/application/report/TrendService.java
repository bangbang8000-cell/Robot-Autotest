package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.TrendRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.PassRateCount;
import com.qa.autotest.domain.report.RunSnapshot;
import com.qa.autotest.domain.report.TrendEnv;
import com.qa.autotest.domain.report.TrendPoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 趋势与性能基线（RPT-04 / FR-v0.5-17~20）。
 *
 * <p>三条硬规则：
 * <ol>
 *   <li>趋势点只取同项目 + 同 case_key；换环境（DUT+LINK 不同）时必须断开，按 envKey 分组画线。</li>
 *   <li>性能基线 = 同环境 + 同用例的历史耗时<b>中位数</b>；本轮耗时 > 基线 1.5 倍才算偏离。</li>
 *   <li>通过率按天 / 周聚合，只统计已完成的 run（run_status='finished'）。</li>
 * </ol>
 */
@Service
public class TrendService {

    /** 趋势接口 limit 的默认值与上限。 */
    static final int DEFAULT_LIMIT = 5;

    /** 通过率接口 limit 的默认值与上限。 */
    static final int DEFAULT_PASS_RATE_LIMIT = 12;
    static final int MAX_PASS_RATE_LIMIT = 52;

    /** 基线取多少个同环境历史点的耗时（限定上界，避免超大项目拖慢查询）。 */
    static final int BASELINE_HISTORY_LIMIT = 50;

    private static final Comparator<TrendPoint> BY_TIME =
            Comparator.comparing(TrendPoint::startedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    /** 单条趋势点；deviated 只可能为 true 于当前运行。 */
    public record TrendPointView(String runId,
                                 Instant startedAt,
                                 String verdict,
                                 long durationMs,
                                 String envKey,
                                 boolean deviated) {
    }

    /** 同一环境的折线；前端按环境分线，禁止跨环境连成一条。 */
    public record TrendSeries(String envKey, List<TrendPointView> points) {
    }

    /** 用例趋势结果；baselineMs 为 null 表示没有同环境历史。 */
    public record CaseTrendResult(String caseKey,
                                  String currentEnvKey,
                                  Long baselineMs,
                                  boolean deviated,
                                  List<TrendPointView> points,
                                  List<TrendSeries> series) {
    }

    /** 通过率时间桶；passRate 为 0~1 的小数（4 位精度）。 */
    public record PassRateItem(String bucket, int pass, int fail, int skip, int total, double passRate) {
    }

    public record PassRateResult(String granularity, List<PassRateItem> buckets) {
    }

    private final TrendRepository trend;
    private final AccessControlService access;

    public TrendService(TrendRepository trend, AccessControlService access) {
        this.trend = trend;
        this.access = access;
    }

    public CaseTrendResult caseTrend(long projectId, String runId, String caseKey, int limit) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        if (caseKey == null || caseKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "caseKey 不能为空");
        }
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        if (effectiveLimit > 50) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "limit 不能超过 50");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        RunSnapshot run = trend.findRun(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: " + runId));
        String currentEnv = TrendEnv.envKey(run.dut(), run.link());

        List<TrendPoint> points = new ArrayList<>(trend.listCasePoints(projectId, caseKey, effectiveLimit));
        points.sort(BY_TIME);
        if (points.size() > effectiveLimit) {
            points = new ArrayList<>(points.subList(points.size() - effectiveLimit, points.size()));
        }

        Optional<TrendPoint> currentPoint = trend.findCasePoint(projectId, runId, caseKey);
        // 基线独立查询：同环境 + 同用例、排除本轮，不受趋势 limit 影响。
        List<Long> historyDurations = trend.listCaseDurations(projectId, caseKey,
                run.dut(), run.link(), runId, BASELINE_HISTORY_LIMIT);
        Long baselineMs = median(historyDurations);

        boolean deviated = currentPoint.isPresent() && baselineMs != null
                && exceedsBaseline(currentPoint.get().durationMs(), baselineMs);

        List<TrendPointView> views = new ArrayList<>(points.size());
        for (TrendPoint point : points) {
            boolean pointDeviated = deviated && point.runId().equals(runId)
                    && Objects.equals(point.envKey(), currentEnv);
            views.add(new TrendPointView(point.runId(), point.startedAt(), point.verdict(),
                    point.durationMs(), point.envKey(), pointDeviated));
        }

        return new CaseTrendResult(caseKey, currentEnv, baselineMs, deviated, views, groupByEnv(views));
    }

    public PassRateResult passRate(long projectId, String granularity, int limit) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (granularity == null || !(granularity.equalsIgnoreCase("day") || granularity.equalsIgnoreCase("week"))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "granularity 只支持 day / week");
        }
        String normalized = granularity.toLowerCase(java.util.Locale.ROOT);
        int effectiveLimit = limit <= 0 ? DEFAULT_PASS_RATE_LIMIT : limit;
        if (effectiveLimit > MAX_PASS_RATE_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "limit 不能超过 " + MAX_PASS_RATE_LIMIT);
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        List<PassRateItem> buckets = new ArrayList<>();
        for (PassRateCount row : trend.listPassRate(projectId, normalized, effectiveLimit)) {
            int total = row.pass() + row.fail() + row.skip();
            double rate = total == 0 ? 0.0 : Math.round((double) row.pass() / total * 10_000) / 10_000.0;
            buckets.add(new PassRateItem(row.bucket(), row.pass(), row.fail(), row.skip(), total, rate));
        }
        return new PassRateResult(normalized, buckets);
    }

    /** 中位数：奇数取中间值，偶数取中间两个的平均值（整数毫秒）。 */
    static Long median(List<Long> durations) {
        if (durations == null || durations.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(durations);
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        long low = sorted.get(size / 2 - 1);
        long high = sorted.get(size / 2);
        return (low + high) / 2;
    }

    /** 边界口径：严格大于 1.5 倍才算偏离，等于不算。用整数比较避免浮点误差。 */
    static boolean exceedsBaseline(long durationMs, long baselineMs) {
        return durationMs * 2 > baselineMs * 3;
    }

    private static List<TrendSeries> groupByEnv(List<TrendPointView> views) {
        Map<String, List<TrendPointView>> grouped = new LinkedHashMap<>();
        for (TrendPointView view : views) {
            grouped.computeIfAbsent(view.envKey(), key -> new ArrayList<>()).add(view);
        }
        List<TrendSeries> series = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<TrendPointView>> entry : grouped.entrySet()) {
            series.add(new TrendSeries(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return series;
    }
}
