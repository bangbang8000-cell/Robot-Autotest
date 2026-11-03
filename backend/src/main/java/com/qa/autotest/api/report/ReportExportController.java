package com.qa.autotest.api.report;

import com.qa.autotest.application.report.ExportService;
import com.qa.autotest.application.report.PrintReportService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.report.ExportRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告导出接口（RPT-05）。
 *
 * <p>鉴权由 {@code AuthInterceptor} 负责；项目级校验在服务层 {@code bindProject} 完成。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private final ExportService exports;
    private final PrintReportService prints;

    public ReportExportController(ExportService exports, PrintReportService prints) {
        this.exports = exports;
        this.prints = prints;
    }

    /**
     * 导出报告。
     *
     * <p>{@code mode=external} 且报告不可对外时返回 400 + {@code metadataMissing}；
     * {@code mode=internal} 允许缺项，产物带红色内部标识。
     */
    @PostMapping("/{runId}/export")
    public ApiResponse<ExportService.ExportResult> export(
            @PathVariable String runId,
            @RequestParam long projectId,
            @RequestParam(defaultValue = "external") String mode,
            @RequestParam(defaultValue = "pdf") String format) {
        return ApiResponse.ok(exports.export(projectId, runId, mode, format));
    }

    /** 导出历史（按导出时间倒序）。 */
    @GetMapping("/{runId}/exports")
    public ApiResponse<List<ExportHistoryView>> history(@PathVariable String runId,
                                                        @RequestParam long projectId) {
        return ApiResponse.ok(exports.history(projectId, runId).stream()
                .map(ExportHistoryView::from).toList());
    }

    /** 发放打印页一次性 token（10 分钟、仅可使用一次）。 */
    @PostMapping("/{runId}/print-token")
    public ApiResponse<PrintReportService.IssuedPrintToken> printToken(
            @PathVariable String runId,
            @RequestParam long projectId,
            @RequestParam(defaultValue = "external") String mode) {
        return ApiResponse.ok(prints.issue(projectId, runId, mode));
    }

    /** 导出历史行。 */
    public record ExportHistoryView(long exportId,
                                    String runId,
                                    String format,
                                    String mode,
                                    String fileName,
                                    long bytes,
                                    String sha256,
                                    String status,
                                    Long requestedBy,
                                    Instant createdAt) {

        static ExportHistoryView from(ExportRecord record) {
            return new ExportHistoryView(record.id(), record.runId(),
                    record.format().wireName(), record.mode().wireName(),
                    record.fileName(), record.sizeBytes(), record.sha256(),
                    record.status(), record.requestedBy(), record.createdAt());
        }
    }
}
