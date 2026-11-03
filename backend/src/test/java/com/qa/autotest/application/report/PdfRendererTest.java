package com.qa.autotest.application.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * 无头浏览器 PDF 打印（RPT-05）：找不到、命令拼装、超时、假脚本成功。
 */
class PdfRendererTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("找不到浏览器 → 503 + 中文原因，不产出假文件")
    void missingBrowserFailsWith503() {
        ExportProperties props = props(90);
        PdfRenderer renderer = new PdfRenderer(props,
                List.of(tempDir.resolve("no-such-chrome.exe").toString()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> renderer.render("http://127.0.0.1:5173/reports/RUN/print?token=t"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, ex.errorCode());
        assertTrue(ex.getMessage().contains("未找到可用的无头浏览器"));
    }

    @Test
    @DisplayName("命令拼装正确：headless + print-to-pdf + URL")
    void commandIsAssembledCorrectly() {
        Path pdf = tempDir.resolve("out.pdf");
        List<String> command = PdfRenderer.buildCommand(
                "C:\\Chrome\\chrome.exe", "http://127.0.0.1:5173/reports/RUN/print?token=t", pdf);

        assertEquals("C:\\Chrome\\chrome.exe", command.get(0));
        assertTrue(command.contains("--headless=new"));
        assertTrue(command.contains("--disable-gpu"));
        assertTrue(command.contains("--no-pdf-header-footer"));
        assertTrue(command.contains("--print-to-pdf=" + pdf.toAbsolutePath()));
        assertEquals("http://127.0.0.1:5173/reports/RUN/print?token=t",
                command.get(command.size() - 1));
    }

    @Test
    @DisplayName("超时被杀并报中文原因")
    void timeoutIsKilledWithChineseMessage() throws IOException {
        Path fake = tempDir.resolve("slow-browser.cmd");
        Files.writeString(fake, "@echo off\r\nping -n 20 127.0.0.1 > nul\r\n",
                StandardCharsets.UTF_8);
        PdfRenderer renderer = new PdfRenderer(props(1), List.of(fake.toString()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> renderer.render("http://127.0.0.1:5173/reports/RUN/print?token=t"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, ex.errorCode());
        assertTrue(ex.getMessage().contains("PDF 打印超时"), ex.getMessage());
        assertTrue(ex.getMessage().contains("终止浏览器进程"));
    }

    @Test
    @DisplayName("假浏览器脚本成功产出 PDF 时原样返回字节")
    @EnabledOnOs(OS.WINDOWS)
    void fakeBrowserProducesPdf() throws IOException {
        Path fixture = tempDir.resolve("fixture.pdf");
        Files.write(fixture, "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
        Path fake = tempDir.resolve("ok-browser.cmd");
        Files.writeString(fake, """
                @echo off
                setlocal
                set "S=%*"
                set "R=%S:*--print-to-pdf=%"
                for /f "tokens=1" %%P in ("%R%") do set "T=%%P"
                set "P=%T:~1%"
                copy /Y "%~dp0fixture.pdf" "%P%" > nul
                """, StandardCharsets.UTF_8);

        PdfRenderer renderer = new PdfRenderer(props(10), List.of(fake.toString()));
        byte[] bytes = renderer.render("http://127.0.0.1:5173/reports/RUN/print?token=t");

        assertNotNull(bytes);
        assertArrayEquals(Files.readAllBytes(fixture), bytes);
    }

    private static ExportProperties props(int timeoutSeconds) {
        ExportProperties props = new ExportProperties();
        props.setTimeoutSeconds(timeoutSeconds);
        props.setPrintBaseUrl("http://127.0.0.1:5173");
        return props;
    }
}
