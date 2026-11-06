package com.qa.autotest.application.importing;

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
