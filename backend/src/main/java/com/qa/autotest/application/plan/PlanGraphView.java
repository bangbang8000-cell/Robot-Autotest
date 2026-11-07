package com.qa.autotest.application.plan;

import java.util.List;

/**
 * 计划依赖图视图（ORCH-02，v2.1）。
 *
 * @param criticalPath    关键路径的节点序列（起点 → 终点），空列表表示图里没有边
 * @param criticalSeconds 关键路径上的预计时长之和：优先历史 P50，无历史时回落为超时秒数
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
     * @param timeoutSeconds   计划配置的超时（熔断值），不是预期耗时
     * @param estimatedSeconds 历史运行时长 P50（秒）；null 表示没有历史样本，前端只显示超时值
     * @param layer            所在层（0 = 没有前置），前端按层分列
     * @param isolated         是否孤立（既无前置也无后置）：这类计划不会被依赖触发/联动
     * @param onCriticalPath   是否落在关键路径上，前端高亮
     */
    public record Node(long id,
                       String name,
                       String status,
                       int timeoutSeconds,
                       Long estimatedSeconds,
                       int layer,
                       boolean isolated,
                       boolean onCriticalPath) {
    }

    /** 边来源：组内链式（GROUP）还是手工配置（MANUAL）。 */
    public static final String SOURCE_GROUP = "GROUP";
    public static final String SOURCE_MANUAL = "MANUAL";

    /**
     * 一条边。
     *
     * @param from   前置计划 id
     * @param to     后置计划 id
     * @param source {@link #SOURCE_GROUP} / {@link #SOURCE_MANUAL}；前端据此区分实线/虚线
     */
    public record Edge(long from, long to, String source) {
    }
}
