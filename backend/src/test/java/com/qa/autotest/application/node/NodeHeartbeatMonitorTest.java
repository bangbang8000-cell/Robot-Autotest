package com.qa.autotest.application.node;

import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.domain.node.ExecutionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线监视器（NODX-01）：判据是「连续 3 个心跳周期缺席」。
 *
 * <p>最容易写假的用例：阈值取「上次心跳距今 > 周期」（1 次缺席就打离线）——
 * 单次网络抖动会把健康节点打掉。这里的断言锁死 staleBefore = now − 3×周期。
 */
class NodeHeartbeatMonitorTest {

    private static final Instant NOW = Instant.parse("2026-11-06T14:00:00Z");

    @Test
    @DisplayName("sweep 的离线阈值 = now − 3×心跳周期（不是 1×）")
    void 阈值是三个周期() {
        FakeNodeRepository repo = new FakeNodeRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        NodeHeartbeatMonitor monitor = new NodeHeartbeatMonitor(repo, clock, 30, 3);

        monitor.sweep();

        assertEquals(1, repo.staleSweeps.size());
        assertEquals(NOW.minusSeconds(90), repo.staleSweeps.get(0),
                "阈值必须是 3 个完整心跳周期");
    }

    @Test
    @DisplayName("3 次缺心跳 → ONLINE 转 OFFLINE；心跳新鲜 → 保持 ONLINE；NEW 不动")
    void 三态分流() {
        FakeNodeRepository repo = new FakeNodeRepository();
        // staleBefore = NOW - 90s
        repo.seed(1L, ExecutionNode.ONLINE, NOW.minusSeconds(91));   // 缺 3+ 周期 → OFFLINE
        repo.seed(2L, ExecutionNode.ONLINE, NOW.minusSeconds(89));   // 缺 2 周期 → 保持
        repo.seed(3L, ExecutionNode.NEW, null);                      // 从没心跳过 → NEW 不动
        repo.seed(4L, ExecutionNode.DISABLED, NOW.minusSeconds(999));// 停用 → 不动
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        NodeHeartbeatMonitor monitor = new NodeHeartbeatMonitor(repo, clock, 30, 3);

        monitor.sweep();

        assertEquals(ExecutionNode.OFFLINE, repo.nodes.get(1L).status());
        assertEquals(ExecutionNode.ONLINE, repo.nodes.get(2L).status());
        assertEquals(ExecutionNode.NEW, repo.nodes.get(3L).status());
        assertEquals(ExecutionNode.DISABLED, repo.nodes.get(4L).status());
    }

    @Test
    @DisplayName("可配置：周期 10s × 3 次 = 30s 阈值（开发期验收用小周期）")
    void 参数可配() {
        FakeNodeRepository repo = new FakeNodeRepository();
        NodeHeartbeatMonitor monitor = new NodeHeartbeatMonitor(repo,
                Clock.fixed(NOW, ZoneOffset.UTC), 10, 3);
        monitor.sweep();
        assertEquals(NOW.minusSeconds(30), repo.staleSweeps.get(0));
        assertTrue(true);
    }

    /** 伪仓储：与真实 JDBC 同一套 markStaleOffline 语义（只动 ONLINE 且心跳超期的行）。 */
    static final class FakeNodeRepository implements ExecutionNodeRepository {

        final Map<Long, ExecutionNode> nodes = new HashMap<>();
        final List<Instant> staleSweeps = new ArrayList<>();
        final AtomicLong seq = new AtomicLong();

        void seed(long id, String status, Instant lastHeartbeat) {
            nodes.put(id, new ExecutionNode(id, "node-" + id, "hash-" + id, status,
                    true, false, 1, lastHeartbeat, Instant.EPOCH));
        }

        @Override
        public Optional<ExecutionNode> find(long id) {
            return Optional.ofNullable(nodes.get(id));
        }

        @Override
        public Optional<ExecutionNode> findByName(String name) {
            return nodes.values().stream().filter(node -> node.name().equals(name)).findFirst();
        }

        @Override
        public boolean nameExists(String name) {
            return findByName(name).isPresent();
        }

        @Override
        public List<ExecutionNode> list() {
            return new ArrayList<>(nodes.values());
        }

        @Override
        public ExecutionNode insert(String name, String tokenHash, boolean rfAvailable,
                                    boolean pytestAvailable, int maxConcurrency, Long operatorId) {
            long id = seq.incrementAndGet();
            ExecutionNode node = new ExecutionNode(id, name, tokenHash, ExecutionNode.NEW,
                    rfAvailable, pytestAvailable, maxConcurrency, null, Instant.EPOCH);
            nodes.put(id, node);
            return node;
        }

        @Override
        public void heartbeat(long id, boolean rfAvailable, boolean pytestAvailable) {
            ExecutionNode node = nodes.get(id);
            nodes.put(id, new ExecutionNode(node.id(), node.name(), node.tokenHash(),
                    ExecutionNode.ONLINE, rfAvailable, pytestAvailable, node.maxConcurrency(),
                    Instant.now(), node.registeredAt()));
        }

        @Override
        public int markStaleOffline(Instant staleBefore) {
            staleSweeps.add(staleBefore);
            int marked = 0;
            for (Map.Entry<Long, ExecutionNode> entry : nodes.entrySet()) {
                ExecutionNode node = entry.getValue();
                if (ExecutionNode.ONLINE.equals(node.status()) && node.lastHeartbeatAt() != null
                        && node.lastHeartbeatAt().isBefore(staleBefore)) {
                    entry.setValue(new ExecutionNode(node.id(), node.name(), node.tokenHash(),
                            ExecutionNode.OFFLINE, node.rfAvailable(), node.pytestAvailable(),
                            node.maxConcurrency(), node.lastHeartbeatAt(), node.registeredAt()));
                    marked++;
                }
            }
            return marked;
        }

        @Override
        public java.util.Map<Long, Integer> runningRunsByNode() {
            return java.util.Map.of();
        }

        @Override
        public void updateStatus(long id, String status, Long operatorId) {
            ExecutionNode node = nodes.get(id);
            nodes.put(id, new ExecutionNode(node.id(), node.name(), node.tokenHash(), status,
                    node.rfAvailable(), node.pytestAvailable(), node.maxConcurrency(),
                    node.lastHeartbeatAt(), node.registeredAt()));
        }
    }
}
