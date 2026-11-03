package com.qa.autotest.domain.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 计划依赖 DAG（PLN-02，纯函数）。
 *
 * <p>边的方向：{@code planId -> dependsOnPlanId}，即「后置指向前置」。
 * 保存依赖时用 {@link #assertNoCycle(long, Collection)} 拒绝成环；
 * 触发时用 {@link #directDownstream(long)} 找前置成功后应联动的直接后置。
 *
 * <p>不依赖任何框架，可在没有数据库的情况下单测。
 */
public final class PlanDependencyGraph {

    /** key=计划，value=它依赖的前置集合。 */
    private final Map<Long, Set<Long>> dependsOn;

    private PlanDependencyGraph(Map<Long, Set<Long>> dependsOn) {
        this.dependsOn = dependsOn;
    }

    /** 由「计划 -> 前置列表」构造；节点顺序稳定（按 id 升序）。 */
    public static PlanDependencyGraph of(Map<Long, ? extends Collection<Long>> edges) {
        Map<Long, Set<Long>> normalized = new TreeMap<>();
        if (edges != null) {
            edges.forEach((planId, deps) -> {
                if (planId == null) {
                    return;
                }
                normalized.put(planId, new LinkedHashSet<>(deps == null ? List.of() : deps));
            });
        }
        return new PlanDependencyGraph(normalized);
    }

    /**
     * 保存前校验：把 {@code planId} 的依赖替换为 {@code proposed} 后，整张图不得有环（含自环）。
     *
     * @throws PlanCycleException 有环时抛出，路径形如 {@code 1 → 2 → 3 → 1}
     */
    public void assertNoCycle(long planId, Collection<Long> proposed) {
        PlanDependencyGraph candidate = withDependencies(planId, proposed);
        List<Long> cycle = candidate.firstCycle();
        if (!cycle.isEmpty()) {
            throw new PlanCycleException(cycle);
        }
    }

    /** 返回一份把指定计划依赖替换后的新图。 */
    public PlanDependencyGraph withDependencies(long planId, Collection<Long> dependencies) {
        Map<Long, Set<Long>> copy = new TreeMap<>();
        dependsOn.forEach((id, deps) -> copy.put(id, new LinkedHashSet<>(deps)));
        copy.put(planId, new LinkedHashSet<>(dependencies == null ? List.of() : dependencies));
        return new PlanDependencyGraph(copy);
    }

    /**
     * 拓扑序（前置在前、后置在后）。图中如有环抛 {@link PlanCycleException}。
     */
    public List<Long> topologicalOrder() {
        List<Long> order = new ArrayList<>();
        Map<Long, Integer> state = new TreeMap<>();
        Deque<Long> path = new ArrayDeque<>();
        for (Long node : allNodes()) {
            if (state.getOrDefault(node, 0) == 0) {
                dfs(node, state, path, order);
            }
        }
        return List.copyOf(order);
    }

    /** 直接后置：依赖里包含 planId 的计划。 */
    public Set<Long> directDownstream(long planId) {
        Set<Long> downstream = new LinkedHashSet<>();
        dependsOn.forEach((plan, deps) -> {
            if (deps.contains(planId) && plan != planId) {
                downstream.add(plan);
            }
        });
        return downstream;
    }

    /** 间接下游（传递闭包），按 id 升序返回。 */
    public Set<Long> allDownstream(long planId) {
        Set<Long> visited = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(directDownstream(planId));
        while (!queue.isEmpty()) {
            Long next = queue.removeFirst();
            if (!visited.add(next)) {
                continue;
            }
            queue.addAll(directDownstream(next));
        }
        return visited;
    }

    /** 所有出现过的计划 id（父节点与依赖节点并集，按 id 升序）。 */
    public Set<Long> allNodes() {
        Set<Long> nodes = new LinkedHashSet<>();
        dependsOn.forEach((plan, deps) -> {
            nodes.add(plan);
            nodes.addAll(deps);
        });
        return nodes;
    }

    private List<Long> firstCycle() {
        Map<Long, Integer> state = new TreeMap<>();
        Deque<Long> path = new ArrayDeque<>();
        for (Long node : allNodes()) {
            if (state.getOrDefault(node, 0) == 0) {
                List<Long> cycle = visit(node, state, path);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        return List.of();
    }

    private List<Long> visit(Long node, Map<Long, Integer> state, Deque<Long> path) {
        state.put(node, 1);
        path.addLast(node);
        for (Long dep : dependsOn.getOrDefault(node, Set.of())) {
            int depState = state.getOrDefault(dep, 0);
            if (depState == 1) {
                List<Long> cycle = new ArrayList<>();
                boolean started = false;
                for (Long onPath : path) {
                    if (onPath.equals(dep)) {
                        started = true;
                    }
                    if (started) {
                        cycle.add(onPath);
                    }
                }
                cycle.add(dep);
                return cycle;
            }
            if (depState == 0) {
                List<Long> cycle = visit(dep, state, path);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        path.removeLast();
        state.put(node, 2);
        return List.of();
    }

    private void dfs(Long node, Map<Long, Integer> state, Deque<Long> path, List<Long> order) {
        state.put(node, 1);
        path.addLast(node);
        for (Long dep : dependsOn.getOrDefault(node, Set.of())) {
            int depState = state.getOrDefault(dep, 0);
            if (depState == 1) {
                List<Long> cycle = new ArrayList<>();
                boolean started = false;
                for (Long onPath : path) {
                    if (onPath.equals(dep)) {
                        started = true;
                    }
                    if (started) {
                        cycle.add(onPath);
                    }
                }
                cycle.add(dep);
                throw new PlanCycleException(cycle);
            }
            if (depState == 0) {
                dfs(dep, state, path, order);
            }
        }
        path.removeLast();
        state.put(node, 2);
        order.add(node);
    }
}
