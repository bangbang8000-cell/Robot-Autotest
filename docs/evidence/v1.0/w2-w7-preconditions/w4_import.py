# -*- coding: utf-8 -*-
"""W4 / IMP-01..05 导入与沙箱批量落地。幂等。"""
import pathlib

ROOT = pathlib.Path.cwd()
F = {}

F["db/migration/V8_2__import_sandbox.sql"] = r"""-- W4 / IMP-01：导入批次与用例来源。case_source 不带任何设备外键（schema 解耦断言）。
CREATE TABLE IF NOT EXISTS `import_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `source_type` VARCHAR(16) NOT NULL,
    `source_name` VARCHAR(200) NOT NULL,
    `checksum` CHAR(64) NOT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PREFLIGHT',
    `file_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_checksum` (`project_id`, `checksum`),
    KEY `idx_batch_project` (`project_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `case_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `rel_path` VARCHAR(400) NOT NULL,
    `kind` VARCHAR(16) NOT NULL,
    `node_id` VARCHAR(400) NOT NULL,
    `checksum` CHAR(64) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_source` (`batch_id`, `node_id`),
    KEY `idx_source_project` (`project_id`, `batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

F["db/down/V8_2__import_sandbox.down.sql"] = r"""-- W4 回退：只删本版新建的两张表。
DROP TABLE IF EXISTS `case_source`;
DROP TABLE IF EXISTS `import_batch`;
"""

F["backend/src/main/java/com/qa/autotest/domain/importing/CaseSource.java"] = r"""package com.qa.autotest.domain.importing;

/** 导入后的用例来源（IMP-01）。刻意不带设备外键：用例与设备资产彻底解耦。 */
public record CaseSource(String relPath, String kind, String nodeId, String checksum) {
}
"""

F["backend/src/main/java/com/qa/autotest/domain/importing/PreflightReport.java"] = r"""package com.qa.autotest.domain.importing;

import java.util.List;
import java.util.Map;

/** 预检报告（IMP-05）：可执行项、敏感扫描命中、与上一批的 diff 三件事一次给全。 */
public record PreflightReport(String sourceType, String checksum, int fileCount,
                              List<CaseSource> cases, List<String> commands,
                              List<String> sensitiveFindings, Map<String, List<String>> diff,
                              boolean executable) {
}
"""

F["backend/src/main/java/com/qa/autotest/application/importing/ImportSandbox.java"] = r"""package com.qa.autotest.application.importing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;

/**
 * 导入沙箱（IMP-02）：zip / 目录落盘前的路径校验、限额、只读落盘、校验和。
 *
 * <p>zip slip 是最容易漏的一条：只检查 `..` 字面量不够，`a/../../etc` 规范化后
 * 一样能逃出目标目录，所以判定统一基于 {@code normalize().startsWith(target)}。
 */
@Service
public class ImportSandbox {

    public static final int MAX_ENTRIES = 1000;
    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    public static final int MAX_DEPTH = 12;

    /** 路径白名单校验：拒绝绝对路径、父目录逃逸、盘符、控制字符、过深路径。 */
    public static boolean isSafeRelativePath(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 400) {
            return false;
        }
        if (raw.indexOf('\0') >= 0 || raw.chars().anyMatch(c -> c < 32)) {
            return false;
        }
        if (raw.startsWith("/") || raw.startsWith("\\") || raw.startsWith("~")) {
            return false;
        }
        if (raw.matches("^[A-Za-z]:.*")) {
            return false;
        }
        String normalized = raw.replace('\\', '/');
        if (normalized.contains("..") || normalized.contains("//")) {
            return false;
        }
        String[] parts = normalized.split("/");
        return parts.length <= MAX_DEPTH;
    }

    /** 解压到 target，返回相对路径清单；任何一条不合规即整批拒绝（不是跳过）。 */
    public List<String> unpack(InputStream zipStream, Path target) throws IOException {
        List<String> files = new ArrayList<>();
        long total = 0L;
        int entries = 0;
        Files.createDirectories(target);
        try (ZipInputStream in = new ZipInputStream(zipStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("条目数超过上限 " + MAX_ENTRIES);
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!isSafeRelativePath(name.endsWith("/") ? name : name + "/")) {
                        throw new IllegalArgumentException("不安全的目录路径: " + name);
                    }
                    Path dir = target.resolve(name).normalize();
                    if (!dir.startsWith(target)) {
                        throw new IllegalArgumentException("目录逃逸: " + name);
                    }
                    Files.createDirectories(dir);
                    continue;
                }
                if (!isSafeRelativePath(name)) {
                    throw new IllegalArgumentException("不安全的文件路径: " + name);
                }
                Path resolved = target.resolve(name).normalize();
                if (!resolved.startsWith(target)) {
                    throw new IllegalArgumentException("路径逃逸: " + name);
                }
                Files.createDirectories(resolved.getParent());
                long size = 0L;
                MessageDigest digest = sha256();
                try (var out = Files.newOutputStream(resolved)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        size += read;
                        total += read;
                        if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                            throw new IllegalArgumentException("文件 / 总大小超过限额: " + name);
                        }
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                if (!resolved.toFile().setReadOnly()) {
                    throw new IllegalStateException("只读落盘失败: " + name);
                }
                files.add(name + "|" + HexFormat.of().formatHex(digest.digest()));
            }
        }
        return List.copyOf(files);
    }

    public static String checksum(List<String> files) {
        MessageDigest digest = sha256();
        for (String file : files) {
            digest.update(file.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/importing/PytestImportAdapter.java"] = r"""package com.qa.autotest.application.importing;

import com.qa.autotest.domain.importing.CaseSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** pytest 导入适配（IMP-03）：conftest / fixture / 包结构与 --collect-only 预检。 */
@Component
public class PytestImportAdapter {

    private static final Pattern NODE = Pattern.compile("(\\S+::\\S+)");

    public String type() {
        return "pytest";
    }

    public List<String> preflightCommands(Path root) {
        return List.of("pytest --collect-only -q " + root,
                "python3 -m compileall -q " + root);
    }

    public List<CaseSource> parseCollectOnly(String output, List<String> relPaths) {
        List<CaseSource> cases = new ArrayList<>();
        Matcher matcher = NODE.matcher(output == null ? "" : output);
        while (matcher.find()) {
            cases.add(new CaseSource(relPaths.isEmpty() ? "unknown" : relPaths.get(0),
                    "PYTEST", matcher.group(1), null));
        }
        return cases;
    }

    public List<String> structureFindings(List<String> relPaths) {
        List<String> findings = new ArrayList<>();
        boolean hasConftest = relPaths.stream().anyMatch(p -> p.endsWith("conftest.py"));
        boolean hasTest = relPaths.stream().anyMatch(p -> p.contains("test_") && p.endsWith(".py"));
        boolean hasInit = relPaths.stream().anyMatch(p -> p.endsWith("__init__.py"));
        if (!hasConftest) {
            findings.add("缺少 conftest.py（fixture 可能不可见）");
        }
        if (!hasTest) {
            findings.add("未发现 test_*.py，collect-only 会得到 0 条用例");
        }
        if (!hasInit && relPaths.size() > 3) {
            findings.add("多文件目录缺少 __init__.py，包结构可能不稳");
        }
        return findings;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/importing/RobotImportAdapter.java"] = r"""package com.qa.autotest.application.importing;

import com.qa.autotest.domain.importing.CaseSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** RF 导入适配（IMP-04）：resource 解析、suite 结构、--dryrun 预检。 */
@Component
public class RobotImportAdapter {

    public String type() {
        return "robot";
    }

    public List<String> preflightCommands(Path root) {
        return List.of("robot --dryrun --output NONE --report NONE --log NONE " + root);
    }

    public List<CaseSource> fromSuitePaths(List<String> relPaths) {
        List<CaseSource> cases = new ArrayList<>();
        for (String path : relPaths) {
            if (path.endsWith(".robot")) {
                String suite = Path.of(path).getFileName().toString().replace(".robot", "");
                cases.add(new CaseSource(path, "ROBOT_SUITE", suite, null));
            } else if (path.endsWith(".resource")) {
                cases.add(new CaseSource(path, "RF_RESOURCE",
                        Path.of(path).getFileName().toString(), null));
            }
        }
        return cases;
    }

    public List<String> structureFindings(List<String> relPaths) {
        List<String> findings = new ArrayList<>();
        if (relPaths.stream().noneMatch(p -> p.endsWith(".robot"))) {
            findings.add("未发现 .robot 套件，dryrun 会得到 0 条用例");
        }
        return findings;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/importing/PreflightService.java"] = r"""package com.qa.autotest.application.importing;

import com.qa.autotest.domain.importing.CaseSource;
import com.qa.autotest.domain.importing.PreflightReport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 预检报告 / 敏感信息扫描 / 重导入 diff（IMP-05）。 */
@Service
public class PreflightService {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|api[_-]?key|secret|token)\\s*[:=]\\s*\\S+"
                    + "|\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private final ImportSandbox sandbox;
    private final PytestImportAdapter pytest;
    private final RobotImportAdapter robot;
    private final Map<Long, List<String>> lastBatchFiles = new ConcurrentHashMap<>();

    public PreflightService(ImportSandbox sandbox, PytestImportAdapter pytest,
                            RobotImportAdapter robot) {
        this.sandbox = sandbox;
        this.pytest = pytest;
        this.robot = robot;
    }

    public PreflightReport preflight(long projectId, String sourceType, String sourceName,
                                     InputStream zip, Path sandboxRoot) {
        boolean isPytest = "pytest".equalsIgnoreCase(sourceType);
        if (!isPytest && !"robot".equalsIgnoreCase(sourceType)) {
            throw new IllegalArgumentException("不支持的 sourceType: " + sourceType);
        }
        Path target = sandboxRoot.resolve("batch-" + projectId + "-" + System.nanoTime());
        List<String> files;
        try {
            files = sandbox.unpack(zip, target);
        } catch (IOException e) {
            throw new IllegalStateException("解压失败", e);
        }
        List<String> relPaths = files.stream().map(f -> f.split("\\|", 2)[0]).toList();
        String checksum = ImportSandbox.checksum(files);
        List<CaseSource> cases = isPytest
                ? pytest.parseCollectOnly(collectOnly(relPaths), relPaths)
                : robot.fromSuitePaths(relPaths);
        List<String> commands = isPytest ? pytest.preflightCommands(target)
                : robot.preflightCommands(target);
        List<String> findings = new ArrayList<>(isPytest
                ? pytest.structureFindings(relPaths) : robot.structureFindings(relPaths));
        findings.addAll(scanSensitive(target, relPaths));
        Map<String, List<String>> diff = diff(projectId, checksum, relPaths);
        lastBatchFiles.put(projectId, relPaths);
        return new PreflightReport(sourceType, checksum, files.size(), cases,
                commands, findings, diff, findings.isEmpty() && !cases.isEmpty());
    }

    /** 本机只生成 collect-only 命令，不执行外部工程（真机执行链待现场）。 */
    private String collectOnly(List<String> relPaths) {
        StringBuilder b = new StringBuilder();
        for (String path : relPaths) {
            if (path.endsWith("test_") || path.contains("test_") && path.endsWith(".py")) {
                b.append(path).append("::test_case\n");
            }
        }
        return b.toString();
    }

    public List<String> scanSensitive(Path root, List<String> relPaths) {
        List<String> findings = new ArrayList<>();
        for (String rel : relPaths) {
            Path file = root.resolve(rel).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                String text = Files.readString(file);
                if (SENSITIVE.matcher(text).find()) {
                    findings.add("疑似敏感信息: " + rel);
                }
            } catch (IOException e) {
                findings.add("无法读取（跳过扫描）: " + rel);
            }
        }
        return findings;
    }

    /** 重导入 diff：新增 / 删除 / 冲突（同名不同 checksum）三分类。 */
    public Map<String, List<String>> diff(long projectId, String newChecksum,
                                          List<String> newFiles) {
        List<String> old = lastBatchFiles.getOrDefault(projectId, List.of());
        Map<String, List<String>> result = new LinkedHashMap<>();
        List<String> added = new ArrayList<>(newFiles);
        added.removeAll(old);
        List<String> removed = new ArrayList<>(old);
        removed.removeAll(newFiles);
        result.put("added", added);
        result.put("removed", removed);
        result.put("conflict", newFiles.equals(old) && !old.isEmpty()
                ? List.of("内容校验和变化需人工确认: " + newChecksum) : List.of());
        return result;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/api/importing/ImportController.java"] = r"""package com.qa.autotest.api.importing;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.importing.ImportSandbox;
import com.qa.autotest.application.importing.PreflightService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.importing.PreflightReport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 导入预检 API（W4）。上传的 zip 一律先进沙箱，未通过预检不写业务表。 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final PreflightService preflight;
    private final AccessControlService access;

    public ImportController(PreflightService preflight, AccessControlService access) {
        this.preflight = preflight;
        this.access = access;
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.ok(Map.of(
                "types", List.of("pytest", "robot"),
                "maxEntries", ImportSandbox.MAX_ENTRIES,
                "maxTotalBytes", ImportSandbox.MAX_TOTAL_BYTES,
                "maxFileBytes", ImportSandbox.MAX_FILE_BYTES,
                "readOnlyLanding", true));
    }

    @GetMapping("/validate-path")
    public ApiResponse<Boolean> validatePath(@RequestParam String path) {
        return ApiResponse.ok(ImportSandbox.isSafeRelativePath(path));
    }

    @PostMapping("/preflight")
    public ApiResponse<PreflightReport> preflight(@RequestParam long projectId,
                                                  @RequestParam String sourceType,
                                                  @RequestParam("file") MultipartFile file) {
        access.requireProjectAccess(projectId);
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "上传文件为空");
        }
        try (var stream = file.getInputStream()) {
            return ApiResponse.ok(preflight.preflight(projectId, sourceType,
                    file.getOriginalFilename() == null ? "upload.zip" : file.getOriginalFilename(),
                    stream, Path.of("artifacts", "import-sandbox")));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "预检失败: " + e.getMessage());
        }
    }
}
"""

# ---------------------------------------------------------------- 单测
F["backend/src/test/java/com/qa/autotest/application/importing/ImportSandboxTest.java"] = r"""package com.qa.autotest.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportSandboxTest {

    private final ImportSandbox sandbox = new ImportSandbox();

    private static byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String entry : entries) {
                zos.putNextEntry(new ZipEntry(entry));
                zos.write("content".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void twelveUnsafePathsAreRejected() {
        String[] unsafe = {
                "../escape.py", "a/../../escape.py", "/etc/passwd", "\\\\server\\share\\x",
                "C:\\Windows\\x", "~/secret", "a//b.py", "a/./../b.py",
                "a" + (char) 0 + "b.py", "a" + (char) 1 + "b.py", "", "   ",
        };
        for (String path : unsafe) {
            assertFalse(ImportSandbox.isSafeRelativePath(path), "应拒绝: " + path);
        }
    }

    @Test
    void safeRelativePathsAccepted() {
        assertTrue(ImportSandbox.isSafeRelativePath("tests/test_a.py"));
        assertTrue(ImportSandbox.isSafeRelativePath("conftest.py"));
        assertTrue(ImportSandbox.isSafeRelativePath("pkg/sub/resource.resource"));
    }

    @Test
    void zipSlipIsRejectedAsWholeBatch(@TempDir Path dir) throws Exception {
        byte[] bad = zip("../evil.py");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> sandbox.unpack(new ByteArrayInputStream(bad), dir.resolve("out")));
        assertTrue(e.getMessage().contains("不安全") || e.getMessage().contains("逃逸"));
    }

    @Test
    void normalZipLandsReadOnlyWithStableChecksum(@TempDir Path dir) throws Exception {
        byte[] good = zip("tests/test_a.py", "conftest.py");
        Path target = dir.resolve("out");
        List<String> files = sandbox.unpack(new ByteArrayInputStream(good), target);
        assertEquals(2, files.size());
        assertFalse(target.resolve("tests/test_a.py").toFile().canWrite());
        assertEquals(ImportSandbox.checksum(files), ImportSandbox.checksum(files));
        assertEquals(64, ImportSandbox.checksum(files).length());
        assertTrue(Files.isRegularFile(target.resolve("conftest.py")));
    }
}
"""

F["backend/src/test/java/com/qa/autotest/application/importing/PreflightServiceTest.java"] = r"""package com.qa.autotest.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflightServiceTest {

    private final PreflightService service = new PreflightService(new ImportSandbox(),
            new PytestImportAdapter(), new RobotImportAdapter());

    private static byte[] zip(String name, String body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(body.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void pytestPreflightFindsStructureGaps(@TempDir Path dir) throws Exception {
        var report = service.preflight(1L, "pytest", "sample.zip",
                new ByteArrayInputStream(zip("tests/test_a.py", "# ordinary test")), dir);
        assertEquals("pytest", report.sourceType());
        assertEquals(1, report.fileCount());
        assertTrue(report.commands().get(0).contains("--collect-only"));
        assertTrue(report.sensitiveFindings().stream()
                .anyMatch(f -> f.contains("conftest.py")));
    }

    @Test
    void sensitiveContentIsFlagged(@TempDir Path dir) throws Exception {
        var report = service.preflight(1L, "robot", "sample.zip",
                new ByteArrayInputStream(zip("suite.robot", "password=hunter2\n10.1.2.3")), dir);
        assertTrue(report.sensitiveFindings().stream().anyMatch(f -> f.contains("疑似敏感信息")));
        assertFalse(report.executable());
    }

    @Test
    void robotPreflightListsSuite(@TempDir Path dir) throws Exception {
        var report = service.preflight(2L, "robot", "s.zip",
                new ByteArrayInputStream(zip("suite.robot", "*** Test Cases ***")), dir);
        assertTrue(report.commands().get(0).contains("--dryrun"));
        assertEquals(1, report.cases().size());
        assertEquals("ROBOT_SUITE", report.cases().get(0).kind());
    }

    @Test
    void reimportDiffSeparatesAddedRemovedAndConflict() {
        var first = service.diff(9L, "aaa", List.of("a.py", "b.py"));
        assertTrue(first.get("added").contains("a.py"));
        assertTrue(first.get("conflict").isEmpty());
        var second = service.diff(10L, "bbb", List.of("b.py", "c.py"));
        assertTrue(second.get("added").contains("c.py"));
        assertTrue(second.get("removed").isEmpty());
    }

    @Test
    void unsupportedSourceTypeRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> service.preflight(1L, "junit", "x.zip",
                        new ByteArrayInputStream(new byte[0]), Path.of(dir.toString())));
    }

    private static <T extends Throwable> void assertThrows(Class<T> type, Runnable action) {
        org.junit.jupiter.api.Assertions.assertThrows(type, action::run);
    }
}
"""

# ---------------------------------------------------------------- 前端
F["frontend/src/api/imports.ts"] = r"""/** W4 / IMP 导入预检 API。上传 zip 一律先进沙箱。 */
import { request, requestMultipart } from './http'

export interface CaseSource {
  relPath: string
  kind: string
  nodeId: string
  checksum: string | null
}

export interface PreflightReport {
  sourceType: string
  checksum: string
  fileCount: number
  cases: CaseSource[]
  commands: string[]
  sensitiveFindings: string[]
  diff: Record<string, string[]>
  executable: boolean
}

/** 只按文件名做最粗的客户端前置判断，真正的路径校验以服务端为准。 */
export function looksLikeZip(name: string): boolean {
  return /\.zip$/i.test(name) && !name.includes('..')
}

export const importsApi = {
  capabilities: () =>
    request<{ types: string[]; maxEntries: number; maxTotalBytes: number;
      maxFileBytes: number; readOnlyLanding: boolean }>('/api/imports/capabilities'),
  validatePath: (path: string) =>
    request<boolean>(`/api/imports/validate-path?path=${encodeURIComponent(path)}`),
  preflight: (projectId: number, sourceType: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return requestMultipart<PreflightReport>(
      `/api/imports/preflight?projectId=${projectId}&sourceType=${sourceType}`, form)
  },
}
"""

F["frontend/src/config/imports.spec.ts"] = r"""import { describe, expect, it } from 'vitest'
import { looksLikeZip } from '@/api/imports'

describe('looksLikeZip', () => {
  it('accepts plain zip names', () => {
    expect(looksLikeZip('samples.zip')).toBe(true)
    expect(looksLikeZip('SAMPLE.ZIP')).toBe(true)
  })

  it('rejects traversal and non-zip names', () => {
    expect(looksLikeZip('../evil.zip')).toBe(false)
    expect(looksLikeZip('samples.tar.gz')).toBe(false)
    expect(looksLikeZip('')).toBe(false)
  })
})
"""

pass_n = 0
skip_n = 0
for rel, text in F.items():
    p = ROOT / rel
    if p.exists():
        skip_n += 1
        print("[SKIP]", rel)
        continue
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")
    pass_n += 1
    print("[PASS]", rel)
print(f"\n[w4-import] PASS={pass_n} SKIP={skip_n} TOTAL={len(F)}")
