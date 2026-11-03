package com.qa.autotest.application.plan;

import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.plan.PlanCycleException;
import com.qa.autotest.domain.plan.PlanDependencyGraph;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.qa.autotest.application.plan.PlanTestSupport.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 计划依赖 DAG（PLN-02）：环检测、拓扑序、下游推导，以及触发/阻断语义。 */
class PlanDependencyGraphTest {

    private static final Instant FIRE = Instant.parse("2026-11-03T02:00:00Z");
    private static final List<String> SUITES = List.of("suites/a.robot");

    @Test
    @DisplayName("无环图给出前置在前的拓扑序")
    void 无环拓扑() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(
                1L, List.of(2L),
                2L, List.of(3L),
                3L, List.of()));

        assertEquals(List.of(3L, 2L, 1L), graph.topologicalOrder());
    }

    @Test
    @DisplayName("自环（依赖自己）被拒绝")
    void 自环拒绝() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(1L, List.of()));

        PlanCycleException cycle = assertThrows(PlanCycleException.class,
                () -> graph.assertNoCycle(1L, List.of(1L)));
        assertEquals(List.of(1L, 1L), cycle.cyclePath());
        assertTrue(cycle.getMessage().contains("检测到循环依赖"));
    }

    @Test
    @DisplayName("两节点环被拒绝，路径可解释")
    void 两节点环() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(
                1L, List.of(2L),
                2L, List.of(1L)));

        PlanCycleException cycle = assertThrows(PlanCycleException.class,
                () -> graph.assertNoCycle(1L, List.of(2L)));
        assertTrue(cycle.cyclePath().size() >= 3);
        assertEquals(cycle.cyclePath().get(0), cycle.cyclePath().get(cycle.cyclePath().size() - 1));
    }

    @Test
    @DisplayName("三节点环被拒绝：1 → 2 → 3 → 1")
    void 三节点环() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(
                1L, List.of(2L),
                2L, List.of(3L),
                3L, List.of(1L)));

        PlanCycleException cycle = assertThrows(PlanCycleException.class,
                () -> graph.assertNoCycle(3L, List.of(1L)));
        assertTrue(cycle.getMessage().contains("3") && cycle.getMessage().contains("1"));
    }

    @Test
    @DisplayName("直接下游只取一跳")
    void 直接下游() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(
                1L, List.of(),
                2L, List.of(1L),
                3L, List.of(1L),
                4L, List.of(2L)));

        assertEquals(Set.of(2L, 3L), Set.copyOf(graph.directDownstream(1L)));
        assertEquals(Set.of(4L), Set.copyOf(graph.directDownstream(2L)));
    }

    @Test
    @DisplayName("间接下游返回传递闭包")
    void 间接下游() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(
                1L, List.of(),
                2L, List.of(1L),
                3L, List.of(2L),
                4L, List.of(2L)));

        assertEquals(Set.of(2L, 3L, 4L), Set.copyOf(graph.allDownstream(1L)));
    }

    @Test
    @DisplayName("替换依赖时把旧边一起算入，仍能发现环")
    void 替换依赖仍能发现环() {
        PlanDependencyGraph graph = PlanDependencyGraph.of(Map.of(
                1L, List.of(),
                2L, List.of(1L),
                3L, List.of(2L)));

        // 把 1 改成依赖 3，形成 1 → 3 → 2 → 1
        assertThrows(PlanCycleException.class, () -> graph.assertNoCycle(1L, List.of(3L)));
    }

    @Test
    @DisplayName("上游成功：只触发直接下游，不越过间接下游")
    void 成功触发直接下游() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        PlanTestSupport.StubRunService runs = new PlanTestSupport.StubRunService();
        PlanTestSupport.StubAudit audit = new PlanTestSupport.StubAudit();
        PlanTestSupport.RecordingDispatcher dispatcher = new PlanTestSupport.RecordingDispatcher();
        PlanScheduleService service = PlanTestSupport.service(plans, runs, audit, dispatcher,
                new PlanProperties());

        PlanRecord upstream = plan(1L, PlanStatus.ENABLED, null, FIRE, "0 0 * * * *", SUITES);
        PlanRecord middle = plan(2L, PlanStatus.ENABLED, null, FIRE, "0 0 * * * *", SUITES);
        PlanRecord leaf = plan(3L, PlanStatus.ENABLED, null, FIRE, "0 0 * * * *", SUITES);
        plans.put(upstream);
        plans.put(middle);
        plans.put(leaf);
        plans.depend(2L, 1L);
        plans.depend(3L, 2L);

        service.afterRun(upstream, PlanTestSupport.run(1L, "UP-1", RunState.FINISHED));

        assertEquals(1, runs.commands.size(), "只应触发直接下游 2");
        assertEquals(2L, runs.origins.get(0).planId());
        assertTrue(audit.contains("PLAN_DOWNSTREAM_TRIGGERED", "upstream_plan=1"));
        assertEquals(1, dispatcher.runIds.size());
    }

    @Test
    @DisplayName("上游失败：下游不触发，记录 skipped_dependency 与原因")
    void 失败不触发下游() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        PlanTestSupport.StubRunService runs = new PlanTestSupport.StubRunService();
        PlanTestSupport.StubAudit audit = new PlanTestSupport.StubAudit();
        PlanScheduleService service = PlanTestSupport.service(plans, runs, audit,
                new PlanTestSupport.RecordingDispatcher(), new PlanProperties());

        PlanRecord upstream = plan(1L, PlanStatus.ENABLED, null, FIRE, "0 0 * * * *", SUITES);
        plans.put(upstream);
        plans.put(plan(2L, PlanStatus.ENABLED, null, FIRE, "0 0 * * * *", SUITES));
        plans.depend(2L, 1L);

        service.afterRun(upstream, PlanTestSupport.run(1L, "UP-2", RunState.FAILED));

        assertTrue(runs.commands.isEmpty(), "失败不得触发下游");
        assertTrue(audit.contains("PLAN_DOWNSTREAM_SKIPPED", "mark=skipped_dependency"));
        assertTrue(audit.contains("PLAN_DOWNSTREAM_SKIPPED", "reason=upstream_failed"));
    }

    @Test
    @DisplayName("下游暂停：成功也不触发，记录 downstream_paused")
    void 暂停下游不触发() {
        PlanTestSupport.FakePlanRepository plans = new PlanTestSupport.FakePlanRepository();
        PlanTestSupport.StubRunService runs = new PlanTestSupport.StubRunService();
        PlanTestSupport.StubAudit audit = new PlanTestSupport.StubAudit();
        PlanScheduleService service = PlanTestSupport.service(plans, runs, audit,
                new PlanTestSupport.RecordingDispatcher(), new PlanProperties());

        PlanRecord upstream = plan(1L, PlanStatus.ENABLED, null, FIRE, "0 0 * * * *", SUITES);
        plans.put(upstream);
        plans.put(plan(2L, PlanStatus.PAUSED, null, FIRE, "0 0 * * * *", SUITES));
        plans.depend(2L, 1L);

        service.afterRun(upstream, PlanTestSupport.run(1L, "UP-3", RunState.FINISHED));

        assertTrue(runs.commands.isEmpty());
        assertTrue(audit.contains("PLAN_DOWNSTREAM_SKIPPED", "reason=downstream_paused"));
    }
}
