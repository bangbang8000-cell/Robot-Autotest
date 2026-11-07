package com.qa.autotest.application.plan;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.PlanGroupRepository;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.plan.PlanCycleException;
import com.qa.autotest.domain.plan.PlanDependencyGraph;
import com.qa.autotest.domain.plan.PlanGroup;
import com.qa.autotest.domain.plan.PlanRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 计划组管理（ORCH-01，v2.1）。
 *
 * <p>职责边界：组只维护「成员 + 顺序」；**建边不新增图语义**——按链式顺序推出来的边
 * 交给 {@link PlanRepository#replaceDependencies} 写进 {@code execution_plan_dependency}，
 * 环检测复用 {@link PlanDependencyGraph}。这样「组推出来的依赖」和「手工配的依赖」
 * 在库里没有区别，调度器与前端图视图都不用改。
 *
 * <p>权限口径与 {@link PlanService} 一致：读 {@code EXECUTION:READ}，
 * 写（建组/改组/删组/增删成员/应用依赖）{@code EXECUTION:UPDATE}。
 */
@Service
public class PlanGroupService {

    private static final int MAX_NAME = 128;
    private static final int MAX_DESCRIPTION = 512;

    private final PlanGroupRepository groups;
    private final PlanRepository plans;
    private final AccessControlService access;
    private final AuditService audit;

    public PlanGroupService(PlanGroupRepository groups,
                            PlanRepository plans,
                            AccessControlService access,
                            AuditService audit) {
        this.groups = groups;
        this.plans = plans;
        this.access = access;
        this.audit = audit;
    }

    public List<PlanGroupView> list(long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        return groups.listByProject(projectId).stream().map(PlanGroupView::of).toList();
    }

    @Transactional
    public PlanGroupView create(GroupCommand command) {
        requireProject(command == null ? 0 : command.projectId());
        long projectId = command.projectId();
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);

        String name = requireName(command.name());
        String description = normalizeDescription(command.description());
        if (groups.findByName(projectId, name).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "计划组名称已存在: " + name);
        }
        long id = groups.insert(new PlanGroup(0L, projectId, name, description, List.of(),
                TenantContext.userId(), null), TenantContext.userId());
        audit.record("PLAN_GROUP_CREATE", "plan_group", String.valueOf(id), "name=" + name);
        return PlanGroupView.of(load(projectId, id));
    }

    @Transactional
    public PlanGroupView update(long groupId, GroupCommand command) {
        requireProject(command == null ? 0 : command.projectId());
        long projectId = command.projectId();
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);

        PlanGroup existing = load(projectId, groupId);
        String name = requireName(command.name());
        String description = normalizeDescription(command.description());
        groups.findByName(projectId, name)
                .filter(other -> other.id() != groupId)
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "计划组名称已存在: " + name);
                });
        groups.update(projectId, groupId, name, description, TenantContext.userId());
        audit.record("PLAN_GROUP_UPDATE", "plan_group", String.valueOf(groupId),
                "name=" + existing.name() + " -> " + name);
        return PlanGroupView.of(load(projectId, groupId));
    }

    /**
     * 删组。只删组成员，**不删**已生成的依赖边——边是计划自己的配置，
     * 删组不该改变已有计划的调度行为（口径见 V9_1 头注释第 4 条）。
     */
    @Transactional
    public void delete(long projectId, long groupId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);
        PlanGroup existing = load(projectId, groupId);
        groups.delete(projectId, groupId);
        audit.record("PLAN_GROUP_DELETE", "plan_group", String.valueOf(groupId),
                "name=" + existing.name() + ", members=" + existing.items().size()
                        + ", edges_kept=true");
    }

    /** 追加成员到末尾。重复加入按幂等处理（不报错、不重复插）。 */
    @Transactional
    public PlanGroupView addMembers(long projectId, long groupId, List<Long> planIds) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);
        load(projectId, groupId);

        List<Long> normalized = normalizePlanIds(planIds);
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "planIds 不能为空");
        }
        for (Long planId : normalized) {
            requirePlanInProject(projectId, planId);
        }
        int inserted = groups.appendItems(projectId, groupId, normalized, TenantContext.userId());
        audit.record("PLAN_GROUP_ADD_MEMBERS", "plan_group", String.valueOf(groupId),
                "requested=" + normalized.size() + ", inserted=" + inserted);
        return PlanGroupView.of(load(projectId, groupId));
    }

    @Transactional
    public PlanGroupView removeMember(long projectId, long groupId, long planId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);
        load(projectId, groupId);
        groups.removeItem(projectId, groupId, planId);
        audit.record("PLAN_GROUP_REMOVE_MEMBER", "plan_group", String.valueOf(groupId),
                "planId=" + planId);
        return PlanGroupView.of(load(projectId, groupId));
    }

    /**
     * 全量重排：{@code planIds} 的顺序即新顺序。列表外的现有成员会被移除（前端拖拽后的整体提交）。
     *
     * <p>N8：重排后**自动应用**链式依赖——顺序变了但边没变的组，调度器仍按旧顺序跑，
     * 前端图上看到的顺序与实际执行顺序不一致。应用与重排同事务：成环时整体回滚，
     * 不会留下「顺序已改、边还是旧的」的中间态。单成员组没有边可建，跳过应用。
     */
    @Transactional
    public PlanGroupView reorder(long projectId, long groupId, List<Long> planIds) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);
        PlanGroup existing = load(projectId, groupId);

        List<Long> normalized = normalizePlanIds(planIds);
        LinkedHashSet<Long> current = new LinkedHashSet<>(
                existing.items().stream().map(PlanGroup.PlanGroupItem::planId).toList());
        for (Long planId : normalized) {
            if (!current.contains(planId)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "计划不在组内，无法参与排序: " + planId);
            }
        }
        groups.reorder(projectId, groupId, normalized, TenantContext.userId());
        audit.record("PLAN_GROUP_REORDER", "plan_group", String.valueOf(groupId),
                "size=" + normalized.size());

        if (normalized.size() >= PlanGroup.MIN_CHAIN_SIZE) {
            PlanGroupView.ApplyResult applied = applyChainInternal(projectId, groupId, true);
            audit.record("PLAN_GROUP_REORDER_APPLY", "plan_group", String.valueOf(groupId),
                    "created=" + applied.created() + ", skipped=" + applied.skipped());
        }
        return PlanGroupView.of(load(projectId, groupId));
    }

    /**
     * 把组内链式顺序落成依赖边：相邻两条，后一条依赖前一条（n 个成员 → n-1 条边）。
     *
     * <p>三条口径：
     * <ul>
     *   <li><b>追加而非覆盖</b>：成员原有的组外依赖保留，只补组内这条；
     *       否则用户手工配的依赖会被一次「应用」抹掉；</li>
     *   <li><b>整图检测</b>：把新边代入整张项目图再判环，不是只看组内
     *       —— 跨组的环才是最容易漏的；</li>
     *   <li><b>失败不落库</b>：环检测在建边写库之前完成，检测失败时一条边都不写。</li>
     * </ul>
     */
    @Transactional
    public PlanGroupView.ApplyResult applyChain(long projectId, long groupId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);
        return applyChainInternal(projectId, groupId, false);
    }

    /**
     * 应用链式依赖的实现体（不做权限/绑定校验）。{@link #reorder} 与 {@link #applyChain}
     * 共用：前者重排后立刻应用，权限已在方法入口校验过一次，不重复走一遍。
     *
     * @param resetReverse 为 true 时先拆掉组内「与新顺序反向」的旧边——只有 <b>重排</b> 才该这么做：
     *                     用户明确改了顺序，上次应用留下的链式边在新顺序下必然反向，
     *                     不拆就永成环。显式 applyChain 时保持严格语义：与既有手工作边成环就报错，
     *                     不能静默删掉用户手工配置的依赖。
     */
    private PlanGroupView.ApplyResult applyChainInternal(long projectId, long groupId,
                                                          boolean resetReverse) {
        PlanGroup group = load(projectId, groupId);
        List<PlanGroup.PlanGroupItem> items = group.items();
        if (items.size() < PlanGroup.MIN_CHAIN_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "组内至少需要 " + PlanGroup.MIN_CHAIN_SIZE + " 个计划才能建依赖");
        }

        Map<Long, List<Long>> edges = new LinkedHashMap<>(plans.allDependencies(projectId));
        Map<Long, List<Long>> touched = new LinkedHashMap<>();
        Map<Long, Integer> position = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            position.put(items.get(i).planId(), i);
        }

        // 先把组内「与本次新顺序反向」的旧边拆掉（上次应用留下的链式边在新顺序下必然是反向边）。
        // 不拆的话：组 [A,B,C] 应用过一次，再重排成 [C,B,A]，新增的 B<-C、A<-B 会与旧边成环，
        // reorder 永远失败。组外依赖（端点在组外）原样保留。
        int removed = 0;
        if (resetReverse) {
            for (PlanGroup.PlanGroupItem item : items) {
                List<Long> current = edges.get(item.planId());
                if (current == null || current.isEmpty()) {
                    continue;
                }
                int pos = position.get(item.planId());
                List<Long> kept = new ArrayList<>(current.size());
                boolean changed = false;
                for (Long dep : current) {
                    Integer depPos = position.get(dep);
                    if (depPos != null && depPos > pos) {
                        changed = true;
                        removed++;
                        continue;
                    }
                    kept.add(dep);
                }
                if (changed) {
                    edges.put(item.planId(), kept);
                    touched.put(item.planId(), kept);
                }
            }
        }

        int created = 0;
        int skipped = 0;
        for (int i = 1; i < items.size(); i++) {
            long planId = items.get(i).planId();
            long dependsOn = items.get(i - 1).planId();
            List<Long> current = new ArrayList<>(edges.getOrDefault(planId, List.of()));
            if (current.contains(dependsOn)) {
                skipped++;
                continue;
            }
            current.add(dependsOn);
            edges.put(planId, current);
            touched.put(planId, current);
            created++;
        }

        if (created > 0 || removed > 0) {
            if (created > 0) {
                assertAcyclic(projectId, edges);
            }
            Long operator = TenantContext.userId();
            touched.forEach((planId, deps) -> plans.replaceDependencies(projectId, planId, deps, operator));
            audit.record("PLAN_GROUP_APPLY", "plan_group", String.valueOf(groupId),
                    "created=" + created + ", skipped=" + skipped + ", removed=" + removed);
        }
        return new PlanGroupView.ApplyResult(groupId, created, skipped,
                PlanGroupView.of(load(projectId, groupId)));
    }

    /**
     * 整图环检测。失败时把环路径翻成**计划名**再抛——
     * 只报 id 等于让用户在几十条计划里自己找（{@link PlanCycleException} 也只带 id）。
     */
    private void assertAcyclic(long projectId, Map<Long, List<Long>> edges) {
        try {
            PlanDependencyGraph.of(edges).topologicalOrder();
        } catch (PlanCycleException cycle) {
            StringJoiner path = new StringJoiner(" → ");
            for (Long planId : cycle.cyclePath()) {
                path.add(plans.find(projectId, planId)
                        .map(PlanRecord::name)
                        .orElse("#" + planId));
            }
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "组内顺序与既有依赖成环: " + path);
        }
    }

    private void requirePlanInProject(long projectId, long planId) {
        if (plans.find(projectId, planId).isPresent()) {
            return;
        }
        // 区分「越权」与「写错 id」：前者 403，后者 400。
        if (groups.existsInOtherProject(projectId, planId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "计划属于其它项目: " + planId);
        }
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划不存在: " + planId);
    }

    private PlanGroup load(long projectId, long groupId) {
        return groups.find(projectId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "计划组不存在: " + groupId));
    }

    private void requireProject(long projectId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划组名称不能为空");
        }
        if (name.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "计划组名称不能超过 " + MAX_NAME + " 字符");
        }
        return name;
    }

    private static String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() > MAX_DESCRIPTION) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "描述不能超过 " + MAX_DESCRIPTION + " 字符");
        }
        return text.isEmpty() ? null : text;
    }

    private static List<Long> normalizePlanIds(List<Long> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long planId : raw) {
            if (planId == null || planId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "计划 id 非法");
            }
            unique.add(planId);
        }
        return List.copyOf(unique);
    }

    /** 组命令：projectId 必填，其余可空（更新时按新值覆盖）。 */
    public record GroupCommand(Long projectId, String name, String description) {
    }
}
