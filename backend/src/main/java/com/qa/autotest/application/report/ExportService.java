package com.qa.autotest.application.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.application.execution.ExecutionPaths;
import com.qa.autotest.application.execution.RunArtifact;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.ExportRecordRepository;
import com.qa.autotest.application.repository.ReportRepository;
import com.qa.autotest.application.repository.RunArtifactRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.ExportFormat;
import com.qa.autotest.domain.report.ExportMode;
import com.qa.autotest.domain.report.ExportRecord;
import com.qa.autotest.domain.report.GateResult;
import com.qa.autotest.domain.report.MetadataGate;
import com.qa.autotest.domain.report.ReportRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 报告导出（RPT-05）。
 *
 * <p>门禁口径：{@code mode=external} 且 {@code exportable=false} → 400 + 缺失键数组；
 * {@code mode=internal} 允许缺项，但 Word 页眉 / PDF 打印页都带红色内部标识。
 * 产物落到该 run 的产物目录（run.artifacts_dir 或 run_artifact 同级目录），
 * 同时写 export_record 与 audit_log，绝不返回没有落盘路径的成功响应。
 */
@Service
public class ExportService {

    private final ReportRepository reports;
    private final ReportTreeService trees;
    private final RunRepository runs;
    private final RunArtifactRepository artifacts;
    private final ExportRecordRepository exportRecords;
    private final DocxExporter docxExporter;
    private final PdfExportService pdfExportService;
    private final AccessControlService access;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExportService(ReportRepository reports,
                         ReportTreeService trees,
                         RunRepository runs,
                         RunArtifactRepository artifacts,
                         ExportRecordRepository exportRecords,
                         DocxExporter docxExporter,
                         PdfExportService pdfExportService,
                         AccessControlService access,
                         AuditService audit,
                         ObjectMapper objectMapper,
                         Clock clock) {
        this.reports = reports;
        this.trees = trees;
        this.runs = runs;
        this.artifacts = artifacts;
        this.exportRecords = exportRecords;
        this.docxExporter = docxExporter;
        this.pdfExportService = pdfExportService;
        this.access = access;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 导出结果：与接口返回字段一一对应。 */
    public record ExportResult(long exportId,
                               String format,
                               String mode,
                               long bytes,
                               String sha256,
                               String downloadUrl) {
    }

    /** 下载定位结果。 */
    public record ExportDownload(Path path, String fileName, String contentType) {
    }

    public ExportResult export(long projectId, String runId, String modeRaw, String formatRaw) {
        validate(projectId, runId);
        ExportMode mode = ExportMode.fromWire(modeRaw)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "mode 只能是 external 或 internal"));
        ExportFormat format = ExportFormat.fromWire(formatRaw)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "format 只能是 pdf 或 docx"));

        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        ReportRecord report = reports.find(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "报告不存在: " + runId));
        // 导出期二次拒绝（RPT-07）：按当前 metadata 重算门禁，不依赖 run.exportable 列的旧结论，
        // 保证「入库时算过」与「导出时再算一次」是同一条口径，库列被改错也放不过去。
        GateResult gate = MetadataGate.check(report.metadata());
        if (mode == ExportMode.EXTERNAL && !gate.exportable()) {
            List<String> missing = gate.missing();
            if (missing.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "外部版导出被拒绝：metadata 存在超长项，请先修正");
            }
            throw new ExportRejectedException(missing);
        }

        Path directory = resolveArtifactDirectory(projectId, runId);
        String fileName = "report_" + safeName(runId) + "_" + mode.wireName()
                + "." + format.extension();
        Instant exportedAt = clock.instant();
        byte[] bytes;
        if (format == ExportFormat.PDF) {
            bytes = pdfExportService.render(projectId, runId, mode);
        } else {
            bytes = docxExporter.render(new ExportDocument(report,
                    trees.treeForPrint(projectId, runId), mode, exportedAt, operator()));
        }
        Path target = directory.resolve(fileName);
        try {
            Files.createDirectories(directory);
            Files.write(target, bytes, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "导出文件落盘失败：" + ex.getMessage());
        }

        String sha256 = sha256(bytes);
        long exportId = exportRecords.insert(projectId, runId, format, mode, fileName,
                target.toAbsolutePath().toString(), sha256, bytes.length,
                TenantContext.userId(), "created");
        String downloadUrl = "/api/exports/" + exportId + "/download?projectId=" + projectId;
        audit.record("report_export", "report", runId, auditDetail(runId, format, mode, sha256));
        return new ExportResult(exportId, format.wireName(), mode.wireName(),
                bytes.length, sha256, downloadUrl);
    }

    public List<ExportRecord> history(long projectId, String runId) {
        validate(projectId, runId);
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        return exportRecords.list(projectId, runId);
    }

    public ExportDownload download(long projectId, long exportId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (exportId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "exportId 非法");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        ExportRecord record = exportRecords.find(projectId, exportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "导出记录不存在或不属于该项目"));
        if (record.filePath() == null || record.filePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出记录没有产物路径，请重新导出");
        }
        Path path = Path.of(record.filePath());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "导出文件已不存在（可能已被清理），请重新导出");
        }
        String fileName = record.fileName() == null || record.fileName().isBlank()
                ? "report_" + safeName(record.runId()) + "_" + record.mode().wireName()
                + "." + record.format().extension()
                : record.fileName();
        return new ExportDownload(path, fileName, record.format().contentType());
    }

    /**
     * 解析产物目录：优先 run.artifacts_dir，其次已登记的 run_artifact 同级目录。
     * 不硬编码任何项目路径。
     */
    private Path resolveArtifactDirectory(long projectId, String runId) {
        RunRecord run = runs.findByRunId(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "运行不存在: " + runId));
        if (run.artifactsDir() != null && !run.artifactsDir().isBlank()) {
            return ExecutionPaths.resolve(run.artifactsDir());
        }
        for (RunArtifact artifact : artifacts.list(projectId, run.id())) {
            if (artifact.path() != null && !artifact.path().isBlank()) {
                Path parent = Path.of(artifact.path()).toAbsolutePath().getParent();
                if (parent != null) {
                    return parent;
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                "该运行尚未登记产物目录，无法导出（请先完成一次执行或解析报告）");
    }

    private String auditDetail(String runId, ExportFormat format, ExportMode mode, String sha256) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("runId", runId);
        detail.put("format", format.wireName());
        detail.put("mode", mode.wireName());
        detail.put("sha256", sha256);
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            return "{\"runId\":\"" + runId + "\"}";
        }
    }

    private static String operator() {
        String username = TenantContext.username();
        return username == null || username.isBlank() ? "unknown" : username;
    }

    private static String safeName(String runId) {
        return runId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }

    private static void validate(long projectId, String runId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
    }
}
