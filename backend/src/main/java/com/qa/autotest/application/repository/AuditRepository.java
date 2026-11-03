package com.qa.autotest.application.repository;

import com.qa.autotest.application.iam.AuditQuery;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.AuditEntry;
import java.util.List;

/** 审计日志端口：只增不改（不存在 update / delete 方法）。 */
public interface AuditRepository {

    void insert(Long projectId, Long actorId, String action, String resourceType,
                String resourceId, String detail);

    List<String> recentActions(Long projectId, int limit);

    /**
     * 带摘要的写入。默认实现退回旧的 insert，保证既有伪仓库不需要改签名；
     * JDBC 实现覆盖此方法以落 {@code operator / summary / ip}。
     */
    default void insertEntry(AuditEntry entry) {
        insert(entry.projectId(), entry.actorId(), entry.action(), entry.resourceType(),
                entry.resourceId(), entry.detail());
    }

    /** 分页查询；v1.0 由 JDBC 实现覆盖。 */
    default PageResult<AuditEntry> page(AuditQuery query) {
        AuditQuery q = query.normalized();
        return new PageResult<>(List.of(), 0, q.page(), q.size());
    }

    /** 导出查询（上限由调用方传入）。 */
    default List<AuditEntry> export(AuditQuery query, int limit) {
        return List.of();
    }
}
