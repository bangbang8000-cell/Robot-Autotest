# -*- coding: utf-8 -*-
"""W3 / KRN-01..05 双内核批量落地。幂等。"""
import pathlib

ROOT = pathlib.Path.cwd()
F = {}

# ---------------------------------------------------------------- domain.kernel
F["backend/src/main/java/com/qa/autotest/domain/kernel/CaseStatus.java"] = r"""package com.qa.autotest.domain.kernel;

import java.util.Locale;

/** 双内核统一状态（KRN-01）。pytest 六态与 RF 三态都归一到这里，绝不各算一列。 */
public enum CaseStatus {
    PASS,
    FAIL,
    SKIP,
    XFAIL,
    XPASS,
    ERROR;

    public static CaseStatus fromPytest(String outcome) {
        if (outcome == null) {
            return ERROR;
        }
        return switch (outcome.trim().toLowerCase(Locale.ROOT)) {
            case "passed", "pass" -> PASS;
            case "failed", "fail", "failure" -> FAIL;
            case "skipped", "skip" -> SKIP;
            case "xfailed", "xfail" -> XFAIL;
            case "xpassed", "xpass" -> XPASS;
            default -> ERROR;
        };
    }

    /** 是否算「未通过」：对外通过率只把 PASS / XFAIL / SKIP 当非失败。 */
    public boolean isFailure() {
        return this == FAIL || this == ERROR || this == XPASS;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/domain/kernel/NormalizedCaseResult.java"] = r"""package com.qa.autotest.domain.kernel;

/** 归一后的单条用例结论。nodeId 对 pytest 是 nodeid（含参数化后缀），对 RF 是 longname。 */
public record NormalizedCaseResult(String nodeId, String name, CaseStatus status,
                                   double durationSeconds, String message) {
}
"""

F["backend/src/main/java/com/qa/autotest/domain/kernel/NormalizedRun.java"] = r"""package com.qa.autotest.domain.kernel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 归一后的整轮结果（KRN-01）。counts 由 cases 现场统计，不接受外部传入的第二套数字。 */
public record NormalizedRun(String kernel, String runId, List<NormalizedCaseResult> cases) {

    public NormalizedRun {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public Map<String, Integer> counts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (CaseStatus status : CaseStatus.values()) {
            result.put(status.name(), 0);
        }
        for (NormalizedCaseResult item : cases) {
            result.merge(item.status().name(), 1, Integer::sum);
        }
        return result;
    }

    public int total() {
        return cases.size();
    }

    public int failures() {
        return (int) cases.stream().filter(c -> c.status().isFailure()).count();
    }

    public boolean complete() {
        return !cases.isEmpty();
    }
}
"""

F["backend/src/main/java/com/qa/autotest/domain/kernel/KernelRequest.java"] = r"""package com.qa.autotest.domain.kernel;

import java.util.List;

/** 内核执行请求。两套内核共用同一个入参，避免«按内核各写一套 DTO»。 */
public record KernelRequest(long projectId, String runId, int shardNo, int processes,
                            List<String> suitePaths, List<String> testNames,
                            String includeTag, String displayName) {

    public KernelRequest {
        suitePaths = suitePaths == null ? List.of() : List.copyOf(suitePaths);
        testNames = testNames == null ? List.of() : List.copyOf(testNames);
    }
}
"""

F["backend/src/main/java/com/qa/autotest/domain/kernel/ExecutionKernel.java"] = r"""package com.qa.autotest.domain.kernel;

import java.nio.file.Path;
import java.util.List;

/** 执行内核 SPI（KRN-01）。domain 层接口，实现不得反向依赖 domain 之外的上层。 */
public interface ExecutionKernel {

    String name();

    /** 由产物目录解析归一结果；找不到主通道时自动走兜底通道。 */
    NormalizedRun normalize(Path artifactDir, String runId);

    /** 生成本内核的执行命令；同一请求下 RF 命令必须与旧路径逐字等价。 */
    List<String> command(KernelRequest request, Path artifactsRoot);
}
"""

F["backend/src/main/java/com/qa/autotest/domain/kernel/KernelSelection.java"] = r"""package com.qa.autotest.domain.kernel;

/** 内核选择结果（KRN-04）：kernel + 生效来源 + 是否被上层覆盖。 */
public record KernelSelection(String kernel, String source, boolean overridden) {
}
"""

# ---------------------------------------------------------------- application.kernel
F["backend/src/main/java/com/qa/autotest/application/kernel/KernelRegistry.java"] = r"""package com.qa.autotest.application.kernel;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.kernel.ExecutionKernel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 内核注册表（KRN-01）：Spring 收集所有 SPI 实现，未知名字一律拒绝而非静默回退。 */
@Service
public class KernelRegistry {

    private final Map<String, ExecutionKernel> kernels;

    public KernelRegistry(List<ExecutionKernel> beans) {
        Map<String, ExecutionKernel> collected = new LinkedHashMap<>();
        for (ExecutionKernel bean : beans) {
            if (collected.putIfAbsent(bean.name(), bean) != null) {
                throw new IllegalStateException("内核名重复: " + bean.name());
            }
        }
        this.kernels = Map.copyOf(collected);
    }

    public ExecutionKernel require(String name) {
        ExecutionKernel kernel = kernels.get(name);
        if (kernel == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "未注册的执行内核: " + name + "；可选 " + kernels.keySet());
        }
        return kernel;
    }

    public Set<String> names() {
        return kernels.keySet();
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/kernel/RobotKernel.java"] = r"""package com.qa.autotest.application.kernel;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.PabotCommandBuilder;
import com.qa.autotest.domain.kernel.ExecutionKernel;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * RF 内核（KRN-02）：命令装配<b>直接委托</b>既有 {@link PabotCommandBuilder}，
 * 不做任何字符串重排，保证 v1.0 的 RF 命令逐字等价（G8 第 3 条）。
 *
 * <p>结果归一复用既有 output.xml 解析器；本内核不引入第二套 RF 判定逻辑。
 */
@Service
public class RobotKernel implements ExecutionKernel {

    public static final String NAME = "robot";

    private final PabotCommandBuilder builder = new PabotCommandBuilder();
    private final ExecutionProperties properties;
    private final OutputXmlNormalizer normalizer;

    public RobotKernel(ExecutionProperties properties, OutputXmlNormalizer normalizer) {
        this.properties = properties;
        this.normalizer = normalizer;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<String> command(KernelRequest request, Path artifactsRoot) {
        PabotCommandBuilder.ShardSpec spec = new PabotCommandBuilder.ShardSpec(
                request.shardNo(), request.suitePaths(), request.processes(),
                request.includeTag(), request.displayName(), request.testNames());
        return builder.build(spec, properties, artifactsRoot).argv();
    }

    @Override
    public NormalizedRun normalize(Path artifactDir, String runId) {
        Path output = artifactDir.resolve("output.xml");
        if (!Files.isRegularFile(output)) {
            throw new IllegalArgumentException("RF 产物缺少 output.xml: " + artifactDir);
        }
        return normalizer.fromOutputXml(output, runId);
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/kernel/OutputXmlNormalizer.java"] = r"""package com.qa.autotest.application.kernel;

import com.qa.autotest.domain.kernel.CaseStatus;
import com.qa.autotest.domain.kernel.NormalizedCaseResult;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** RF output.xml → NormalizedRun（KRN-01）。只读 <test> 的 status，不重写 RF 判定。 */
@Component
public class OutputXmlNormalizer {

    public NormalizedRun fromOutputXml(Path output, String runId) {
        try (InputStream in = Files.newInputStream(output)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(in);
            NodeList tests = doc.getElementsByTagName("test");
            List<NormalizedCaseResult> cases = new ArrayList<>();
            for (int i = 0; i < tests.getLength(); i++) {
                Element test = (Element) tests.item(i);
                String name = test.getAttribute("name");
                NodeList statuses = test.getElementsByTagName("status");
                String status = statuses.getLength() == 0
                        ? "FAIL" : ((Element) statuses.item(0)).getAttribute("status");
                cases.add(new NormalizedCaseResult(name, name,
                        "PASS".equalsIgnoreCase(status) ? CaseStatus.PASS : CaseStatus.FAIL,
                        0.0, status));
            }
            return new NormalizedRun(RobotKernel.NAME, runId, cases);
        } catch (Exception e) {
            throw new IllegalStateException("output.xml 解析失败: " + output, e);
        }
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/kernel/PytestKernel.java"] = r"""package com.qa.autotest.application.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.domain.kernel.CaseStatus;
import com.qa.autotest.domain.kernel.ExecutionKernel;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedCaseResult;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * pytest 内核（KRN-03）：主通道 pytest-json-report，兜底 junitxml。
 * 参数化用例保留 [param] 后缀独立成条，xfail / xpass / error 不折叠成 PASS/FAIL。
 */
@Service
public class PytestKernel implements ExecutionKernel {

    public static final String NAME = "pytest";

    private final ObjectMapper mapper;

    public PytestKernel(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<String> command(KernelRequest request, Path artifactsRoot) {
        Path dir = artifactsRoot.resolve("kernel-pytest-" + request.shardNo());
        List<String> argv = new ArrayList<>();
        argv.add("pytest");
        argv.add("--json-report");
        argv.add("--json-report-file=" + dir.resolve("pytest-report.json"));
        argv.add("--junitxml=" + dir.resolve("junit.xml"));
        argv.add("-q");
        for (String test : request.testNames()) {
            argv.add(test);
        }
        argv.addAll(request.suitePaths());
        return List.copyOf(argv);
    }

    @Override
    public NormalizedRun normalize(Path artifactDir, String runId) {
        Path json = artifactDir.resolve("pytest-report.json");
        if (Files.isRegularFile(json)) {
            return fromJson(json, runId);
        }
        Path junit = artifactDir.resolve("junit.xml");
        if (Files.isRegularFile(junit)) {
            return fromJunit(junit, runId);
        }
        throw new IllegalArgumentException("pytest 产物缺少 pytest-report.json / junit.xml: "
                + artifactDir);
    }

    private NormalizedRun fromJson(Path path, String runId) {
        try (InputStream in = Files.newInputStream(path)) {
            JsonNode root = mapper.readTree(in);
            List<NormalizedCaseResult> cases = new ArrayList<>();
            for (JsonNode test : root.path("tests")) {
                String nodeId = test.path("nodeid").asText("unknown");
                String outcome = test.path("outcome").asText("error");
                double duration = test.path("duration").asDouble(0.0);
                cases.add(new NormalizedCaseResult(nodeId, nodeId,
                        CaseStatus.fromPytest(outcome), duration, outcome));
            }
            return new NormalizedRun(NAME, runId, cases);
        } catch (Exception e) {
            throw new IllegalStateException("pytest json-report 解析失败: " + path, e);
        }
    }

    private NormalizedRun fromJunit(Path path, String runId) {
        try (InputStream in = Files.newInputStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(in);
            NodeList nodes = doc.getElementsByTagName("testcase");
            List<NormalizedCaseResult> cases = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element node = (Element) nodes.item(i);
                String name = node.getAttribute("name");
                String classname = node.getAttribute("classname");
                String nodeId = classname.isEmpty() ? name : classname + "::" + name;
                CaseStatus status = CaseStatus.PASS;
                if (node.getElementsByTagName("failure").getLength() > 0) {
                    status = CaseStatus.FAIL;
                } else if (node.getElementsByTagName("error").getLength() > 0) {
                    status = CaseStatus.ERROR;
                } else if (node.getElementsByTagName("skipped").getLength() > 0) {
                    status = CaseStatus.SKIP;
                }
                cases.add(new NormalizedCaseResult(nodeId, name, status,
                        parseDouble(node.getAttribute("time")), status.name()));
            }
            return new NormalizedRun(NAME, runId, cases);
        } catch (Exception e) {
            throw new IllegalStateException("junit.xml 解析失败: " + path, e);
        }
    }

    private static double parseDouble(String raw) {
        try {
            return raw == null || raw.isBlank() ? 0.0 : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/kernel/KernelSelector.java"] = r"""package com.qa.autotest.application.kernel;

import com.qa.autotest.application.repository.SystemSettingRepository;
import com.qa.autotest.application.system.SystemSettingService;
import com.qa.autotest.domain.kernel.KernelSelection;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 选择粒度（KRN-04）：项目默认 → 用例集覆盖 → 用例覆盖，后者优先；
 * 每次返回「生效来源」与「是否被覆盖」，页面上必须能解释为什么跑到这个内核。
 */
@Service
public class KernelSelector {

    private final KernelRegistry registry;
    private final SystemSettingRepository settings;

    public KernelSelector(KernelRegistry registry, SystemSettingRepository settings) {
        this.registry = registry;
        this.settings = settings;
    }

    public KernelSelection select(long projectId, String setOverride, String caseOverride) {
        String projectDefault = settings.findProject(projectId,
                        SystemSettingService.P_DEFAULT_KERNEL)
                .orElse(RobotKernel.NAME)
                .toLowerCase(Locale.ROOT);
        String chosen = choose(projectDefault, setOverride, caseOverride);
        registry.require(chosen);
        String source = caseOverride != null && !caseOverride.isBlank() ? "CASE_OVERRIDE"
                : setOverride != null && !setOverride.isBlank() ? "SET_OVERRIDE"
                : "PROJECT_DEFAULT";
        return new KernelSelection(chosen, source, !"PROJECT_DEFAULT".equals(source));
    }

    private static String choose(String projectDefault, String setOverride, String caseOverride) {
        if (caseOverride != null && !caseOverride.isBlank()) {
            return caseOverride.trim().toLowerCase(Locale.ROOT);
        }
        if (setOverride != null && !setOverride.isBlank()) {
            return setOverride.trim().toLowerCase(Locale.ROOT);
        }
        return projectDefault;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/kernel/EnvironmentProbe.java"] = r"""package com.qa.autotest.application.kernel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 环境探测 / 能力矩阵 / 进程树终止 / 重试超时统一（KRN-05）。 */
@Service
public class EnvironmentProbe {

    private final KernelRegistry registry;

    public EnvironmentProbe(KernelRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String binary : new String[]{"robot", "pabot", "pytest", "python3"}) {
            result.put(binary, onPath(binary));
        }
        result.put("kernels", registry.names());
        result.put("timeoutSeconds", 1800);
        result.put("maxRetries", 2);
        result.put("sameRunMixedKernels", false);
        return result;
    }

    public static boolean onPath(String binary) {
        String path = System.getenv().getOrDefault("PATH", "");
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, binary);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 先终止后代再终止自身，避免留下孤儿 pabot / pytest 子进程。 */
    public int terminateTree(ProcessHandle root) {
        if (root == null || !root.isAlive()) {
            return 0;
        }
        int count = 0;
        for (ProcessHandle child : root.descendants().toList()) {
            if (child.destroyForcibly()) {
                count++;
            }
        }
        if (root.destroyForcibly()) {
            count++;
        }
        return count;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/api/kernel/KernelController.java"] = r"""package com.qa.autotest.api.kernel;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.kernel.EnvironmentProbe;
import com.qa.autotest.application.kernel.KernelRegistry;
import com.qa.autotest.application.kernel.KernelSelector;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.KernelSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内核能力 / 选择 / 命令预览（W3）。命令预览用固定 fixture，不接受任意路径。 */
@RestController
@RequestMapping("/api/kernels")
public class KernelController {

    private final KernelRegistry registry;
    private final KernelSelector selector;
    private final EnvironmentProbe probe;
    private final AccessControlService access;

    public KernelController(KernelRegistry registry, KernelSelector selector,
                            EnvironmentProbe probe, AccessControlService access) {
        this.registry = registry;
        this.selector = selector;
        this.probe = probe;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.ok(probe.capabilities());
    }

    @GetMapping("/select")
    public ApiResponse<KernelSelection> select(@RequestParam long projectId,
                                               @RequestParam(required = false) String setKernel,
                                               @RequestParam(required = false) String caseKernel) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(selector.select(projectId, setKernel, caseKernel));
    }

    @GetMapping("/command")
    public ApiResponse<Map<String, Object>> command(@RequestParam long projectId,
                                                    @RequestParam String kernel,
                                                    @RequestParam(defaultValue = "1") int processes) {
        access.requireProjectAccess(projectId);
        KernelRequest request = new KernelRequest(projectId, "PREVIEW", 1, processes,
                List.of("fixtures/smoke/2node-smoke.robot"), List.of(), null, "kernel-preview");
        List<String> argv = registry.require(kernel).command(request, Path.of("artifacts"));
        return ApiResponse.ok(Map.of("kernel", kernel, "argv", argv,
                "source", "PabotCommandBuilder / C4 白名单命令"));
    }
}
"""

# ---------------------------------------------------------------- 单测
F["backend/src/test/java/com/qa/autotest/domain/kernel/NormalizedRunTest.java"] = r"""package com.qa.autotest.domain.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NormalizedRunTest {

    private static NormalizedRun sample() {
        return new NormalizedRun("pytest", "R1", List.of(
                new NormalizedCaseResult("a.py::test_ok[1]", "test_ok[1]", CaseStatus.PASS, 0.1, "passed"),
                new NormalizedCaseResult("a.py::test_ok[2]", "test_ok[2]", CaseStatus.PASS, 0.1, "passed"),
                new NormalizedCaseResult("a.py::test_bad", "test_bad", CaseStatus.FAIL, 0.2, "failed"),
                new NormalizedCaseResult("a.py::test_x", "test_x", CaseStatus.XFAIL, 0.0, "xfailed")));
    }

    @Test
    void countsAreDerivedFromCasesNotPassedIn() {
        Map<String, Integer> counts = sample().counts();
        assertEquals(2, counts.get("PASS"));
        assertEquals(1, counts.get("FAIL"));
        assertEquals(1, counts.get("XFAIL"));
        assertEquals(0, counts.get("SKIP"));
        assertEquals(6, counts.size());
    }

    @Test
    void parameterizedCasesStaySeparate() {
        assertEquals(4, sample().total());
        assertEquals(2, sample().cases().stream()
                .filter(c -> c.nodeId().contains("[")).count());
    }

    @Test
    void failureSemanticsKeepXfailOutOfFailures() {
        assertEquals(1, sample().failures());
        assertTrue(CaseStatus.XPASS.isFailure());
        assertFalse(CaseStatus.SKIP.isFailure());
        assertTrue(sample().complete());
    }

    @Test
    void emptyRunIsIncomplete() {
        NormalizedRun empty = new NormalizedRun("robot", "R0", null);
        assertFalse(empty.complete());
        assertEquals(0, empty.total());
    }
}
"""

F["backend/src/test/java/com/qa/autotest/application/kernel/PytestKernelTest.java"] = r'''package com.qa.autotest.application.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.domain.kernel.CaseStatus;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PytestKernelTest {

    private final PytestKernel kernel = new PytestKernel(new ObjectMapper());

    @Test
    void jsonReportMapsAllSixStatuses(@TempDir Path dir) throws Exception {
        String json = """
                {"tests":[
                  {"nodeid":"t.py::test_p[1]","outcome":"passed","duration":0.1},
                  {"nodeid":"t.py::test_p[2]","outcome":"passed","duration":0.2},
                  {"nodeid":"t.py::test_f","outcome":"failed","duration":0.3},
                  {"nodeid":"t.py::test_s","outcome":"skipped"},
                  {"nodeid":"t.py::test_x","outcome":"xfailed"},
                  {"nodeid":"t.py::test_xp","outcome":"xpassed"},
                  {"nodeid":"t.py::test_e","outcome":"error"}
                ]}""";
        Files.writeString(dir.resolve("pytest-report.json"), json);
        NormalizedRun run = kernel.normalize(dir, "R1");
        assertEquals(7, run.total());
        assertEquals(2, run.counts().get("PASS"));
        assertEquals(1, run.counts().get("XFAIL"));
        assertEquals(1, run.counts().get("ERROR"));
        assertEquals(3, run.failures());
        assertTrue(run.cases().get(0).nodeId().contains("[1]"));
    }

    @Test
    void junitFallbackDistinguishesFailureErrorAndSkipped(@TempDir Path dir) throws Exception {
        String xml = """
                <testsuite name="s" tests="4">
                  <testcase classname="t" name="ok" time="0.1"/>
                  <testcase classname="t" name="bad" time="0.2"><failure message="boom"/></testcase>
                  <testcase classname="t" name="err"><error message="setup"/></testcase>
                  <testcase classname="t" name="skip"><skipped/></testcase>
                </testsuite>""";
        Files.writeString(dir.resolve("junit.xml"), xml);
        NormalizedRun run = kernel.normalize(dir, "R2");
        assertEquals(4, run.total());
        assertEquals(CaseStatus.PASS, run.cases().get(0).status());
        assertEquals(CaseStatus.FAIL, run.cases().get(1).status());
        assertEquals(CaseStatus.ERROR, run.cases().get(2).status());
        assertEquals(CaseStatus.SKIP, run.cases().get(3).status());
    }

    @Test
    void missingArtifactsRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> kernel.normalize(dir, "R3"));
    }

    @Test
    void commandUsesBothReportChannels() {
        KernelRequest request = new KernelRequest(1L, "R4", 2, 4,
                List.of("tests/"), List.of("tests/test_a.py::test_ok"), null, "preview");
        List<String> argv = kernel.command(request, Path.of("artifacts"));
        assertEquals("pytest", argv.get(0));
        assertTrue(argv.stream().anyMatch(a -> a.startsWith("--json-report-file=")));
        assertTrue(argv.stream().anyMatch(a -> a.startsWith("--junitxml=")));
        assertTrue(argv.contains("tests/test_a.py::test_ok"));
    }
}
'''

F["backend/src/test/java/com/qa/autotest/application/kernel/RobotKernelParityTest.java"] = r'''package com.qa.autotest.application.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.PabotCommandBuilder;
import com.qa.autotest.domain.kernel.KernelRequest;
import com.qa.autotest.domain.kernel.NormalizedRun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RobotKernelParityTest {

    private final RobotKernel kernel = new RobotKernel(new ExecutionProperties(),
            new OutputXmlNormalizer());

    private static KernelRequest request() {
        return new KernelRequest(1L, "R1", 1, 2,
                List.of("fixtures/smoke/2node-smoke.robot", "fixtures/smoke/extra.robot"),
                List.of("Case A", "Case B"), "smoke", "parity-check");
    }

    @Test
    void commandIsByteForByteEqualToLegacyBuilder() {
        KernelRequest request = request();
        Path artifacts = Path.of("artifacts");
        PabotCommandBuilder.ShardSpec spec = new PabotCommandBuilder.ShardSpec(
                request.shardNo(), request.suitePaths(), request.processes(),
                request.includeTag(), request.displayName(), request.testNames());
        List<String> legacy = new PabotCommandBuilder()
                .build(spec, new ExecutionProperties(), artifacts).argv();
        assertEquals(legacy, kernel.command(request, artifacts));
        assertTrue(legacy.contains("--test"));
    }

    @Test
    void outputXmlIsNormalizedWithoutRewritingRfSemantics(@TempDir Path dir) throws Exception {
        String xml = """
                <robot><suite name="S"><test name="A"><status status="PASS"/></test>
                <test name="B"><status status="FAIL"/></test></suite></robot>""";
        Files.writeString(dir.resolve("output.xml"), xml);
        NormalizedRun run = kernel.normalize(dir, "R1");
        assertEquals(2, run.total());
        assertEquals(1, run.failures());
        assertEquals("robot", run.kernel());
    }
}
'''

F["backend/src/test/java/com/qa/autotest/application/kernel/KernelSelectorTest.java"] = r"""package com.qa.autotest.application.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.application.repository.SystemSettingRepository;
import com.qa.autotest.application.system.SystemSettingService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.domain.kernel.KernelSelection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KernelSelectorTest {

    private static SystemSettingRepository settings(String value) {
        return new SystemSettingRepository() {
            @Override public Map<String, String> findAllSystem() { return Map.of(); }
            @Override public Optional<String> findSystem(String key) { return Optional.empty(); }
            @Override public void upsertSystem(String key, String v, Long actor) { }
            @Override public Map<String, String> findAllProject(long projectId) { return Map.of(); }
            @Override public Optional<String> findProject(long projectId, String key) {
                return SystemSettingService.P_DEFAULT_KERNEL.equals(key)
                        ? Optional.ofNullable(value) : Optional.empty();
            }
            @Override public void upsertProject(long projectId, String key, String v, Long actor) { }
        };
    }

    private static KernelRegistry registry() {
        return new KernelRegistry(List.of(
                new RobotKernel(new com.qa.autotest.application.execution.ExecutionProperties(),
                        new OutputXmlNormalizer()),
                new PytestKernel(new com.fasterxml.jackson.databind.ObjectMapper())));
    }

    @Test
    void projectDefaultWinsWhenNoOverride() {
        KernelSelection s = new KernelSelector(registry(), settings("robot"))
                .select(1L, null, null);
        assertEquals("robot", s.kernel());
        assertEquals("PROJECT_DEFAULT", s.source());
        assertFalse(s.overridden());
    }

    @Test
    void caseOverrideBeatsSetOverrideAndMarksSource() {
        KernelSelection s = new KernelSelector(registry(), settings("robot"))
                .select(1L, "pytest", "ROBOT");
        assertEquals("robot", s.kernel());
        assertEquals("CASE_OVERRIDE", s.source());
        assertTrue(s.overridden());
    }

    @Test
    void unknownKernelIsRejectedNotSilentlyFallenBack() {
        assertThrows(BusinessException.class,
                () -> new KernelSelector(registry(), settings("robot"))
                        .select(1L, null, "nonexistent"));
    }

    @Test
    void registryRejectsDuplicateNames() {
        assertThrows(IllegalStateException.class,
                () -> new KernelRegistry(List.of(
                        new PytestKernel(new com.fasterxml.jackson.databind.ObjectMapper()),
                        new PytestKernel(new com.fasterxml.jackson.databind.ObjectMapper()))));
    }
}
"""

F["backend/src/test/java/com/qa/autotest/arch/KernelBoundaryTest.java"] = r"""package com.qa.autotest.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** KRN-01 SPI 边界：domain.kernel 必须是纯领域层，不反向依赖任何上层或框架。 */
class KernelBoundaryTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.qa.autotest");

    @Test
    void domainKernelDoesNotDependOnUpperLayersOrFrameworks() {
        // 用精确的本包前缀，不写 ..api.. / ..application.. 这类宽匹配：
        // 否则 org.junit.jupiter.api 会被 ..api.. 命中，把测试类也误判成违规。
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.kernel..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.qa.autotest.api..",
                        "com.qa.autotest.application..",
                        "com.qa.autotest.infrastructure..",
                        "org.springframework..", "com.fasterxml.jackson..")
                .because("KRN-01 的 SPI 必须能脱离容器与本项目上层单测");
        rule.check(CLASSES);
    }

}
"""

# ---------------------------------------------------------------- 前端
F["frontend/src/api/kernels.ts"] = r"""/** W3 / KRN 内核能力与状态归一 API。 */
import { request } from './http'

export interface KernelCapabilities {
  robot: boolean
  pabot: boolean
  pytest: boolean
  python3: boolean
  kernels: string[]
  timeoutSeconds: number
  maxRetries: number
  sameRunMixedKernels: boolean
}

export interface KernelSelection {
  kernel: string
  source: string
  overridden: boolean
}

export type StatusTone = 'ok' | 'fail' | 'warn' | 'muted'

/** 归一状态的视觉语义：XPASS 按失败处理，XFAIL / SKIP 不算失败。 */
export function caseStatusTone(status: string): StatusTone {
  switch (status.toUpperCase()) {
    case 'PASS': return 'ok'
    case 'FAIL':
    case 'ERROR':
    case 'XPASS': return 'fail'
    case 'XFAIL': return 'warn'
    default: return 'muted'
  }
}

export const kernelsApi = {
  capabilities: () => request<KernelCapabilities>('/api/kernels'),
  select: (projectId: number, setKernel?: string, caseKernel?: string) => {
    const params = new URLSearchParams({ projectId: String(projectId) })
    if (setKernel) params.set('setKernel', setKernel)
    if (caseKernel) params.set('caseKernel', caseKernel)
    return request<KernelSelection>(`/api/kernels/select?${params.toString()}`)
  },
  command: (projectId: number, kernel: string, processes = 1) =>
    request<{ kernel: string; argv: string[]; source: string }>(
      `/api/kernels/command?projectId=${projectId}&kernel=${encodeURIComponent(kernel)}&processes=${processes}`,
    ),
}
"""

F["frontend/src/config/kernels.spec.ts"] = r"""import { describe, expect, it } from 'vitest'
import { caseStatusTone } from '@/api/kernels'

describe('caseStatusTone', () => {
  it('maps six normalized statuses consistently', () => {
    expect(caseStatusTone('PASS')).toBe('ok')
    expect(caseStatusTone('FAIL')).toBe('fail')
    expect(caseStatusTone('ERROR')).toBe('fail')
    expect(caseStatusTone('XPASS')).toBe('fail')
    expect(caseStatusTone('XFAIL')).toBe('warn')
    expect(caseStatusTone('SKIP')).toBe('muted')
  })

  it('is case insensitive and falls back to muted', () => {
    expect(caseStatusTone('pass')).toBe('ok')
    expect(caseStatusTone('unknown')).toBe('muted')
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
print(f"\n[w3-kernel] PASS={pass_n} SKIP={skip_n} TOTAL={len(F)}")
