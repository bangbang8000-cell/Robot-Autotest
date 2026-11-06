package com.qa.autotest.api.plan;

import com.qa.autotest.application.plan.PlanGroupService;
import com.qa.autotest.application.plan.PlanGroupView;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计划组接口（ORCH-01，v2.1）。
 *
 * <p>路径与 {@link PlanController} 平级（{@code /api/plan-groups}），不复用 {@code /api/plans/{id}/groups}：
 * 组是独立聚合，删除组不应被理解为「改计划」。项目一律用 {@code projectId} 传入，
 * 服务层统一走 {@code access.bindProject} 做项目级权限校验。
 */
@RestController
@RequestMapping("/api/plan-groups")
public class PlanGroupController {

    private final PlanGroupService groups;

    public PlanGroupController(PlanGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    public ApiResponse<List<PlanGroupView>> list(@RequestParam long projectId) {
        return ApiResponse.ok(groups.list(projectId));
    }

    @PostMapping
    public ApiResponse<PlanGroupView> create(@RequestBody GroupRequest request) {
        return ApiResponse.ok(groups.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlanGroupView> update(@PathVariable long id, @RequestBody GroupRequest request) {
        return ApiResponse.ok(groups.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestParam long projectId, @PathVariable long id) {
        groups.delete(projectId, id);
        return ApiResponse.ok(null);
    }

    /** 追加成员：重复加入幂等，不报错。 */
    @PostMapping("/{id}/members")
    public ApiResponse<PlanGroupView> addMembers(@PathVariable long id,
                                                 @RequestBody MembersRequest request) {
        return ApiResponse.ok(groups.addMembers(request.projectId(), id, request.planIds()));
    }

    @DeleteMapping("/{id}/members/{planId}")
    public ApiResponse<PlanGroupView> removeMember(@RequestParam long projectId,
                                                   @PathVariable long id,
                                                   @PathVariable long planId) {
        return ApiResponse.ok(groups.removeMember(projectId, id, planId));
    }

    /** 全量重排：请求体的 planIds 顺序即新顺序。 */
    @PutMapping("/{id}/order")
    public ApiResponse<PlanGroupView> reorder(@PathVariable long id,
                                              @RequestBody MembersRequest request) {
        return ApiResponse.ok(groups.reorder(request.projectId(), id, request.planIds()));
    }

    /** 把组内顺序落成依赖边；成环返回 400 且错误文案带**计划名**路径。 */
    @PostMapping("/{id}/apply")
    public ApiResponse<PlanGroupView.ApplyResult> apply(@RequestParam long projectId,
                                                        @PathVariable long id) {
        return ApiResponse.ok(groups.applyChain(projectId, id));
    }

    public record GroupRequest(Long projectId, String name, String description) {

        PlanGroupService.GroupCommand toCommand() {
            if (projectId == null || projectId <= 0) {
                throw new IllegalArgumentException("projectId 不能为空");
            }
            return new PlanGroupService.GroupCommand(projectId, name, description);
        }
    }

    public record MembersRequest(long projectId, List<Long> planIds) {
    }
}
