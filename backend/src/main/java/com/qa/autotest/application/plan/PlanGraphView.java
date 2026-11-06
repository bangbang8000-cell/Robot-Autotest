package com.qa.autotest.application.plan;

import java.util.List;

/**
 * 计划依赖图视图（ORCH-02，v2.1）。
 *
 * @param criticalPath    关键路径的节点序列（起点 → 终点），空列表表示图里没有边
 * @param criticalSeconds 关键路径上的权重之和（超时秒数），**是估算不是实测耗时**
 * @param maxLayer        最大层号，前端据此决定画几列
 */
public record PlanGraphView(long projectId,
                            List<Node> nodes,
                            List<Edge> edges,
                            List<Long> criticalPath,
                            long criticalSeconds,
                            int maxLayer) {

    public PlanGraphView {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        criticalPath = criticalPath == null ? List.of() : List.copyOf(criticalPath);
    }

    /**
     * 一个节点。
     *
     * @param layer          所在层（0 = 没有前置），前端按层分列
     * @param isolated       是否孤立（既无前置也无后置）：这类计划不会被依赖触发/联动
     * @param onCriticalPath 是否落在关键路径上，前端高亮
     */
    public record Node(long id,
                       String name,
                       String status,
                       int timeoutSeconds,
                       int layer,
                       boolean isolated,
                       boolean onCriticalPath) {
    }

    /**
     * 一条边。
     *
     * @param from 前置计划 id
     * @param to   后置计划 id
     */
    public record Edge(long from, long to) {
    }
}
