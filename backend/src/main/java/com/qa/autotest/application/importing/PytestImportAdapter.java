package com.qa.autotest.application.importing;

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
