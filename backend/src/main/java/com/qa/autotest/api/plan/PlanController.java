package com.qa.autotest.api.plan;

import com.qa.autotest.application.plan.PlanCommand;
import com.qa.autotest.application.plan.PlanService;
import com.qa.autotest.application.plan.PlanView;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.execution.RunRecord;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 执行计划接口（PLN-01 / PLN-02）。
 *
 * <p>路径按任务书：{@code /api/plans}，项目用 {@code projectId} 传入；
 * 服务层统一走 {@code access.bindProject} 做项目级权限校验。
 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public ApiResponse<PageResult<PlanView>> page(@RequestParam long projectId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(plans.page(projectId, page, Math.min(size, 100)));
    }

    @PostMapping
    public ApiResponse<PlanView> create(@RequestBody PlanRequest request) {
        return ApiResponse.ok(plans.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlanView> update(@PathVariable long id, @RequestBody PlanRequest request) {
        return ApiResponse.ok(plans.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestParam long projectId, @PathVariable long id) {
        plans.delete(projectId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<PlanView> pause(@RequestParam long projectId, @PathVariable long id) {
        return ApiResponse.ok(plans.pause(projectId, id));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<PlanView> resume(@RequestParam long projectId, @PathVariable long id) {
        return ApiResponse.ok(plans.resume(projectId, id));
    }

    /** 立即触发：不受依赖与并发上限约束，但需 EXECUTION:EXECUTE；trigger_type 仍为 manual。 */
    @PostMapping("/{id}/trigger-now")
    public ApiResponse<RunRecord> triggerNow(@RequestParam long projectId, @PathVariable long id) {
        return ApiResponse.ok(plans.triggerNow(projectId, id));
    }

    /** 表单 cron 预览：返回未来 3 次触发点；非法表达式 400。 */
    @PostMapping("/preview")
    public ApiResponse<List<Instant>> preview(@RequestBody PreviewRequest request) {
        return ApiResponse.ok(plans.preview(request.projectId(), request.cron(), request.timezone()));
    }

    public record PlanRequest(Long projectId,
                              String name,
                              Long testSetId,
                              Long environmentId,
                              List<String> suitePaths,
                              String includeTag,
                              Integer concurrency,
                              Integer timeoutSeconds,
                              String cron,
                              String timezone,
                              String overlapPolicy,
                              List<Long> dependencyIds) {

        PlanCommand toCommand() {
            if (projectId == null || projectId <= 0) {
                throw new IllegalArgumentException("projectId 不能为空");
            }
            return new PlanCommand(projectId,
                    name,
                    testSetId,
                    environmentId,
                    suitePaths == null ? List.of() : suitePaths,
                    includeTag,
                    concurrency == null ? 1 : concurrency,
                    timeoutSeconds == null ? 3600 : timeoutSeconds,
                    cron,
                    timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone,
                    overlapPolicy,
                    dependencyIds == null ? List.of() : dependencyIds);
        }
    }

    public record PreviewRequest(long projectId, String cron, String timezone) {
    }
}
