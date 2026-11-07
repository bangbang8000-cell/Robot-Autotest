package com.qa.autotest.application.plan;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.plan.PlanGraph;
import com.qa.autotest.domain.plan.PlanGroup;
import com.qa.autotest.domain.plan.PlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 依赖图布局与关键路径（ORCH-02）：分层、最长路径、同分确定性、孤立点。
 *
 * <p>纯函数测试，不起 Spring、不连库。关键路径最容易写错的两处：
 * ① 权重算成「边数」而不是「节点权重之和」（本文用超时秒数）；
 * ② 同分时没定 tie-break，同一张图两次渲染高亮不同链路。
 */
class PlanGraphTest {

    @Test
    @DisplayName("链式 A->B->C 分成 0/1/2 三层")
    void 链式分层() {
        PlanGraph graph = PlanGraph.of(Map.of(
                1L, List.of(),          // A 无前置
                2L, List.of(1L),        // B 依赖 A
                3L, List.of(2L)),       // C 依赖 B
                Map.of(1L, 100L, 2L, 200L, 3L, 300L));

        assertEquals(Map.of(1L, 0, 2L, 1, 3L, 2), graph.layers());
    }

    @Test
    @DisplayName("分叉时层号取前置最大值 + 1")
    void 分叉分层() {
        // A(0) -> B(1) -> C(2)；A -> C 也应让 C 落在第 2 层
        PlanGraph graph = PlanGraph.of(Map.of(
                2L, List.of(1L),
                3L, List.of(1L, 2L)),
                Map.of(1L, 10L, 2L, 10L, 3L, 10L));

        assertEquals(2, graph.layers().get(3L));
    }

    @Test
    @DisplayName("关键路径按节点权重之和取最长，而不是边数")
    void 关键路径按权重() {
        // A(10) -> B(10)；A -> C(1000)。边数都是 1，权重 B=20 / C=1010，应选 C
        PlanGraph graph = PlanGraph.of(Map.of(
                2L, List.of(1L),
                3L, List.of(1L)),
                Map.of(1L, 10L, 2L, 10L, 3L, 1000L));

        assertEquals(List.of(1L, 3L), graph.criticalPath());
    }

    @Test
    @DisplayName("长链即使单点权重小也能胜出（累加才是关键路径）")
    void 长链胜出() {
        // A(10)->B(10)->C(10) 共 30；D(25) 单点 25。应选 A-B-C
        PlanGraph graph = PlanGraph.of(Map.of(
                2L, List.of(1L),
                3L, List.of(2L),
                4L, List.of()),
                Map.of(1L, 10L, 2L, 10L, 3L, 10L, 4L, 25L));

        assertEquals(List.of(1L, 2L, 3L), graph.criticalPath());
    }

    @Test
    @DisplayName("同分时取 id 更小的终点，保证两次渲染一致")
    void 同分确定性() {
        PlanGraph graph = PlanGraph.of(Map.of(
                2L, List.of(1L),
                3L, List.of(1L)),
                Map.of(1L, 5L, 2L, 5L, 3L, 5L));

        List<Long> first = graph.criticalPath();
        List<Long> second = graph.criticalPath();
        assertEquals(first, second);
        assertEquals(List.of(1L, 2L), first);
    }

    @Test
    @DisplayName("无前置也无后置的点被标为孤立点")
    void 孤立点() {
        PlanGraph graph = PlanGraph.of(Map.of(
                1L, List.of(),
                2L, List.of(1L),
                3L, List.of()),
                Map.of(1L, 1L, 2L, 1L, 3L, 1L));

        assertEquals(java.util.Set.of(3L), graph.isolatedNodes());
    }

    @Test
    @DisplayName("空图返回空关键路径，不抛异常")
    void 空图() {
        PlanGraph graph = PlanGraph.of(Map.of(), Map.of());
        assertTrue(graph.criticalPath().isEmpty());
        assertTrue(graph.nodes().isEmpty());
    }

    @Test
    @DisplayName("单点图的关键路径就是它自己")
    void 单点图() {
        PlanGraph graph = PlanGraph.of(Map.of(7L, List.of()), Map.of(7L, 60L));
        assertEquals(List.of(7L), graph.criticalPath());
    }

    @Test
    @DisplayName("权重缺失按 0 处理，不把关键路径算崩")
    void 权重缺失() {
        PlanGraph graph = PlanGraph.of(Map.of(2L, List.of(1L)), Map.of());
        assertEquals(List.of(1L, 2L), graph.criticalPath());
    }

    @Test
    @DisplayName("服务层：项目内计划与依赖组装成节点+边，孤立点标记正确")
    void 服务层组装() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        plans.put(PlanTestSupport.plan(1L, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                List.of("a.robot")));
        plans.put(PlanTestSupport.plan(2L, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                List.of("b.robot")));
        plans.put(PlanTestSupport.plan(3L, PlanStatus.PAUSED, null, null, "0 0 3 * * *",
                List.of("c.robot")));
        plans.depend(2L, 1L);

        PlanGraphService service = new PlanGraphService(plans,
                new PlanTestSupport.StubGroupRepository(), new PermissiveAccess());
        PlanGraphView view = service.graph(PlanTestSupport.PROJECT);

        assertEquals(3, view.nodes().size());
        assertEquals(1, view.edges().size());
        assertEquals(1L, view.edges().get(0).from());
        assertEquals(2L, view.edges().get(0).to());
        assertEquals(PlanGraphView.SOURCE_MANUAL, view.edges().get(0).source());
        assertEquals(List.of(1L, 2L), view.criticalPath());
        assertEquals(1, view.maxLayer());

        PlanGraphView.Node isolated = view.nodes().stream()
                .filter(node -> node.id() == 3L).findFirst().orElseThrow();
        assertTrue(isolated.isolated());
        assertFalse(view.nodes().stream().filter(node -> node.id() == 2L)
                .findFirst().orElseThrow().isolated());
    }

    @Test
    @DisplayName("服务层：projectId 非法直接拒绝")
    void 服务层非法项目() {
        PlanGraphService service = new PlanGraphService(new PlanTestSupport.FakePlanRepository(),
                new PlanTestSupport.StubGroupRepository(), new PermissiveAccess());
        BusinessException error = assertThrows(BusinessException.class, () -> service.graph(0L));
        assertEquals(ErrorCode.INVALID_ARGUMENT, error.errorCode());
    }

    @Test
    @DisplayName("N8：关键路径优先用历史 P50，无历史才回落超时秒数")
    void 预计时长用P50() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        // 超时 3600 但历史 P50 只有 600：权重必须取 600，否则 30 分钟的计划会被 1 小时的计划压过
        plans.put(PlanTestSupport.plan(1L, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                List.of("a.robot")));
        plans.put(PlanTestSupport.plan(2L, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                List.of("b.robot")));
        plans.depend(2L, 1L);
        plans.median(1L, 600L);

        PlanGraphService service = new PlanGraphService(plans,
                new PlanTestSupport.StubGroupRepository(), new PermissiveAccess());
        PlanGraphView view = service.graph(PlanTestSupport.PROJECT);

        PlanGraphView.Node first = view.nodes().stream().filter(node -> node.id() == 1L)
                .findFirst().orElseThrow();
        assertEquals(600L, first.estimatedSeconds());
        assertEquals(3600, first.timeoutSeconds());
        PlanGraphView.Node second = view.nodes().stream().filter(node -> node.id() == 2L)
                .findFirst().orElseThrow();
        assertNull(second.estimatedSeconds(), "无历史样本时 estimatedSeconds 必须为 null");
        // 1 取 P50 600 + 2 回落超时 3600 = 4200；若 1 也误用超时则是 7200
        assertEquals(4200, view.criticalSeconds());
    }

    @Test
    @DisplayName("N8：组内链式边标记为 GROUP，手工边标记为 MANUAL")
    void 边来源标记() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        for (long id = 1; id <= 4; id++) {
            plans.put(PlanTestSupport.plan(id, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                    List.of("p" + id + ".robot")));
        }
        plans.depend(2L, 1L);          // 组 10 的链 1 -> 2
        plans.depend(3L, 2L);          // 组 10 的链 2 -> 3
        plans.depend(4L, 1L);          // 手工边 1 -> 4

        PlanTestSupport.StubGroupRepository groups = new PlanTestSupport.StubGroupRepository();
        groups.chain(10L, 1L, 2L, 3L);

        PlanGraphService service = new PlanGraphService(plans, groups, new PermissiveAccess());
        PlanGraphView view = service.graph(PlanTestSupport.PROJECT);

        assertEquals(3, view.edges().size());
        view.edges().forEach(edge -> {
            String expected = edge.to() == 4L ? PlanGraphView.SOURCE_MANUAL
                    : PlanGraphView.SOURCE_GROUP;
            assertEquals(expected, edge.source(), edge.from() + "->" + edge.to());
        });
    }

    @Test
    @DisplayName("N8：组内顺序来自 sortOrder，乱序 items 也能推出正确的边")
    void 组内边按sortOrder推导() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        plans.put(PlanTestSupport.plan(1L, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                List.of("a.robot")));
        plans.put(PlanTestSupport.plan(2L, PlanStatus.ENABLED, null, null, "0 0 3 * * *",
                List.of("b.robot")));
        plans.depend(2L, 1L);

        PlanTestSupport.StubGroupRepository groups = new PlanTestSupport.StubGroupRepository();
        groups.groups.add(new PlanGroup(11L, PlanTestSupport.PROJECT, "g", null,
                List.of(new PlanGroup.PlanGroupItem(2L, "plan-2", 1),
                        new PlanGroup.PlanGroupItem(1L, "plan-1", 0)),
                PlanTestSupport.OPERATOR, PlanTestSupport.CLOCK.instant()));

        PlanGraphService service = new PlanGraphService(plans, groups, new PermissiveAccess());
        PlanGraphView view = service.graph(PlanTestSupport.PROJECT);

        assertEquals(PlanGraphView.SOURCE_GROUP, view.edges().get(0).source());
    }

    /** 权限桩。 */
    static final class PermissiveAccess extends AccessControlService {

        PermissiveAccess() {
            super(null, null);
        }

        @Override
        public void bindProject(long projectId) {
            // 测试不做项目成员校验
        }

        @Override
        public void require(PermissionResource resource, PermissionAction action) {
            // 测试不做角色校验
        }
    }
}
