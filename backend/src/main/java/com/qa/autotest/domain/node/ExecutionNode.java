package com.qa.autotest.domain.node;

/**
 * 执行节点（NODX-01）。
 *
 * <p>四态口径（写在字段注释里也写在文档里）：
 * <ul>
 *   <li>{@link #NEW}：已注册、从没心跳过——注册完还没部署 agent 的中间态；</li>
 *   <li>{@link #ONLINE}：心跳在周期内；</li>
 *   <li>{@link #OFFLINE}：连续 3 个心跳周期缺席（由 {@code NodeHeartbeatMonitor} 置位），
 *       离线节点**拒绝发起、不排队**（口径见 DEV-PLAN §5.3）；</li>
 *   <li>{@link #DISABLED}：管理员手动停用，心跳一律拒绝。</li>
 * </ul>
 *
 * <p>节点是平台级资源：无 project_id，不进 TenantGuard 的项目过滤；接口侧用 isAdmin 收口。
 */
public record ExecutionNode(long id,
                            String name,
                            String tokenHash,
                            String status,
                            boolean rfAvailable,
                            boolean pytestAvailable,
                            int maxConcurrency,
                            java.time.Instant lastHeartbeatAt,
                            java.time.Instant registeredAt) {

    public static final String NEW = "NEW";
    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";
    public static final String DISABLED = "DISABLED";

    public boolean platformLevel() {
        return true;
    }
}
