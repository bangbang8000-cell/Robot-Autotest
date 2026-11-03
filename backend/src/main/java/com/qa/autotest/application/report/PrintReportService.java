package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ReportRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.ExportMode;
import com.qa.autotest.domain.report.MetadataGate;
import com.qa.autotest.domain.report.ReportRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 打印页编排（RPT-05）：发放一次性 token、按 token 返回与 Word 同源的数据。
 */
@Service
public class PrintReportService {

    private final ReportRepository reports;
    private final ReportTreeService trees;
    private final PrintTokenService tokens;
    private final ExportProperties properties;
    private final AccessControlService access;

    public PrintReportService(ReportRepository reports,
                              ReportTreeService trees,
                              PrintTokenService tokens,
                              ExportProperties properties,
                              AccessControlService access) {
        this.reports = reports;
        this.trees = trees;
        this.tokens = tokens;
        this.properties = properties;
        this.access = access;
    }

    /** 发放结果：token、过期时间与完整打印页 URL。 */
    public record IssuedPrintToken(String token, Instant expiresAt, String printUrl, String mode) {
    }

    public IssuedPrintToken issue(long projectId, String runId, String modeRaw) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        ExportMode mode = ExportMode.fromWire(modeRaw)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "mode 只能是 external 或 internal"));
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        ReportRecord report = reports.find(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "报告不存在: " + runId));
        if (mode == ExportMode.EXTERNAL && !report.exportable()) {
            List<String> missing = report.metadataMissing().isEmpty()
                    ? MetadataGate.check(report.metadata()).missing()
                    : report.metadataMissing();
            throw new ExportRejectedException(missing);
        }
        PrintTokenService.IssuedToken issued = tokens.issue(projectId, runId, mode);
        return new IssuedPrintToken(issued.token(), issued.expiresAt(),
                properties.printUrl(runId, issued.token()), mode.wireName());
    }

    /** 打印页取数；token 一次性，重复使用或过期都返回中文提示。 */
    public PrintReportView view(String runId, String token) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        PrintTokenService.TokenContext context = tokens.consume(token, runId);
        ReportRecord report = reports.find(context.projectId(), context.runId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "报告不存在: " + context.runId()));
        ExportMode mode = context.mode();
        return new PrintReportView(report.runId(), mode.wireName(), mode.needsInternalMark(),
                report.exportable(), report.metadataMissing(), report.metadata(),
                report.passCount(), report.failCount(), report.skipCount(),
                report.startedAt(), report.finishedAt(),
                trees.treeForPrint(context.projectId(), context.runId()));
    }
}
