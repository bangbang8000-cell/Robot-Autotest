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
 * 计划依赖图布局与关键路径（ORCH-02，v2.1）。
 *
 * <p>纯函数、不依赖框架与数据库：前端要的是「每个点在第几层、哪条链最长」，
 * 这两件事都能只靠边集算出来，放到领域层就能脱离 Spring 单测。
 *
 * <p>三个口径：
 * <ol>
 *   <li><b>层</b>（{@link #layers()}）：无前置的点在第 0 层，其余 = 前置最大层 + 1。
 *       布局靠它分列，避免让前端自己去猜拓扑位置；</li>
 *   <li><b>关键路径</b>（{@link #criticalPath()}）：沿边累加节点权重（用计划超时秒数），
 *       取总和最大的一条。**相同时取字典序最小的一条**——否则同一张图两次渲染高亮不同链路，
 *       用户会以为自己点错了；</li>
 *   <li><b>孤立点</b>（{@link #isolatedNodes()}）：既无前置也无后置的点。它们参与布局但不参与关键路径，
 *       视觉上要能区分出来（这些计划不会被任何依赖触发/联动）。</li>
 * </ol>
 *
 * <p>权重口径是「超时秒数」而不是历史耗时：本版没有可靠的历史耗时（按用例粒度统计才有意义），
 * 用超时值至少能表达「这条链理论上最慢」。界面上必须标注「估算」，不能当成真实耗时。
 */
public final class PlanGraph {

    /** 计划 -> 它依赖的前置集合（边方向：后置指向前置）。 */
    private final Map<Long, Set<Long>> dependsOn;
    /** 节点权重（超时秒数）；缺省按 0 处理。 */
    private final Map<Long, Long> weights;

    private PlanGraph(Map<Long, Set<Long>> dependsOn, Map<Long, Long> weights) {
        this.dependsOn = dependsOn;
        this.weights = weights;
    }

    /**
     * @param edges   计划 -> 前置列表
     * @param weights 计划 -> 权重（秒）；缺失的按 0 处理，不会让关键路径算崩
     */
    public static PlanGraph of(Map<Long, ? extends Collection<Long>> edges,
                               Map<Long, Long> weights) {
        Map<Long, Set<Long>> normalized = new TreeMap<>();
        if (edges != null) {
            edges.forEach((plan, deps) -> {
                if (plan == null) {
                    return;
                }
                normalized.put(plan, new LinkedHashSet<>(deps == null ? List.of() : deps));
            });
        }
        Map<Long, Long> safeWeights = new TreeMap<>();
        if (weights != null) {
            weights.forEach((plan, weight) -> {
                if (plan != null && weight != null) {
                    safeWeights.put(plan, Math.max(0L, weight));
                }
            });
        }
        return new PlanGraph(normalized, safeWeights);
    }

    /**
     * 所有节点：**权重表的键 + 边里出现的点**的并集。
     *
     * <p>把权重表的键也算进来很关键：一条边都没有的计划（孤立点）只可能在这里出现，
     * 只从边集推节点会把它整条漏掉，图上看起来「计划凭空消失了」。
     */
    public Set<Long> nodes() {
        Set<Long> all = new LinkedHashSet<>(weights.keySet());
        dependsOn.forEach((plan, deps) -> {
            all.add(plan);
            all.addAll(deps);
        });
        return all;
    }

    /** 拓扑序（前置在前）。有环时由 {@link PlanDependencyGraph} 的环检测先拦住，这里不再判。 */
    public List<Long> topologicalOrder() {
        Map<Long, Integer> state = new TreeMap<>();
        List<Long> order = new ArrayList<>();
        for (Long node : nodes()) {
            if (state.getOrDefault(node, 0) == 0) {
                dfs(node, state, order);
            }
        }
        return order;
    }

    /** 分层：无前置 = 第 0 层，否则 = 前置最大层 + 1。 */
    public Map<Long, Integer> layers() {
        Map<Long, Integer> layers = new TreeMap<>();
        for (Long node : topologicalOrder()) {
            int layer = 0;
            for (Long dep : dependsOn.getOrDefault(node, Set.of())) {
                layer = Math.max(layer, layers.getOrDefault(dep, 0) + 1);
            }
            layers.put(node, layer);
        }
        return layers;
    }

    /**
     * 关键路径：沿边累加权重最长的一条，返回从起点到终点的节点序列。
     *
     * <p>空图返回空列表；单点图返回该点本身（一条只有自己的「路径」）。
     */
    public List<Long> criticalPath() {
        Map<Long, Long> best = new TreeMap<>();
        Map<Long, Integer> length = new TreeMap<>();
        Map<Long, Long> predecessor = new TreeMap<>();
        for (Long node : topologicalOrder()) {
            long weight = weights.getOrDefault(node, 0L);
            long bestTotal = weight;
            int bestLength = 1;
            Long bestDep = null;
            for (Long dep : dependsOn.getOrDefault(node, Set.of())) {
                long candidate = best.getOrDefault(dep, 0L) + weight;
                int candidateLength = length.getOrDefault(dep, 0) + 1;
                // 先比总权重；同权重比链长（全零权重时靠它兜底，否则单点和长链分不出来）；
                // 再同则取 id 更小的前置，保证同一张图两次渲染高亮同一条链。
                boolean better = candidate > bestTotal
                        || (candidate == bestTotal && candidateLength > bestLength)
                        || (candidate == bestTotal && candidateLength == bestLength
                            && bestDep != null && dep < bestDep);
                if (better) {
                    bestTotal = candidate;
                    bestLength = candidateLength;
                    bestDep = dep;
                }
            }
            best.put(node, bestTotal);
            length.put(node, bestLength);
            if (bestDep != null) {
                predecessor.put(node, bestDep);
            }
        }
        if (best.isEmpty()) {
            return List.of();
        }
        // 终点：总权重最大 → 链最长 → id 最小
        Long end = null;
        long maxWeight = Long.MIN_VALUE;
        int maxLength = Integer.MIN_VALUE;
        for (Map.Entry<Long, Long> entry : best.entrySet()) {
            long weight = entry.getValue();
            int len = length.getOrDefault(entry.getKey(), 0);
            if (weight > maxWeight || (weight == maxWeight && len > maxLength)) {
                maxWeight = weight;
                maxLength = len;
                end = entry.getKey();
            }
        }
        if (end == null) {
            return List.of();
        }
        Deque<Long> path = new ArrayDeque<>();
        Long cursor = end;
        while (cursor != null) {
            path.addFirst(cursor);
            cursor = predecessor.get(cursor);
        }
        return List.copyOf(path);
    }

    /** 孤立点：既没有前置、也没有被任何计划依赖。 */
    public Set<Long> isolatedNodes() {
        Set<Long> downstream = new LinkedHashSet<>();
        dependsOn.forEach((plan, deps) -> downstream.addAll(deps));
        Set<Long> isolated = new LinkedHashSet<>();
        for (Long node : nodes()) {
            if (dependsOn.getOrDefault(node, Set.of()).isEmpty() && !downstream.contains(node)) {
                isolated.add(node);
            }
        }
        return isolated;
    }

    private void dfs(Long node, Map<Long, Integer> state, List<Long> order) {
        state.put(node, 1);
        for (Long dep : dependsOn.getOrDefault(node, Set.of())) {
            if (state.getOrDefault(dep, 0) == 0) {
                dfs(dep, state, order);
            }
        }
        state.put(node, 2);
        order.add(node);
    }
}
