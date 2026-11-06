package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportExportTest {

    private final ReportExportService service = new ReportExportService();

    private static Map<String, Object> view() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("title", "Quarterly Regression");
        v.put("conclusion", "PASS");
        v.put("passCount", 8);
        v.put("failCount", 0);
        v.put("watermark", ReportExportService.WATERMARK);
        return v;
    }

    @Test
    void htmlHasDoctypeAndWatermarkAndNoSensitiveText() {
        String html = new String(service.export("html", view()), StandardCharsets.UTF_8);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains(ReportExportService.WATERMARK));
        assertFalse(html.contains("10.202."));
    }

    @Test
    void pdfIsAValidMinimalDocument() {
        byte[] pdf = service.export("pdf", view());
        String head = new String(pdf, 0, 8, StandardCharsets.US_ASCII);
        assertEquals("%PDF-1.4", head);
        String all = new String(pdf, StandardCharsets.US_ASCII);
        assertTrue(all.contains("%%EOF"));
        assertTrue(all.contains("startxref"));
        assertTrue(all.contains("/Type /Catalog"));
    }

    @Test
    void docxIsAZipContainer() {
        byte[] docx = service.export("docx", view());
        assertTrue(docx.length > 100);
        assertEquals('P', docx[0]);
        assertEquals('K', docx[1]);
    }

    @Test
    void unknownFormatRejectedAndMetadataHelpersAgree() {
        assertThrows(IllegalArgumentException.class, () -> service.export("xlsx", view()));
        assertEquals("pdf", ReportExportService.extension("PDF"));
        assertEquals("application/pdf", ReportExportService.contentType("pdf"));
        assertTrue(ReportExportService.contentType("docx").contains("wordprocessingml"));
    }
}
