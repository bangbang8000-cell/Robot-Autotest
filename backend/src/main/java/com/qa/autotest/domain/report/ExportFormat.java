package com.qa.autotest.domain.report;

import java.util.Locale;
import java.util.Optional;

/**
 * 报告导出格式（RPT-05）。
 *
 * <p>wire 值用于接口参数与 {@code export_record.export_format}，保持小写；
 * 扩展名与 Content-Type 供落盘与下载使用。
 */
public enum ExportFormat {

    /** PDF（无头浏览器打印）。 */
    PDF("pdf", "application/pdf"),
    /** Word（POI 直接生成 docx）。 */
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String wireName;
    private final String contentType;

    ExportFormat(String wireName, String contentType) {
        this.wireName = wireName;
        this.contentType = contentType;
    }

    public String wireName() {
        return wireName;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return wireName;
    }

    /** 解析接口参数；空值或未知值返回 empty，由调用方给出中文报错。 */
    public static Optional<ExportFormat> fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ExportFormat value : values()) {
            if (value.wireName.equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
