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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        Path fake = fakeBrowser("slow-browser",
                "@echo off\r\nping -n 20 127.0.0.1 > nul\r\n",
                "sleep 20");
        PdfRenderer renderer = new PdfRenderer(props(1), List.of(fake.toString()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> renderer.render("http://127.0.0.1:5173/reports/RUN/print?token=t"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, ex.errorCode());
        assertTrue(ex.getMessage().contains("PDF 打印超时"), ex.getMessage());
        assertTrue(ex.getMessage().contains("终止浏览器进程"));
    }

    @Test
    @DisplayName("假浏览器脚本成功产出 PDF 时原样返回字节")
    void fakeBrowserProducesPdf() throws IOException {
        Path fixture = tempDir.resolve("fixture.pdf");
        Files.write(fixture, "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
        String windowsScript = """
                @echo off
                setlocal
                set "S=%*"
                set "R=%S:*--print-to-pdf=%"
                for /f "tokens=1" %%P in ("%R%") do set "T=%%P"
                set "P=%T:~1%"
                copy /Y "%~dp0fixture.pdf" "%P%" > nul
                """;
        String posixScript = "out=\"\"\n"
                + "for a in \"$@\"; do\n"
                + "  case \"$a\" in --print-to-pdf=*) out=\"${a#--print-to-pdf=}\" ;; esac\n"
                + "done\n"
                + "cp \"" + fixture.toAbsolutePath() + "\" \"$out\"";
        Path fake = fakeBrowser("ok-browser", windowsScript, posixScript);

        PdfRenderer renderer = new PdfRenderer(props(10), List.of(fake.toString()));
        byte[] bytes = renderer.render("http://127.0.0.1:5173/reports/RUN/print?token=t");

        assertNotNull(bytes);
        assertArrayEquals(Files.readAllBytes(fixture), bytes);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * 造一个「假浏览器」脚本：Windows 写 .cmd，POSIX 写带 shebang 的 .sh 并 chmod 700。
     *
     * <p>原先这几条用例只会写 .cmd，在 Linux 上必然 {@code Permission denied} 而红 ——
     * 而 Linux 正是本项目的部署平台（Ubuntu 服务器）。加 {@code @EnabledOnOs(WINDOWS)} 跳过
     * 只是把红改成「不报」，等于「超时杀浏览器」这条路径在 Linux 上永远没验过。这里改成两侧
     * 都能真跑：{@link PdfRenderer#buildCommand} 把可执行文件放参数首位，POSIX 下带 shebang
     * 的脚本可直接被 ProcessBuilder 执行。
     */
    private Path fakeBrowser(String name, String windowsBody, String posixBody) throws IOException {
        boolean windows = isWindows();
        Path script = tempDir.resolve(name + (windows ? ".cmd" : ".sh"));
        if (windows) {
            Files.writeString(script, windowsBody, StandardCharsets.UTF_8);
        } else {
            Files.writeString(script, "#!/bin/sh\n" + posixBody + "\n", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        }
        return script;
    }

    private static ExportProperties props(int timeoutSeconds) {
        ExportProperties props = new ExportProperties();
        props.setTimeoutSeconds(timeoutSeconds);
        props.setPrintBaseUrl("http://127.0.0.1:5173");
        return props;
    }
}
