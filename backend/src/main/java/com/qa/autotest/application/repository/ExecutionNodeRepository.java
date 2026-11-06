package com.qa.autotest.application.repository;

import com.qa.autotest.domain.node.ExecutionNode;

import java.util.List;
import java.util.Optional;

/**
 * 执行节点仓储（NODX-01）。
 *
 * <p>平台级表，查询不带 project_id 条件——这条豁免写在类注释与迁移注释里，
 * 不走 {@code TenantGuard.requireProjectScope}。
 */
public interface ExecutionNodeRepository {

    Optional<ExecutionNode> find(long id);

    Optional<ExecutionNode> findByName(String name);

    boolean nameExists(String name);

    List<ExecutionNode> list();

    /** 注册：tokenHash 是 SHA-256(明文)，明文只在服务层返回值里出现一次。 */
    ExecutionNode insert(String name, String tokenHash, boolean rfAvailable,
                         boolean pytestAvailable, int maxConcurrency, Long operatorId);

    /** 心跳：更新能力上报与 last_heartbeat_at，并置 ONLINE。 */
    void heartbeat(long id, boolean rfAvailable, boolean pytestAvailable);

    /** 监视器：把心跳超期的 ONLINE 节点置 OFFLINE（NEW / DISABLED 不动）。 */
    int markStaleOffline(java.time.Instant staleBefore);

    void updateStatus(long id, String status, Long operatorId);
}
