package com.qa.autotest.application.metrics;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.application.repository.ExecutionShardRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.domain.node.ExecutionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W4 指标口径：失败率分母、队列深度、节点在线率、分片恢复数，以及四类告警的触发边界。
 *
 * <p>边界按「≥ 阈值即告警」写死（失败率、队列）；在线率是「< 阈值」才告警——
 * 一个是越坏越告警，一个是越好才安静，写反了线上永远不响或永远响。
 */
class MetricsServiceTest {

    private static final long PROJECT = 29L;
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");

    private RunRepository runs;
    private ExecutionNodeRepository nodes;
    private ExecutionShardRepository shards;
    private AccessControlService access;
    private MetricsService service;

    @BeforeEach
    void setUp() {
        runs = mock(RunRepository.class);
        nodes = mock(ExecutionNodeRepository.class);
        shards = mock(ExecutionShardRepository.class);
        access = mock(AccessControlService.class);
        service = new MetricsService(runs, nodes, shards, access,
                Clock.fixed(NOW, ZoneOffset.UTC), 0.30, 5, 0.50);
    }

    private static ExecutionNode node(long id, String status) {
        return new ExecutionNode(id, "node-" + id, "hash", status, true, true, 4, NOW, NOW, null);
    }

    @Test
    @DisplayName("空库：所有读数为 0，在线率 0，不产生告警")
    void emptyProjectProducesZeroReadingsAndNoAlerts() {
        when(runs.countByStatus(PROJECT)).thenReturn(Map.of());
        when(nodes.list()).thenReturn(List.of());
        when(shards.countByState(PROJECT)).thenReturn(Map.of());
        when(shards.recoveredAttempts(PROJECT)).thenReturn(0);

        MetricsService.MetricsView view = service.snapshot(PROJECT);

        assertThat(view.runs().total()).isZero();
        assertThat(view.runs().failureRate()).isZero();
        assertThat(view.queue().depth()).isZero();
        assertThat(view.nodes().onlineRate()).isZero();
        assertThat(view.shards().total()).isZero();
        assertThat(view.alerts()).isEmpty();
        assertThat(view.generatedAt()).isEqualTo(NOW.toString());
        verify(access).bindProject(PROJECT);
    }

    @Test
    @DisplayName("失败率 ≥ 0.30 触发 WARN，分母只算 finished + failed")
    void failureRateAtThresholdRaisesAlert() {
        when(runs.countByStatus(PROJECT)).thenReturn(Map.of(
                "finished", 7, "failed", 3, "running", 2, "queued", 1));
        when(nodes.list()).thenReturn(List.of(node(11, ExecutionNode.ONLINE)));
        when(shards.countByState(PROJECT)).thenReturn(Map.of());
        when(shards.recoveredAttempts(PROJECT)).thenReturn(0);

        MetricsService.MetricsView view = service.snapshot(PROJECT);

        assertThat(view.runs().failureRate()).isEqualTo(0.3);
        assertThat(view.alerts()).anyMatch(alert -> alert.key().equals("failureRate"));
    }

    @Test
    @DisplayName("队列深度 = queued + preparing，达到阈值触发告警")
    void queueDepthRaisesAlert() {
        when(runs.countByStatus(PROJECT)).thenReturn(Map.of("queued", 3, "preparing", 3));
        when(nodes.list()).thenReturn(List.of(node(11, ExecutionNode.ONLINE)));
        when(shards.countByState(PROJECT)).thenReturn(Map.of());
        when(shards.recoveredAttempts(PROJECT)).thenReturn(0);

        MetricsService.MetricsView view = service.snapshot(PROJECT);

        assertThat(view.queue().depth()).isEqualTo(6);
        assertThat(view.alerts()).anyMatch(alert -> alert.key().equals("queueDepth"));
    }

    @Test
    @DisplayName("节点在线率 < 0.50 才告警，等于 0.50 保持安静")
    void onlineRateRaisesAlertOnlyBelowThreshold() {
        when(runs.countByStatus(PROJECT)).thenReturn(Map.of("finished", 10));
        when(shards.countByState(PROJECT)).thenReturn(Map.of());
        when(shards.recoveredAttempts(PROJECT)).thenReturn(0);

        when(nodes.list()).thenReturn(List.of(
                node(11, ExecutionNode.ONLINE), node(12, ExecutionNode.OFFLINE),
                node(13, ExecutionNode.DISABLED)));
        MetricsService.MetricsView low = service.snapshot(PROJECT);
        assertThat(low.nodes().onlineRate()).isEqualTo(0.3333);
        assertThat(low.alerts()).anyMatch(alert -> alert.key().equals("onlineRate"));

        when(nodes.list()).thenReturn(List.of(
                node(11, ExecutionNode.ONLINE), node(12, ExecutionNode.OFFLINE)));
        MetricsService.MetricsView half = service.snapshot(PROJECT);
        assertThat(half.nodes().onlineRate()).isEqualTo(0.5);
        assertThat(half.alerts()).noneMatch(alert -> alert.key().equals("onlineRate"));
    }

    @Test
    @DisplayName("存在 LOST 分片即告警，并带出重派多花的尝试次数")
    void lostShardsRaiseAlert() {
        when(runs.countByStatus(PROJECT)).thenReturn(Map.of("finished", 10));
        when(nodes.list()).thenReturn(List.of(node(11, ExecutionNode.ONLINE)));
        when(shards.countByState(PROJECT)).thenReturn(Map.of("LEASED", 1, "LOST", 2, "DONE", 5));
        when(shards.recoveredAttempts(PROJECT)).thenReturn(3);

        MetricsService.MetricsView view = service.snapshot(PROJECT);

        assertThat(view.shards().lost()).isEqualTo(2);
        assertThat(view.shards().recoveredAttempts()).isEqualTo(3);
        assertThat(view.alerts()).anyMatch(alert -> alert.key().equals("lostShards"));
    }

    @Test
    @DisplayName("健康状态：失败率 0、无排队、节点全在线、无 LOST → 零告警")
    void healthyProjectHasNoAlerts() {
        when(runs.countByStatus(PROJECT)).thenReturn(Map.of("finished", 12));
        when(nodes.list()).thenReturn(List.of(node(11, ExecutionNode.ONLINE),
                node(12, ExecutionNode.ONLINE)));
        when(shards.countByState(PROJECT)).thenReturn(Map.of("DONE", 8));
        when(shards.recoveredAttempts(PROJECT)).thenReturn(0);

        MetricsService.MetricsView view = service.snapshot(PROJECT);

        assertThat(view.alerts()).isEmpty();
        assertThat(view.nodes().onlineRate()).isEqualTo(1.0);
        assertThat(view.shards().total()).isEqualTo(8);
    }
}
