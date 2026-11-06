package com.qa.autotest.application.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

/**
 * 三格式导出（RPTX-05）。
 *
 * <p>HTML 走 C5 白名单视图 + 水印；DOCX 用已有 POI 依赖；PDF 不引第三方库，
 * 手写最小 PDF 1.4（Helvetica / ASCII 文本层）——中文正文走 HTML 与 DOCX，
 * PDF 文本层只放 ASCII 字段摘要，保证敏感字段扫描器能读到同一份内容。
 */
@Service
public class ReportExportService {

    public static final String WATERMARK = "INTERNAL USE ONLY";

    public byte[] export(String format, Map<String, Object> view) {
        String kind = format == null ? "html" : format.trim().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "html" -> html(view).getBytes(StandardCharsets.UTF_8);
            case "pdf" -> pdf(view);
            case "docx" -> docx(view);
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    public static String extension(String format) {
        String kind = format == null ? "html" : format.trim().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "pdf" -> "pdf";
            case "docx" -> "docx";
            case "html" -> "html";
            default -> throw new IllegalArgumentException("不支持的导出格式: " + format);
        };
    }

    public static String contentType(String format) {
        return switch (extension(format)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "text/html;charset=UTF-8";
        };
    }

    private static String html(Map<String, Object> view) {
        StringBuilder b = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>Test Report</title></head><body>");
        b.append("<h1>").append(ReportViewRenderer.escape(String.valueOf(view.get("title"))))
                .append("</h1><p class=\"wm\">")
                .append(ReportViewRenderer.escape(WATERMARK)).append("</p><table>");
        for (Map.Entry<String, Object> e : view.entrySet()) {
            b.append("<tr><th>").append(ReportViewRenderer.escape(e.getKey()))
                    .append("</th><td>")
                    .append(ReportViewRenderer.escape(String.valueOf(e.getValue())))
                    .append("</td></tr>");
        }
        return b.append("</table></body></html>").toString();
    }

    private static List<String> lines(Map<String, Object> view) {
        List<String> result = new ArrayList<>();
        result.add("Test Report / " + WATERMARK);
        for (Map.Entry<String, Object> e : view.entrySet()) {
            result.add(e.getKey() + ": " + String.valueOf(e.getValue()));
        }
        return result;
    }

    private static String ascii(String text) {
        StringBuilder b = new StringBuilder();
        for (char c : text.toCharArray()) {
            b.append(c >= 32 && c < 127 ? c : '?');
        }
        return b.toString();
    }

    private static String pdfEscape(String text) {
        return ascii(text).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] pdf(Map<String, Object> view) {
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n50 790 Td\n15 TL\n");
        for (String line : lines(view)) {
            content.append('(').append(pdfEscape(line)).append(") Tj\nT*\n");
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] offsets = new int[6];
        write(out, "%PDF-1.4\n");
        obj(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        obj(out, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        obj(out, offsets, 3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        obj(out, offsets, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        offsets[5] = out.size();
        write(out, "5 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
        out.write(stream, 0, stream.length);
        write(out, "endstream\nendobj\n");
        int startXref = out.size();
        write(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i < 6; i++) {
            write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }
        write(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
                + startXref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void obj(ByteArrayOutputStream out, int[] offsets, int number, String body) {
        offsets[number] = out.size();
        write(out, number + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] docx(Map<String, Object> view) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setText("Test Report / " + WATERMARK);
            for (String line : lines(view)) {
                XWPFParagraph paragraph = doc.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("DOCX 导出失败", e);
        }
    }
}
