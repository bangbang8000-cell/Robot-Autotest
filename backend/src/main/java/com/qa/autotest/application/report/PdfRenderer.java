package com.qa.autotest.application.report;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 无头浏览器 PDF 打印（RPT-05）。
 *
 * <p>可执行文件按顺序探测：环境变量 {@code REPORT_PDF_CHROME} → 配置
 * {@code report.export.chrome-executable} → Windows Chrome → Windows Edge →
 * {@code google-chrome} → {@code chromium}。找不到或打印失败一律抛
 * {@link ErrorCode#DEPENDENCY_UNAVAILABLE}（HTTP 503），<b>绝不返回伪造的 PDF</b>。
 */
@Service
public class PdfRenderer {

    /** 打印超时上限（秒）。 */
    public static final int DEFAULT_TIMEOUT_SECONDS = 90;

    private static final java.util.List<String> WINDOWS_CHROME = java.util.List.of(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");
    private static final java.util.List<String> WINDOWS_EDGE = java.util.List.of(
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");
    private static final java.util.List<String> UNIX_BROWSERS = java.util.List.of(
            "google-chrome", "chromium", "chromium-browser");

    private final ExportProperties properties;
    private final List<String> candidates;
    private final Duration timeout;

    @Autowired
    public PdfRenderer(ExportProperties properties) {
        this(properties, defaultCandidates(properties));
    }

    /** 测试注入：显式候选列表与配置共用同一个超时。 */
    PdfRenderer(ExportProperties properties, List<String> candidates) {
        this.properties = properties;
        this.candidates = List.copyOf(candidates);
        int seconds = properties.getTimeoutSeconds() > 0
                ? properties.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        this.timeout = Duration.ofSeconds(seconds);
    }

    /**
     * 打印 URL 为 PDF 并返回字节。
     *
     * @throws BusinessException 浏览器缺失 / 超时 / 退出码非 0 / 产物不是合法 PDF（503）
     */
    public byte[] render(String url) {
        String executable = resolveExecutable();
        if (executable == null) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "未找到可用的无头浏览器：请安装 Chrome/Edge，或设置环境变量 REPORT_PDF_CHROME");
        }
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("rpt05-pdf-");
            Path pdf = workDir.resolve("out.pdf");
            Path log = workDir.resolve("browser.log");
            List<String> command = buildCommand(executable, url, pdf);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "PDF 打印超时（" + timeout.toSeconds() + " 秒），已终止浏览器进程");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "PDF 打印失败：无头浏览器退出码 " + exitCode + "，日志：" + tail(log));
            }
            if (!Files.isRegularFile(pdf)) {
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "PDF 打印失败：浏览器未生成文件，日志：" + tail(log));
            }
            byte[] bytes = Files.readAllBytes(pdf);
            if (!isPdf(bytes)) {
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "PDF 打印失败：产物不是合法 PDF");
            }
            return bytes;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "PDF 打印失败：无法启动无头浏览器（" + ex.getMessage() + "）");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "PDF 打印被中断，未生成可用产物");
        } finally {
            deleteQuietly(workDir);
        }
    }

    /** 命令拼装（测试直接断言参数顺序与关键开关）。 */
    public static List<String> buildCommand(String executable, String url, Path pdfFile) {
        return List.of(
                executable,
                "--headless=new",
                "--disable-gpu",
                "--no-pdf-header-footer",
                "--virtual-time-budget=15000",
                "--print-to-pdf=" + pdfFile.toAbsolutePath(),
                url);
    }

    /** 探测可执行文件；找不到返回 null，由调用方给出 503 中文原因。 */
    String resolveExecutable() {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String trimmed = candidate.trim();
            Path path = Path.of(trimmed);
            if (path.isAbsolute()) {
                if (Files.isRegularFile(path)) {
                    return trimmed;
                }
                // 绝对路径不存在就是不存在，不能再当成 PATH 上的命令名拼接
                continue;
            }
            if (Files.isRegularFile(path)) {
                return trimmed;
            }
            String onPath = findOnPath(trimmed);
            if (onPath != null) {
                return onPath;
            }
        }
        return null;
    }

    private static List<String> defaultCandidates(ExportProperties properties) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String fromEnv = System.getenv("REPORT_PDF_CHROME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            values.add(fromEnv);
        }
        if (properties.getChromeExecutable() != null && !properties.getChromeExecutable().isBlank()) {
            values.add(properties.getChromeExecutable());
        }
        values.addAll(WINDOWS_CHROME);
        values.addAll(WINDOWS_EDGE);
        values.addAll(UNIX_BROWSERS);
        return new ArrayList<>(values);
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        List<String> extensions = new ArrayList<>();
        String pathExt = System.getenv("PATHEXT");
        if (pathExt != null && !pathExt.isBlank()) {
            for (String ext : pathExt.split(";")) {
                if (!ext.isBlank()) {
                    extensions.add(ext.trim());
                }
            }
        } else {
            extensions.add("");
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            for (String ext : extensions) {
                Path candidate = Path.of(directory, name + ext);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
            // 无扩展名的 Unix 可执行文件
            Path bare = Path.of(directory, name);
            if (Files.isRegularFile(bare)) {
                return bare.toString();
            }
        }
        return null;
    }

    private static boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return false;
        }
        String head = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        return "%PDF-".equals(head);
    }

    private static String tail(Path log) {
        try {
            if (!Files.isRegularFile(log)) {
                return "（无日志）";
            }
            String text = Files.readString(log, StandardCharsets.UTF_8);
            int from = Math.max(0, text.length() - 300);
            return text.substring(from).replaceAll("\\s+", " ").trim();
        } catch (IOException ex) {
            return "（日志读取失败）";
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响导出结果
                }
            });
        } catch (IOException ignored) {
            // 临时目录清理失败不影响导出结果
        }
    }
}
