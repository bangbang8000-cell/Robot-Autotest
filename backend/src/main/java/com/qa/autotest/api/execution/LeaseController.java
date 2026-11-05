package com.qa.autotest.api.execution;

import com.qa.autotest.application.execution.LeaseAdminService;
import com.qa.autotest.application.execution.LeaseView;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ResourceLeaseRepository;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 租约只读视图（FR-v0.4-09）：看资源被谁占着，不提供申请/释放入口。 */
@RestController
@RequestMapping("/api/execution/leases")
public class LeaseController {

    private final ResourceLeaseRepository leases;
    private final AccessControlService access;
    private final LeaseAdminService admin;

    public LeaseController(ResourceLeaseRepository leases, AccessControlService access,
                           LeaseAdminService admin) {
        this.leases = leases;
        this.access = access;
        this.admin = admin;
    }

    @GetMapping
    public ApiResponse<List<LeaseView>> list(@RequestParam long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        return ApiResponse.ok(leases.listByProject(projectId));
    }

    /** 管理员强制释放（FR-v0.4-10）：confirm 必须等于占用该资源的 runId。 */
    @PostMapping("/force-release")
    public ApiResponse<LeaseAdminService.ForceReleaseResult> forceRelease(
            @RequestParam long projectId, @RequestParam String resourceKey,
            @RequestParam String confirm) {
        return ApiResponse.ok(admin.forceRelease(projectId, resourceKey, confirm));
    }
}
