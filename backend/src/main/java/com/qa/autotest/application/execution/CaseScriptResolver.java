package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RobotFileRepository;
import com.qa.autotest.domain.caseasset.FileNode;
import com.qa.autotest.domain.caseasset.RobotFile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用例编号 → 脚本测试绑定解析（EXEC-02 / ADR-0005 §3）。
 *
 * <p>绑定来源（按优先级）：
 * <ol>
 *   <li>RF 脚本测试的 {@code [Tags]} 中出现 {@code case:<case_code>}（ADR-0005 的约定）；</li>
 *   <li>兼容 EXEC-01 起的报告覆盖口径：测试名与用例编号完全一致（无标签时的兜底）。</li>
 * </ol>
 *
 * <p>只读 {@code robot_file.content}，不依赖磁盘工作区 —— 与执行器实际使用的
 * {@code execution.workspace-root} 可能不一致，这条差异已在 WBS 口径里注明。
 */
@Service
public class CaseScriptResolver {

    private static final String TAG_PREFIX = "case:";

    private final RobotFileRepository files;

    public CaseScriptResolver(RobotFileRepository files) {
        this.files = files;
    }

    /**
     * 解析所需编号的绑定；未命中的编号不出现在结果里，由调用方判「未绑定」。
     */
    public Map<String, List<Binding>> resolve(long projectId, Set<String> caseCodes) {
        Map<String, LinkedHashSet<Binding>> index = new LinkedHashMap<>();
        if (caseCodes == null || caseCodes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        for (FileNode node : files.nodes(projectId)) {
            if (node.directory() || !node.path().toLowerCase(Locale.ROOT).endsWith(".robot")) {
                continue;
            }
            if (index.keySet().containsAll(caseCodes)) {
                break;
            }
            Optional<RobotFile> file = files.findByPath(projectId, node.path());
            if (file.isEmpty()) {
                continue;
            }
            for (ScriptTest test : scan(file.get().content())) {
                bind(index, caseCodes, node.path(), test);
            }
        }
        Map<String, List<Binding>> result = new LinkedHashMap<>();
        index.forEach((code, bindings) -> result.put(code, new ArrayList<>(bindings)));
        return result;
    }

    private static void bind(Map<String, LinkedHashSet<Binding>> index, Set<String> wanted,
                             String path, ScriptTest test) {
        for (String tag : test.tags()) {
            if (tag.length() > TAG_PREFIX.length()
                    && tag.regionMatches(true, 0, TAG_PREFIX, 0, TAG_PREFIX.length())) {
                String code = tag.substring(TAG_PREFIX.length()).trim();
                if (wanted.contains(code)) {
                    index.computeIfAbsent(code, key -> new LinkedHashSet<>())
                            .add(new Binding(path, test.name()));
                }
            }
        }
        // 兜底：测试名即用例编号（EXEC-01 起的覆盖口径），无标签时仍可执行
        String name = test.name().trim();
        if (wanted.contains(name)) {
            index.computeIfAbsent(name, key -> new LinkedHashSet<>())
                    .add(new Binding(path, test.name()));
        }
    }

    /**
     * 抽取 {@code *** Test Cases ***} 段内的测试名与 {@code [Tags]}。
     *
     * <p>只认列 0 且非注释的行作为测试名；缩进行属于当前测试。支持中英文节区名。
     */
    static List<ScriptTest> scan(String content) {
        List<ScriptTest> tests = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return tests;
        }
        boolean inTestCases = false;
        String name = null;
        Set<String> tags = new LinkedHashSet<>();
        for (String rawLine : content.split("\n", -1)) {
            String trimmed = rawLine.trim();
            if (trimmed.startsWith("***") && trimmed.endsWith("***")) {
                if (inTestCases && name != null) {
                    tests.add(new ScriptTest(name, Set.copyOf(tags)));
                }
                inTestCases = isTestCaseSection(trimmed);
                name = null;
                tags = new LinkedHashSet<>();
                continue;
            }
            if (!inTestCases || trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            char first = rawLine.charAt(0);
            if (first == ' ' || first == '\t') {
                if (name != null && isTagsLine(trimmed)) {
                    tags.addAll(tagValues(trimmed));
                }
                continue;
            }
            if (name != null) {
                tests.add(new ScriptTest(name, Set.copyOf(tags)));
            }
            name = trimmed;
            tags = new LinkedHashSet<>();
        }
        if (inTestCases && name != null) {
            tests.add(new ScriptTest(name, Set.copyOf(tags)));
        }
        return tests;
    }

    private static boolean isTagsLine(String trimmed) {
        return trimmed.length() > 6 && trimmed.regionMatches(true, 0, "[tags]", 0, 6);
    }

    private static List<String> tagValues(String trimmed) {
        String rest = trimmed.substring(6).trim();
        return rest.isEmpty() ? List.of() : List.of(rest.split("\\s+"));
    }

    private static boolean isTestCaseSection(String sectionLine) {
        String inner = sectionLine.replace("*", "").replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        return "testcase".equals(inner) || "testcases".equals(inner)
                || "测试用例".equals(inner);
    }

    /** 脚本测试：测试名 + 标签。 */
    record ScriptTest(String name, Set<String> tags) {
    }

    /** 绑定结果：套件文件 + 测试名。 */
    public record Binding(String suitePath, String testName) {
    }
}
