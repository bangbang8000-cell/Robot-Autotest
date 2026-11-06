package com.qa.autotest.application.node;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.node.ExecutionNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行节点注册与心跳（NODX-01，FR-v2.1-13/14）。
 *
 * <p>三条硬口径：
 * <ol>
 *   <li><b>令牌只存哈希</b>：注册响应里明文出现**一次**，库里只有 SHA-256——
 *       节点令牌等价于执行凭据，泄库不泄凭据。令牌是 32 字节随机数（hex 64 字符），
 *       高熵所以不需要慢哈希（不是用户口令，不适用 bcrypt 那套）。</li>
 *   <li><b>心跳携带令牌</b>：令牌放 {@code X-Node-Token} 头，服务端比哈希；
 *       错令牌 401，且错误文案不区分「节点不存在 / 令牌不对」（防枚举）。</li>
 *   <li><b>状态四态</b>（见 {@link ExecutionNode} 类注释）；本服务只负责
 *       NEW/DISABLED 与心跳置 ONLINE，OFFLINE 由监视器置位。</li>
 * </ol>
 */
@Service
public class ExecutionNodeService {

    /** 心跳周期（秒）；离线阈值 = 周期 × missedLimit（默认 3 次）。 */
    private final int heartbeatIntervalSeconds;

    private final ExecutionNodeRepository nodes;
    private final AccessControlService access;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public ExecutionNodeService(ExecutionNodeRepository nodes, AccessControlService access,
                                AuditService audit,
                                @Value("${node.heartbeat-interval-seconds:30}")
                                int heartbeatIntervalSeconds) {
        this.nodes = nodes;
        this.access = access;
        this.audit = audit;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int heartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    /** 节点列表读数（能力 + 并发上限 + 最近心跳 + 在跑占用）。不返回令牌哈希。 */
    public record NodeView(long id, String name, String status,
                           boolean rfAvailable, boolean pytestAvailable,
                           int maxConcurrency, String lastHeartbeatAt,
                           int runningRuns) {
    }

    /** 注册响应：明文令牌只在这里出现一次。 */
    public record RegisterResult(long nodeId, String name, String token) {
    }

    @Transactional
    public RegisterResult register(String name, boolean rfAvailable, boolean pytestAvailable,
                                   int maxConcurrency, String agentBaseUrl) {
        requireAdmin();
        if (name == null || name.isBlank() || name.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "节点名必填且 ≤ 64 字符");
        }
        if (nodes.nameExists(name.trim())) {
            throw new BusinessException(ErrorCode.CONFLICT, "节点名已存在: " + name.trim());
        }
        if (maxConcurrency < 1 || maxConcurrency > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "并发上限必须是 1..64: " + maxConcurrency);
        }
        String agentUrl = normalizeAgentBaseUrl(agentBaseUrl);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        ExecutionNode node = nodes.insert(name.trim(), sha256(token),
                rfAvailable, pytestAvailable, maxConcurrency, null, agentUrl);
        // 审计只记名字与能力，**绝不记明文令牌**（摘要里也不行——日志是持久面）
        audit.record("NODE_REGISTER", "execution_node", String.valueOf(node.id()),
                "name=" + node.name() + "; rf=" + rfAvailable + "; pytest=" + pytestAvailable
                        + "; maxConcurrency=" + maxConcurrency);
        return new RegisterResult(node.id(), node.name(), token);
    }

    /**
     * 心跳：验令牌（哈希比对）→ 更新能力与 last_heartbeat_at → 置 ONLINE。
     * DISABLED 节点拒绝心跳（403）——停用必须真的停，不能靠心跳「复活」。
     */
    @Transactional
    public NodeView heartbeat(long nodeId, String token, boolean rfAvailable,
                              boolean pytestAvailable) {
        ExecutionNode node = requireNodeWithToken(nodeId, token);
        if (ExecutionNode.DISABLED.equals(node.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "节点已停用，先由管理员启用");
        }
        nodes.heartbeat(nodeId, rfAvailable, pytestAvailable);
        return view(nodes.find(nodeId).orElse(node));
    }

    public List<NodeView> list() {
        requireAdmin();
        java.util.Map<Long, Integer> running = nodes.runningRunsByNode();
        return nodes.list().stream()
                .map(node -> view(node, running.getOrDefault(node.id(), 0)))
                .toList();
    }

    @Transactional
    public NodeView disable(long nodeId) {
        return setStatus(nodeId, ExecutionNode.DISABLED, "NODE_DISABLE");
    }

    @Transactional
    public NodeView enable(long nodeId) {
        return setStatus(nodeId, ExecutionNode.NEW, "NODE_ENABLE");
    }

    private NodeView setStatus(long nodeId, String status, String auditAction) {
        requireAdmin();
        ExecutionNode node = nodes.find(nodeId).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "节点不存在: " + nodeId));
        nodes.updateStatus(nodeId, status, null);
        audit.record(auditAction, "execution_node", String.valueOf(nodeId),
                "name=" + node.name() + "; " + node.status() + " -> " + status);
        return view(nodes.find(nodeId).orElse(node));
    }

    /** 心跳鉴权：不存在与令牌错统一 401，防枚举。 */
    private ExecutionNode requireNodeWithToken(long nodeId, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "缺少节点令牌");
        }
        ExecutionNode node = nodes.find(nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "节点令牌无效"));
        if (!sha256(token).equals(node.tokenHash())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "节点令牌无效");
        }
        return node;
    }

    /** agent 接入地址校验：可空（未部署 agent），有值必须是 http(s) URL 且 ≤200 字符。 */
    private String normalizeAgentBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String url = raw.trim();
        if (url.length() > 200 || !url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "agent 接入地址必须是 http(s):// 且 ≤ 200 字符");
        }
        return url;
    }

    private void requireAdmin() {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "节点管理仅管理员");
        }
    }

    private NodeView view(ExecutionNode node) {
        return view(node, 0);
    }

    private NodeView view(ExecutionNode node, int runningRuns) {
        return new NodeView(node.id(), node.name(), node.status(),
                node.rfAvailable(), node.pytestAvailable(), node.maxConcurrency(),
                node.lastHeartbeatAt() == null ? null : node.lastHeartbeatAt().toString(),
                runningRuns);
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
