package com.qa.autotest.api.plan;

import com.qa.autotest.application.plan.PlanConflictService;
import com.qa.autotest.application.plan.PlanConflictService.PlanConflict;
import com.qa.autotest.application.plan.PlanService;
import com.qa.autotest.application.plan.PlanService.BatchPlanResult;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计划编排助手（W5/v3.0.1）：冲突检测 + 批量启停。
 *
 * <p>单独一个控制器而不是塞进 {@link PlanController}：这两组能力是「读全部计划 + 批量写」，
 * 与单条 CRUD 的权限模型相同但语义不同，拆开后既有的 PlanController 构造器与测试不用改。
 */
@RestController
@RequestMapping("/api/plans")
public class PlanAssistantController {

    private final PlanConflictService conflicts;
    private final PlanService plans;

    public PlanAssistantController(PlanConflictService conflicts, PlanService plans) {
        this.conflicts = conflicts;
        this.plans = plans;
    }

    /** 只读检测；不自动修复，用户看清后再调批量启停。 */
    @GetMapping("/conflicts")
    public ApiResponse<List<PlanConflict>> detect(@RequestParam long projectId) {
        return ApiResponse.ok(conflicts.detect(projectId));
    }

    @PostMapping("/batch")
    public ApiResponse<List<BatchPlanResult>> batch(@RequestBody BatchRequest request) {
        return ApiResponse.ok(plans.batchStatus(request.projectId(), request.planIds(),
                request.status()));
    }

    public record BatchRequest(long projectId, List<Long> planIds, String status) {
    }
}
