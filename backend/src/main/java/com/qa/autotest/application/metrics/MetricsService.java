package com.qa.autotest.application.metrics;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.application.repository.ExecutionShardRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.domain.node.ExecutionNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容量与可观测指标（W4）：把 run 队列、节点在线率、分片恢复与失败率汇总成一组读数，
 * 并按阈值给出告警。**只读**，不写库，不改变任何运行状态。
 *
 * <p>口径：
 * <ul>
 *   <li>{@code failureRate = failed / (finished + failed)}，没有终态 run 时记 0；</li>
 *   <li>{@code queueDepth = queued + preparing}（排队中 + 正在准备，尚未真正跑用例）；</li>
 *   <li>{@code onlineRate = ONLINE 节点数 / 注册节点总数}（DISABLED 计入分母，代表「可用率」）；</li>
 *   <li>{@code recoveredAttempts = Σ(attempt - 1)}，即重派多花的尝试次数，用来观察故障转移频率。</li>
 * </ul>
 *
 * <p>阈值全部可配，缺省值按「单机自用」场景给，见 {@code application.yml}。
 */
@Service
public class MetricsService {

    private final RunRepository runs;
    private final ExecutionNodeRepository nodes;
    private final ExecutionShardRepository shards;
    private final AccessControlService access;
    private final Clock clock;
    private final double failureRateWarn;
    private final int queueDepthWarn;
    private final double onlineRateWarn;

    public MetricsService(RunRepository runs,
                          ExecutionNodeRepository nodes,
                          ExecutionShardRepository shards,
                          AccessControlService access,
                          Clock clock,
                          @Value("${metrics.failure-rate-warn:0.30}") double failureRateWarn,
                          @Value("${metrics.queue-depth-warn:5}") int queueDepthWarn,
                          @Value("${metrics.online-rate-warn:0.50}") double onlineRateWarn) {
        this.runs = runs;
        this.nodes = nodes;
        this.shards = shards;
        this.access = access;
        this.clock = clock;
        this.failureRateWarn = failureRateWarn;
        this.queueDepthWarn = queueDepthWarn;
        this.onlineRateWarn = onlineRateWarn;
    }

    public record RunMetrics(int total, int finished, int failed, int running, double failureRate) {
    }

    public record QueueMetrics(int queued, int preparing, int depth) {
    }

    public record NodeMetrics(int total, int online, int offline, int disabled, double onlineRate) {
    }

    public record ShardMetrics(int total, int leased, int lost, int recoveredAttempts) {
    }

    /** 单条告警：{@code level} 目前只有 WARN（自用场景不做分级降噪）。 */
    public record AlertView(String key, String level, double value, double threshold, String message) {
    }

    public record MetricsView(long projectId,
                              String generatedAt,
                              RunMetrics runs,
                              QueueMetrics queue,
                              NodeMetrics nodes,
                              ShardMetrics shards,
                              List<AlertView> alerts) {
    }

    public MetricsView snapshot(long projectId) {
        access.bindProject(projectId);

        Map<String, Integer> runCounts = new LinkedHashMap<>(runs.countByStatus(projectId));
        int finished = runCounts.getOrDefault("finished", 0);
        int failed = runCounts.getOrDefault("failed", 0);
        int running = runCounts.getOrDefault("running", 0);
        int queued = runCounts.getOrDefault("queued", 0);
        int preparing = runCounts.getOrDefault("preparing", 0);
        int totalRuns = runCounts.values().stream().mapToInt(Integer::intValue).sum();
        double failureRate = finished + failed == 0 ? 0.0 : (double) failed / (finished + failed);

        List<ExecutionNode> nodeList = nodes.list();
        int online = (int) nodeList.stream().filter(n -> ExecutionNode.ONLINE.equals(n.status())).count();
        int offline = (int) nodeList.stream().filter(n -> ExecutionNode.OFFLINE.equals(n.status())).count();
        int disabled = (int) nodeList.stream().filter(n -> ExecutionNode.DISABLED.equals(n.status())).count();
        double onlineRate = nodeList.isEmpty() ? 0.0 : (double) online / nodeList.size();

        Map<String, Integer> shardCounts = new LinkedHashMap<>(shards.countByState(projectId));
        int shardTotal = shardCounts.values().stream().mapToInt(Integer::intValue).sum();
        int leased = shardCounts.getOrDefault("LEASED", 0);
        int lost = shardCounts.getOrDefault("LOST", 0);
        int recovered = shards.recoveredAttempts(projectId);

        List<AlertView> alerts = new ArrayList<>();
        if (failureRate >= failureRateWarn) {
            alerts.add(new AlertView("failureRate", "WARN", round(failureRate), failureRateWarn,
                    "失败率 " + percent(failureRate) + " 已到阈值 " + percent(failureRateWarn)));
        }
        if (queued + preparing >= queueDepthWarn) {
            alerts.add(new AlertView("queueDepth", "WARN", queued + preparing, queueDepthWarn,
                    "队列深度 " + (queued + preparing) + " 已到阈值 " + queueDepthWarn));
        }
        if (!nodeList.isEmpty() && onlineRate < onlineRateWarn) {
            alerts.add(new AlertView("onlineRate", "WARN", round(onlineRate), onlineRateWarn,
                    "节点在线率 " + percent(onlineRate) + " 低于阈值 " + percent(onlineRateWarn)));
        }
        if (lost > 0) {
            alerts.add(new AlertView("lostShards", "WARN", lost, 1,
                    "存在 " + lost + " 个待重派/已失败分片"));
        }

        return new MetricsView(projectId, Instant.now(clock).toString(),
                new RunMetrics(totalRuns, finished, failed, running, round(failureRate)),
                new QueueMetrics(queued, preparing, queued + preparing),
                new NodeMetrics(nodeList.size(), online, offline, disabled, round(onlineRate)),
                new ShardMetrics(shardTotal, leased, lost, recovered),
                List.copyOf(alerts));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100);
    }
}
