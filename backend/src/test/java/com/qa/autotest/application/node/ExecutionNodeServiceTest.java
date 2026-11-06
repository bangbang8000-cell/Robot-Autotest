package com.qa.autotest.application.node;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.node.ExecutionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点注册与心跳（NODX-01）。
 *
 * <p>不连库、不启 Spring。三条最容易写假的用例：
 * ① 令牌哈希入库但**审计/日志里留了明文**（持久面泄漏，比不加密更糟）；
 * ② 错令牌返回 404 而不是 401（把「节点是否存在」泄露给扫描器）；
 * ③ DISABLED 节点靠心跳「复活」（停用必须真的停）。
 */
class ExecutionNodeServiceTest {

    private FakeNodeRepository repo;
    private FakeAudit audit;
    private ExecutionNodeService service;

    @BeforeEach
    void setUp() {
        repo = new FakeNodeRepository();
        audit = new FakeAudit();
        service = new ExecutionNodeService(repo, new AdminAccess(), audit, 30);
    }

    @Test
    @DisplayName("注册：明文令牌只在响应里出现一次，库里只有 SHA-256 哈希")
    void 注册只存哈希() {
        ExecutionNodeService.RegisterResult result = service.register("node-a", true, false, 2);
        assertNotNull(result.token());
        assertEquals(64, result.token().length());

        ExecutionNode stored = repo.nodes.get(result.nodeId());
        assertNotEquals(result.token(), stored.tokenHash(), "明文不得入库");
        assertEquals(ExecutionNodeService.sha256(result.token()), stored.tokenHash());
        assertEquals(ExecutionNode.NEW, stored.status());

        // 审计摘要里也不得出现明文令牌（日志是持久面）
        for (String detail : audit.details) {
            assertFalse(detail.contains(result.token()), "审计不得记录明文令牌: " + detail);
        }
    }

    @Test
    @DisplayName("重名注册 → 409；并发上限越界 → 400")
    void 注册守门() {
        service.register("node-a", true, false, 1);
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class,
                        () -> service.register("node-a", true, true, 1)).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(BusinessException.class,
                        () -> service.register("node-b", true, true, 0)).errorCode());
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(BusinessException.class,
                        () -> service.register("node-b", true, true, 65)).errorCode());
    }

    @Test
    @DisplayName("心跳：正确令牌 → ONLINE + 能力更新 + last_heartbeat_at 非空")
    void 心跳置在线() {
        ExecutionNodeService.RegisterResult result = service.register("node-a", false, false, 1);
        ExecutionNodeService.NodeView view =
                service.heartbeat(result.nodeId(), result.token(), true, true);
        assertEquals(ExecutionNode.ONLINE, view.status());
        assertTrue(view.rfAvailable());
        assertTrue(view.pytestAvailable());
        assertNotNull(view.lastHeartbeatAt());
    }

    @Test
    @DisplayName("心跳：错令牌 / 缺令牌 / 不存在的节点 → 一律 401（防枚举）")
    void 心跳防枚举() {
        ExecutionNodeService.RegisterResult result = service.register("node-a", true, false, 1);
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BusinessException.class,
                        () -> service.heartbeat(result.nodeId(), "wrong-token", true, true))
                        .errorCode());
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BusinessException.class,
                        () -> service.heartbeat(result.nodeId(), null, true, true)).errorCode());
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BusinessException.class,
                        () -> service.heartbeat(99999, "whatever", true, true)).errorCode());
    }

    @Test
    @DisplayName("DISABLED 节点心跳 → 403（停用不能靠心跳复活）")
    void 停用拒绝心跳() {
        ExecutionNodeService.RegisterResult result = service.register("node-a", true, false, 1);
        service.heartbeat(result.nodeId(), result.token(), true, false);
        service.disable(result.nodeId());
        assertEquals(ExecutionNode.DISABLED, repo.nodes.get(result.nodeId()).status());
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class,
                        () -> service.heartbeat(result.nodeId(), result.token(), true, true))
                        .errorCode());
        // enable 后回 NEW（等下一次心跳再上线），而不是直接 ONLINE
        service.enable(result.nodeId());
        assertEquals(ExecutionNode.NEW, repo.nodes.get(result.nodeId()).status());
    }

    @Test
    @DisplayName("非管理员：register / list / disable 全 403")
    void 非管理员拒绝() {
        ExecutionNodeService stranger = new ExecutionNodeService(repo, new NobodyAccess(),
                audit, 30);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class,
                        () -> stranger.register("node-x", true, false, 1)).errorCode());
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, stranger::list).errorCode());
    }

    @Test
    @DisplayName("列表：能力与并发上限可见，令牌哈希不外泄")
    void 列表不泄露哈希() {
        ExecutionNodeService.RegisterResult result = service.register("node-a", true, false, 3);
        service.heartbeat(result.nodeId(), result.token(), true, false);
        List<ExecutionNodeService.NodeView> views = service.list();
        assertEquals(1, views.size());
        ExecutionNodeService.NodeView view = views.get(0);
        assertEquals("node-a", view.name());
        assertTrue(view.rfAvailable());
        assertFalse(view.pytestAvailable());
        assertEquals(3, view.maxConcurrency());
        assertFalse(view.toString().contains(result.token()));
        assertFalse(view.toString().contains(repo.nodes.get(result.nodeId()).tokenHash()));
    }

    // ---------- 桩 ----------

    static final class FakeNodeRepository implements ExecutionNodeRepository {

        final Map<Long, ExecutionNode> nodes = new HashMap<>();
        final List<Instant> staleSweeps = new ArrayList<>();
        final AtomicLong seq = new AtomicLong();

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
            return nodes.values().stream()
                    .sorted(java.util.Comparator.comparingLong(ExecutionNode::id)).toList();
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
        public void updateStatus(long id, String status, Long operatorId) {
            ExecutionNode node = nodes.get(id);
            nodes.put(id, new ExecutionNode(node.id(), node.name(), node.tokenHash(), status,
                    node.rfAvailable(), node.pytestAvailable(), node.maxConcurrency(),
                    node.lastHeartbeatAt(), node.registeredAt()));
        }
    }

    static final class AdminAccess extends AccessControlService {

        AdminAccess() {
            super(null, null);
        }

        @Override
        public boolean isAdmin() {
            return true;
        }
    }

    static final class NobodyAccess extends AccessControlService {

        NobodyAccess() {
            super(null, null);
        }

        @Override
        public boolean isAdmin() {
            return false;
        }
    }

    static final class FakeAudit extends AuditService {

        final List<String> actions = new ArrayList<>();
        final List<String> details = new ArrayList<>();

        FakeAudit() {
            super(null);
        }

        @Override
        public void record(String action, String resource, String resourceId, String detail) {
            actions.add(action);
            details.add(detail == null ? "" : detail);
        }
    }
}
