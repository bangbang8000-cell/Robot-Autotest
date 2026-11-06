package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReportExportTest {

    private final ReportExportService service = new ReportExportService();

    private static Map<String, Object> view() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("title", "Quarterly Regression");
        v.put("conclusion", "PASS");
        v.put("passCount", 8);
        v.put("failCount", 0);
        v.put("passRate", 88.89);
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
    void xlsxIsAZipContainerWithNumericCellsAsNumbers() throws Exception {
        byte[] xlsx = service.export("xlsx", view());
        assertEquals('P', xlsx[0]);
        assertEquals('K', xlsx[1]);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("FIELD_KEY", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("TITLE", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("VALUE", sheet.getRow(0).getCell(2).getStringCellValue());

            Map<String, Cell> values = new LinkedHashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                values.put(row.getCell(0).getStringCellValue(), row.getCell(2));
            }
            // 计数与通过率必须是数字型：拼成文本后 Excel 求和/排序/条件格式全部失效
            assertEquals(CellType.NUMERIC, values.get("passCount").getCellType());
            assertEquals(8.0, values.get("passCount").getNumericCellValue(), 1e-9);
            assertEquals(CellType.NUMERIC, values.get("passRate").getCellType());
            assertEquals(88.89, values.get("passRate").getNumericCellValue(), 1e-9);
            // 文本字段仍要走字符串，不能被误当数字
            assertEquals(CellType.STRING, values.get("conclusion").getCellType());
        }
    }

    @Test
    void xlsxUsesTemplateTitlesAndFallsBackToKey() throws Exception {
        byte[] xlsx = service.export("xlsx", view(), Map.of("passRate", "通过率"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, String> titles = new LinkedHashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                titles.put(row.getCell(0).getStringCellValue(), row.getCell(1).getStringCellValue());
            }
            assertEquals("通过率", titles.get("passRate"));
            // 没配标题的字段回落到 key，不能变成空标题
            assertEquals("passCount", titles.get("passCount"));
        }
    }

    @Test
    void structuredValuesAreNotDroppedInXlsx() throws Exception {
        Map<String, Object> v = view();
        v.put("failureDistribution", Map.of("fail", 0));
        v.put("trend", java.util.List.of());
        byte[] xlsx = service.export("xlsx", v);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            String text = null;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if ("failureDistribution".equals(sheet.getRow(i).getCell(0).getStringCellValue())) {
                    text = sheet.getRow(i).getCell(2).getStringCellValue();
                }
            }
            assertEquals("{\"fail\":0}", text);
        }
    }

    @Test
    void unknownFormatRejectedAndMetadataHelpersAgree() {
        assertThrows(IllegalArgumentException.class, () -> service.export("csv", view()));
        assertEquals("pdf", ReportExportService.extension("PDF"));
        assertEquals("application/pdf", ReportExportService.contentType("pdf"));
        assertTrue(ReportExportService.contentType("docx").contains("wordprocessingml"));
        assertTrue(ReportExportService.contentType("xlsx").contains("spreadsheetml"));
        assertEquals("xlsx", ReportExportService.extension("XLSX"));
    }
}
