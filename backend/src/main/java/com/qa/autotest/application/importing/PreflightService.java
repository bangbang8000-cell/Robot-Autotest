package com.qa.autotest.application.importing;

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
