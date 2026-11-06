package com.qa.autotest.domain.plan;

import java.time.Instant;
import java.util.List;

/**
 * 计划组（ORCH-01，v2.1）。
 *
 * <p>组只承载「成员 + 顺序」，不重复存图：链式依赖在建边时落进 {@code execution_plan_dependency}，
 * 复用 {@link PlanDependencyGraph} 的环检测，避免出现第二套图语义。
 *
 * @param items 已按 {@code sort_order} 升序；空列表表示组内没有计划
 */
public record PlanGroup(long id,
                        long projectId,
                        String name,
                        String description,
                        List<PlanGroupItem> items,
                        Long createdBy,
                        Instant createdAt) {

    public PlanGroup {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 组内成员：planId 与展示用名称（名称来自 execution_plan，成环报错时要靠它）。 */
    public record PlanGroupItem(long planId, String planName, int sortOrder) {
    }

    /** 链式建边需要的最少成员数：1 个成员没有边可建。 */
    public static final int MIN_CHAIN_SIZE = 2;

    /** 按链式顺序推出应建的边数：n 个成员 → n-1 条边。 */
    public int expectedEdgeCount() {
        return Math.max(0, items.size() - 1);
    }
}
