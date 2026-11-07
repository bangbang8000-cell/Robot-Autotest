package com.qa.autotest.application.plan;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.PlanGroupRepository;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.plan.PlanGraph;
import com.qa.autotest.domain.plan.PlanGroup;
import com.qa.autotest.domain.plan.PlanRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计划依赖图（ORCH-02，v2.1）。
 *
 * <p>只读：本类不改任何数据。图从 `execution_plan_dependency` 读，布局与关键路径由
 * {@link PlanGraph} 这个纯函数算，视图层只管拼装。
 *
 * <p>为什么要分页拉全量而不是写一条 JOIN SQL：计划是**项目级配置**，正常量级是几十条，
 * 一条 SQL 多表聚合反而要把「前置名」「状态」都摊平再在 Java 里重组；
 * 分页拉取复用了 `PlanRepository.page` 的租户守卫，不需要新增 SQL 面。
 * 上限 {@value #MAX_PLANS} 条，超过直接拒绝——上万条计划的图在浏览器里也没法看。
 *
 * <p>N8 三项增强：
 * <ol>
 *   <li>关键路径权重优先用历史运行时长 P50，没有历史才回落为超时秒数（超时是熔断值，不是预期耗时）；</li>
 *   <li>每条边标记来源（组内链式 GROUP / 手工配置 MANUAL），前端用虚线区分；</li>
 *   <li>节点同时带出 P50，前端可显示「预计 X 分 / 超时 Y 分」。</li>
 * </ol>
 */
@Service
public class PlanGraphService {

    /** 单张图上限：超过这个量级应当分组（ORCH-01 的计划组）而不是画一张图。 */
    static final int MAX_PLANS = 500;
    private static final int PAGE_SIZE = 100;

    private final PlanRepository plans;
    private final PlanGroupRepository groups;
    private final AccessControlService access;

    public PlanGraphService(PlanRepository plans, PlanGroupRepository groups,
                            AccessControlService access) {
        this.plans = plans;
        this.groups = groups;
        this.access = access;
    }

    public PlanGraphView graph(long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }

        List<PlanRecord> all = loadAll(projectId);
        Map<Long, Long> medians = plans.medianDurationSeconds(projectId);
        Map<Long, PlanRecord> byId = new LinkedHashMap<>();
        Map<Long, Long> weights = new LinkedHashMap<>();
        for (PlanRecord plan : all) {
            byId.put(plan.id(), plan);
            Long estimated = medians.get(plan.id());
            weights.put(plan.id(), estimated != null ? estimated : (long) plan.timeoutSeconds());
        }

        Map<Long, List<Long>> edges = plans.allDependencies(projectId);
        PlanGraph graph = PlanGraph.of(edges, weights);
        List<Long> criticalPath = graph.criticalPath();
        Set<Long> critical = new LinkedHashSet<>(criticalPath);
        Map<Long, Integer> layers = graph.layers();
        Set<Long> isolated = graph.isolatedNodes();
        Set<String> groupEdges = groupEdgeKeys(projectId);

        List<PlanGraphView.Node> nodes = new ArrayList<>(all.size());
        for (PlanRecord plan : all) {
            nodes.add(new PlanGraphView.Node(plan.id(), plan.name(), plan.status().dbValue(),
                    plan.timeoutSeconds(), medians.get(plan.id()),
                    layers.getOrDefault(plan.id(), 0),
                    isolated.contains(plan.id()), critical.contains(plan.id())));
        }

        // 边只保留两端都在项目内的：依赖表理论上不会有跨项目边（保存时已校验），
        // 但历史脏数据不该让图渲染出一个没有名字的幽灵节点。
        List<PlanGraphView.Edge> edgesView = new ArrayList<>();
        edges.forEach((plan, deps) -> {
            if (!byId.containsKey(plan)) {
                return;
            }
            for (Long dep : deps) {
                if (byId.containsKey(dep)) {
                    String source = groupEdges.contains(dep + "->" + plan)
                            ? PlanGraphView.SOURCE_GROUP : PlanGraphView.SOURCE_MANUAL;
                    edgesView.add(new PlanGraphView.Edge(dep, plan, source));
                }
            }
        });

        long criticalSeconds = criticalPath.stream()
                .mapToLong(id -> weights.getOrDefault(id, 0L))
                .sum();
        int maxLayer = layers.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new PlanGraphView(projectId, nodes, edgesView, criticalPath, criticalSeconds, maxLayer);
    }

    /**
     * 组内相邻成员对推导出来的边键（"from->to"）。
     *
     * <p>不做 DB 迁移：组只存「成员 + 顺序」，链式边在建边时已落进
     * `execution_plan_dependency`，这里按顺序反推即可。同一条边即使既在组内又手工配过，
     * 也按 GROUP 展示——来源只是视觉提示，不影响可执行语义。
     */
    private Set<String> groupEdgeKeys(long projectId) {
        Set<String> keys = new HashSet<>();
        for (PlanGroup group : groups.listByProject(projectId)) {
            List<PlanGroup.PlanGroupItem> items = group.items().stream()
                    .sorted(java.util.Comparator.comparingInt(PlanGroup.PlanGroupItem::sortOrder))
                    .toList();
            for (int i = 0; i + 1 < items.size(); i++) {
                keys.add(items.get(i).planId() + "->" + items.get(i + 1).planId());
            }
        }
        return keys;
    }

    /** 分页取全量；超过上限直接拒绝，不截断（截断后的图会漏边，比不画更误导）。 */
    private List<PlanRecord> loadAll(long projectId) {
        List<PlanRecord> all = new ArrayList<>();
        int page = 1;
        while (true) {
            PageResult<PlanRecord> result = plans.page(projectId, page, PAGE_SIZE);
            all.addAll(result.items());
            if (all.size() > MAX_PLANS) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "计划数量超过 " + MAX_PLANS + " 条，请改用计划组分组查看");
            }
            if (result.items().size() < PAGE_SIZE || all.size() >= result.total()) {
                break;
            }
            page++;
        }
        return all;
    }
}
