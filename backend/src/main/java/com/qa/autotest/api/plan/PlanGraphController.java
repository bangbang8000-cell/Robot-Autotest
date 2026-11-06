package com.qa.autotest.api.plan;

import com.qa.autotest.application.plan.PlanGraphService;
import com.qa.autotest.application.plan.PlanGraphView;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计划依赖图接口（ORCH-02，v2.1）。
 *
 * <p>只读：图是「现有配置长什么样」的投影，编辑入口仍在计划与计划组，
 * 不提供「拖一条边」的接口——拖拽会绕过保存时的环检测。
 */
@RestController
@RequestMapping("/api/plan-graph")
public class PlanGraphController {

    private final PlanGraphService graphs;

    public PlanGraphController(PlanGraphService graphs) {
        this.graphs = graphs;
    }

    @GetMapping
    public ApiResponse<PlanGraphView> graph(@RequestParam long projectId) {
        return ApiResponse.ok(graphs.graph(projectId));
    }
}
