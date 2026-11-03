package com.qa.autotest.api.iam;

import com.qa.autotest.application.iam.AuditQuery;
import com.qa.autotest.application.iam.AuditQueryService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.AuditEntry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询（HRD-02 / FR-v1.0-06）。
 *
 * <p>只有两个只读接口：分页查询与 CSV 导出。刻意不提供 DELETE / PUT，审计只增不改。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditQueryService audits;

    public AuditController(AuditQueryService audits) {
        this.audits = audits;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditEntry>> page(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(audits.page(new AuditQuery(projectId, action,
                parse(from, false), parse(to, true), page, size)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = audits.exportCsv(new AuditQuery(projectId, action,
                parse(from, false), parse(to, true), 1, 20));
        String fileName = "audit-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZONE).format(Instant.now()) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 兼容三种时间格式：ISO-8601（带 Z）、{@code yyyy-MM-dd HH:mm:ss}、{@code yyyy-MM-dd}。
     * 日期粒度的 {@code to} 取当天末刻，避免「到某天」漏掉当天记录。
     */
    static Instant parse(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // 继续尝试本地时间格式
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME).atZone(ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试日期格式
        }
        try {
            LocalDate date = LocalDate.parse(text);
            LocalTime time = endOfDay ? LocalTime.of(23, 59, 59, 999_000_000) : LocalTime.MIN;
            return date.atTime(time).atZone(ZONE).toInstant();
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "时间格式非法，应为 ISO-8601 或 yyyy-MM-dd HH:mm:ss");
        }
    }
}
