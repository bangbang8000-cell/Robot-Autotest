package com.qa.autotest.application.repository;

import com.qa.autotest.domain.plan.PlanGroup;

import java.util.List;
import java.util.Optional;

/**
 * 计划组仓储（ORCH-01）。所有查询都带 project_id 条件（TenantGuard 口径）。
 *
 * <p>边不在这里维护：建边复用 {@link PlanRepository#replaceDependencies}，
 * 保证「组推出来的边」与「手工配的边」落在同一张表、走同一套环检测。
 */
public interface PlanGroupRepository {

    /** 项目下的全部组，按 id 升序；每次带出成员（成员数一般 ≤ 50，不做分页）。 */
    List<PlanGroup> listByProject(long projectId);

    Optional<PlanGroup> find(long projectId, long id);

    /** 按名称精确查，用于唯一键冲突前的友好报错。 */
    Optional<PlanGroup> findByName(long projectId, String name);

    long insert(PlanGroup draft, Long operatorId);

    void update(long projectId, long id, String name, String description, Long operatorId);

    void delete(long projectId, long id);

    /** 追加成员到末尾；已存在的成员按幂等跳过（依赖唯一键 uk_plan_group_item）。 */
    int appendItems(long projectId, long groupId, List<Long> planIds, Long operatorId);

    void removeItem(long projectId, long groupId, long planId);

    /** 全量重排：{@code planIds} 的顺序即新顺序，不在列表里的成员被移除。 */
    void reorder(long projectId, long groupId, List<Long> planIds, Long operatorId);

    /**
     * 该计划是否属于**别的**项目。用于把「跨项目引用」判成 403 而不是笼统的 400
     * —— 两者对调用方的含义不同：前者是越权，后者是参数写错。
     */
    boolean existsInOtherProject(long projectId, long planId);
}
