package com.qa.autotest.application.report;

import com.qa.autotest.domain.report.RetentionCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 保留策略候选集计算（RPT-06），纯函数、不碰文件与数据库。
 *
 * <p>顺序（DEV-PLAN §6）：
 * <ol>
 *   <li>按开始时间倒序，最近 {@code keep_recent_runs} 次永久保护；</li>
 *   <li>再扣除三类保护（有导出记录 / pinned / p0 证据）；</li>
 *   <li>超过 {@code max_days} 的运行入选，原因 {@code expired}；</li>
 *   <li>若扣除后项目总占用仍超 {@code max_project_gb}，从最旧的剩余运行继续补足，原因 {@code quota}。</li>
 * </ol>
 */
public final class RetentionPlanner {

    /** 1 GB = 1024^3 字节（与运维口径一致）。 */
    public static final long BYTES_PER_GB = 1024L * 1024 * 1024;

    static final String REASON_EXPIRED = "expired";
    static final String REASON_QUOTA = "quota";

    private RetentionPlanner() {
    }

    /**
     * 待清理项。
     *
     * @param runDbId run.id
     * @param runId   RUN_ID
     * @param bytes   产物占用
     * @param reason  expired（超龄）/ quota（项目超限）
     */
    public record Item(long runDbId, String runId, long bytes, String reason) {
    }

    /** 规划结果；items 按 run.id 升序，保证每次 dry-run 清单稳定。 */
    public record Plan(List<Item> items, long totalBytes) {
    }

    public static Plan plan(List<RetentionCandidate> candidates,
                            long projectBytes,
                            Instant now,
                            int maxDays,
                            long maxProjectGb,
                            int keepRecentRuns) {
        List<RetentionCandidate> ordered = new ArrayList<>(candidates == null ? List.of() : candidates);
        ordered.sort(newestFirst());

        Set<Long> recentIds = ordered.stream()
                .limit(Math.max(0, keepRecentRuns))
                .map(RetentionCandidate::runDbId)
                .collect(Collectors.toSet());

        Map<Long, Item> selected = new LinkedHashMap<>();
        Instant cutoff = now.minus(Duration.ofDays(Math.max(0, maxDays)));

        // 第一轮：超龄（且未被最近 N 次与三类保护排除）。
        for (RetentionCandidate candidate : ordered) {
            if (!eligible(candidate, recentIds)) {
                continue;
            }
            if (candidate.startedAt() != null && candidate.startedAt().isBefore(cutoff)) {
                selected.put(candidate.runDbId(),
                        new Item(candidate.runDbId(), candidate.runId(), candidate.bytes(), REASON_EXPIRED));
            }
        }

        // 第二轮：项目总占用仍超上限时，从最旧的剩余运行继续清理。
        long remaining = projectBytes - sumBytes(selected);
        long limitBytes = Math.max(0, maxProjectGb) * BYTES_PER_GB;
        if (remaining > limitBytes) {
            List<RetentionCandidate> oldestFirst = new ArrayList<>(ordered);
            oldestFirst.sort(newestFirst().reversed());
            for (RetentionCandidate candidate : oldestFirst) {
                if (remaining <= limitBytes) {
                    break;
                }
                if (!eligible(candidate, recentIds) || selected.containsKey(candidate.runDbId())) {
                    continue;
                }
                selected.put(candidate.runDbId(),
                        new Item(candidate.runDbId(), candidate.runId(), candidate.bytes(), REASON_QUOTA));
                remaining -= candidate.bytes();
            }
        }

        List<Item> items = new ArrayList<>(selected.values());
        items.sort(Comparator.comparingLong(Item::runDbId));
        return new Plan(items, sumBytes(selected));
    }

    private static boolean eligible(RetentionCandidate candidate, Set<Long> recentIds) {
        return !recentIds.contains(candidate.runDbId())
                && !candidate.protectedRun()
                && candidate.hasArtifacts();
    }

    private static long sumBytes(Map<Long, Item> items) {
        long total = 0;
        for (Item item : items.values()) {
            total += item.bytes();
        }
        return total;
    }

    /** 开始时间倒序（最新在前）；null 视为最旧；同一时间按 run.id 倒序，保证结果稳定。 */
    private static Comparator<RetentionCandidate> newestFirst() {
        return Comparator.comparing(RetentionCandidate::startedAt,
                        Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                .thenComparing(Comparator.comparingLong(RetentionCandidate::runDbId).reversed());
    }
}
