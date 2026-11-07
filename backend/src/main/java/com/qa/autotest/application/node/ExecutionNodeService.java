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
import org.springframework.beans.factory.annotation.Autowired;
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

    /** 心跳写库限流窗口（秒）：同一节点窗口内的重复心跳只回应、不重复写库（N10）。 */
    private final int heartbeatThrottleSeconds;

    private final java.time.Clock clock;

    private final ExecutionNodeRepository nodes;
    private final AccessControlService access;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    /**
     * 生产构造器必须显式标注：类里另有 3 个测试用的包级构造器，
     * 有多个候选时 Spring 不再自动挑唯一构造器，会去找无参构造并报
     * {@code No default constructor found}（本期门禁 05 实测踩到）。
     */
    @Autowired
    public ExecutionNodeService(ExecutionNodeRepository nodes, AccessControlService access,
                                AuditService audit,
                                @Value("${node.heartbeat-interval-seconds:30}")
                                int heartbeatIntervalSeconds,
                                @Value("${node.heartbeat-throttle-seconds:5}")
                                int heartbeatThrottleSeconds) {
        this(nodes, access, audit, heartbeatIntervalSeconds, heartbeatThrottleSeconds,
                java.time.Clock.systemUTC());
    }

    /** 单测入口：默认 5 秒限流窗口。 */
    ExecutionNodeService(ExecutionNodeRepository nodes, AccessControlService access,
                         AuditService audit, int heartbeatIntervalSeconds) {
        this(nodes, access, audit, heartbeatIntervalSeconds, 5, java.time.Clock.systemUTC());
    }

    ExecutionNodeService(ExecutionNodeRepository nodes, AccessControlService access,
                         AuditService audit, int heartbeatIntervalSeconds,
                         int heartbeatThrottleSeconds, java.time.Clock clock) {
        this.nodes = nodes;
        this.access = access;
        this.audit = audit;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.heartbeatThrottleSeconds = Math.max(0, heartbeatThrottleSeconds);
        this.clock = clock;
    }

    /** 心跳限流窗口（秒），0 表示关闭限流。 */
    public int heartbeatThrottleSeconds() {
        return heartbeatThrottleSeconds;
    }

    public int heartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    /** 节点最近一条 run 摘要（NODX-04「最近任务」列）。 */
    public record LastRunView(String runId, String state, String finishedAt) {
    }

    /** 节点列表读数（能力 + 并发上限 + 最近心跳 + 在跑占用 + 最近任务）。不返回令牌哈希。 */
    public record NodeView(long id, String name, String status,
                           boolean rfAvailable, boolean pytestAvailable,
                           int maxConcurrency, String lastHeartbeatAt,
                           int runningRuns, String agentBaseUrl,
                           LastRunView lastRun) {
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
        // N10：同一节点在限流窗口内的重复心跳只回应、不再写库。
        // 高速轮询（毫秒级探活）会打满执行节点表的写竞争，而心跳的语义本来就是「我还活着」，
        // 5 秒内的第二条既不改变存活判定，也不该带来一次 UPDATE。
        if (throttled(node)) {
            return view(node);
        }
        nodes.heartbeat(nodeId, rfAvailable, pytestAvailable);
        return view(nodes.find(nodeId).orElse(node));
    }

    private boolean throttled(ExecutionNode node) {
        if (heartbeatThrottleSeconds <= 0 || node.lastHeartbeatAt() == null) {
            return false;
        }
        return !clock.instant().isAfter(node.lastHeartbeatAt()
                .plusSeconds(heartbeatThrottleSeconds));
    }

    public List<NodeView> list() {
        requireAdmin();
        java.util.Map<Long, Integer> running = nodes.runningRunsByNode();
        java.util.Map<Long, ExecutionNodeRepository.NodeLastRun> latest = nodes.latestRunsByNode();
        return nodes.list().stream()
                .map(node -> {
                    ExecutionNodeRepository.NodeLastRun lr = latest.get(node.id());
                    LastRunView lastRun = lr == null ? null
                            : new LastRunView(lr.runId(), lr.state(), lr.finishedAt());
                    return view(node, running.getOrDefault(node.id(), 0), lastRun);
                })
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

    /**
     * 节点令牌鉴权（W2：分片 claim / complete 复用心跳同一口径）。
     * 与心跳的差别是**不刷新 last_heartbeat**——领任务不该顺带证明存活；
     * DISABLED 节点同样拒绝，停用必须真的停。
     */
    public ExecutionNode authenticate(long nodeId, String token) {
        ExecutionNode node = requireNodeWithToken(nodeId, token);
        if (ExecutionNode.DISABLED.equals(node.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "节点已停用，先由管理员启用");
        }
        return node;
    }

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
        return view(node, 0, null);
    }

    private NodeView view(ExecutionNode node, int runningRuns, LastRunView lastRun) {
        return new NodeView(node.id(), node.name(), node.status(),
                node.rfAvailable(), node.pytestAvailable(), node.maxConcurrency(),
                node.lastHeartbeatAt() == null ? null : node.lastHeartbeatAt().toString(),
                runningRuns, node.agentBaseUrl(), lastRun);
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
