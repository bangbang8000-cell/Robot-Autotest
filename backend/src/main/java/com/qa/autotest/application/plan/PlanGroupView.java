package com.qa.autotest.application.plan;

import com.qa.autotest.domain.plan.PlanGroup;

import java.time.Instant;
import java.util.List;

/**
 * 计划组视图（ORCH-01）。
 *
 * @param items     已按组内顺序排列；前端按此顺序画链
 * @param edgeCount 当前链式顺序**应有的**边数（n-1），用于展示「应用后将建几条边」
 */
public record PlanGroupView(long id,
                            long projectId,
                            String name,
                            String description,
                            List<Member> items,
                            int edgeCount,
                            Instant createdAt) {

    public record Member(long planId, String planName, int sortOrder) {
    }

    public static PlanGroupView of(PlanGroup group) {
        List<Member> members = group.items().stream()
                .map(item -> new Member(item.planId(), item.planName(), item.sortOrder()))
                .toList();
        return new PlanGroupView(group.id(), group.projectId(), group.name(),
                group.description(), members, group.expectedEdgeCount(), group.createdAt());
    }

    /** 应用链式依赖后的结果：新建了几条边、跳过几条（已存在）。 */
    public record ApplyResult(long groupId, int created, int skipped, PlanGroupView group) {
    }
}
