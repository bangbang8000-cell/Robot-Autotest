package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.RobotFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 请求期执行预检（EXEC-04 / EXEC-05）。
 *
 * <p>在落库、排队之前拦掉确定性错误，避免「先建 run、再由执行期预检判失败」：
 * <ul>
 *   <li>套件路径必须真实存在（文件或目录）；</li>
 *   <li>测试级执行时，同一测试名不得出现在多个所选套件 —— {@code --test} 是全局过滤，
 *       重名会让未勾选的同名测试一起跑。</li>
 * </ul>
 *
 * <p><b>来源优先级</b>：项目在 {@code robot_file} 有脚本时以数据库为准（ADR-0005 真源，
 * 与 EXEC-05 的落盘工作区一致）；数据库为空时回落到磁盘 {@code execution.workspace-root}，
 * 兼容仍在磁盘维护脚本的项目。只做只读探测，不改工作区。
 */
@Service
public class ExecutionRequestPrecheck {

    private final RobotFileRepository scripts;
    private final ExecutionProperties props;
    private final FileSystemProbe files;

    public ExecutionRequestPrecheck(RobotFileRepository scripts, ExecutionProperties props,
                                    FileSystemProbe files) {
        this.scripts = scripts;
        this.props = props;
        this.files = files;
    }

    /** 套件路径存在性：缺失即 400，并给出真源与缺失清单。 */
    public void requireSuitesExist(long projectId, List<String> suitePaths) {
        List<RobotFile> all = scripts.listAll(projectId);
        if (all.isEmpty()) {
            requireSuitesExistOnDisk(suitePaths);
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String path : suitePaths) {
            if (!existsInScripts(all, path)) {
                missing.add(path);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "套件路径在脚本工作区不存在（robot_file, project=" + projectId + "）："
                            + String.join(", ", missing)
                            + "。请先把脚本保存到文件工作区，或核对路径");
        }
    }

    /** 测试名跨套件重名：{@code --test} 无法区分，提前 400。 */
    public void requireUnambiguousTestNames(long projectId, List<String> suitePaths,
                                            List<String> testNames) {
        if (testNames == null || testNames.isEmpty()) {
            return;
        }
        List<RobotFile> all = scripts.listAll(projectId);
        if (all.isEmpty()) {
            requireUnambiguousTestNamesOnDisk(suitePaths, testNames);
            return;
        }
        Map<String, List<String>> hits = new LinkedHashMap<>();
        for (String suite : suitePaths) {
            for (RobotFile file : all) {
                if (!file.path().endsWith(".robot") || !underSuite(file.path(), suite)) {
                    continue;
                }
                Set<String> names = new HashSet<>();
                for (CaseScriptResolver.ScriptTest test
                        : CaseScriptResolver.scan(file.content() == null ? "" : file.content())) {
                    names.add(test.name().trim());
                }
                // 以文件为单位登记：目录套件下两个文件重名同样会让 --test 多跑。
                collectHits(hits, file.path(), names, testNames);
            }
        }
        rejectAmbiguous(hits);
    }

    private static boolean existsInScripts(List<RobotFile> all, String path) {
        return all.stream().anyMatch(file -> underSuite(file.path(), path));
    }

    /** 文件本身，或目录前缀下的文件，都算命中。 */
    private static boolean underSuite(String filePath, String suitePath) {
        return filePath.equals(suitePath) || filePath.startsWith(suitePath + "/");
    }

    private static void collectHits(Map<String, List<String>> hits, String suite,
                                    Set<String> names, List<String> testNames) {
        for (String wanted : testNames) {
            if (wanted != null && names.contains(wanted.trim())) {
                hits.computeIfAbsent(wanted.trim(), key -> new ArrayList<>()).add(suite);
            }
        }
    }

    private static void rejectAmbiguous(Map<String, List<String>> hits) {
        List<String> ambiguous = new ArrayList<>();
        hits.forEach((name, suites) -> {
            if (suites.size() > 1) {
                ambiguous.add(name + "（" + String.join(", ", suites) + "）");
            }
        });
        if (!ambiguous.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "测试名在多个所选套件中重复，--test 无法区分："
                            + String.join("; ", ambiguous)
                            + "。请让测试名唯一，或改用 include 标签过滤");
        }
    }

    /** 磁盘回落：项目尚未迁入文件工作区时沿用原有行为。 */
    private void requireSuitesExistOnDisk(List<String> suitePaths) {
        Path root = ExecutionPaths.resolve(props.getWorkspaceRoot());
        List<String> missing = new ArrayList<>();
        for (String path : suitePaths) {
            if (!files.exists(root.resolve(path))) {
                missing.add(path);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "套件路径在执行工作区不存在（workspace=" + root + "）："
                            + String.join(", ", missing)
                            + "。请先同步脚本到执行工作区，或核对路径");
        }
    }

    private void requireUnambiguousTestNamesOnDisk(List<String> suitePaths,
                                                   List<String> testNames) {
        Path root = ExecutionPaths.resolve(props.getWorkspaceRoot());
        Map<String, List<String>> hits = new LinkedHashMap<>();
        Set<String> uniqueSuites = new LinkedHashSet<>(suitePaths);
        for (String suite : uniqueSuites) {
            // 目录套件要递归展开：只扫传入路径本身会把目录下的同名测试漏掉，与数据库口径不一致。
            for (Path file : robotFilesUnder(root.resolve(suite))) {
                Set<String> names = new HashSet<>();
                for (CaseScriptResolver.ScriptTest test : CaseScriptResolver.scan(read(file))) {
                    names.add(test.name().trim());
                }
                collectHits(hits, root.relativize(file).toString().replace('\\', '/'),
                        names, testNames);
            }
        }
        rejectAmbiguous(hits);
    }

    /** 文件直接返回；目录递归收集 {@code .robot}，与 {@code robot_file} 分支的扫描范围保持一致。 */
    private static List<Path> robotFilesUnder(Path target) {
        if (Files.isRegularFile(target)) {
            return List.of(target);
        }
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(target)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".robot"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
