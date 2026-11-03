package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ReportRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.ReportRecord;
import org.springframework.stereotype.Service;

/**
 * 报告查询（RPT-01）：详情与分页列表只读，门禁结论直接取入库时固化的 run.exportable / metadata_missing。
 */
@Service
public class ReportQueryService {

    private final ReportRepository reports;
    private final AccessControlService access;

    public ReportQueryService(ReportRepository reports, AccessControlService access) {
        this.reports = reports;
        this.access = access;
    }

    public ReportRecord detail(long projectId, String runId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        return reports.find(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告不存在: " + runId));
    }

    public PageResult<ReportRecord> page(long projectId, int page, int size) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法：page>=1 且 1<=size<=100");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        return reports.page(projectId, page, size);
    }
}
