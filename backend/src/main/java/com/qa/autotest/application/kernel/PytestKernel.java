package com.qa.autotest.application.kernel;

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
 *
 * <p>并行路线见 ADR-0007：走 pytest-xdist（{@code -n N}），不引入平台侧分片合并。
 * 并发为 1 时不加 {@code -n}，与 A2/A3 取证时的串行命令逐字等价。
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
        // ADR-0007：并行统一由 xdist 承担（json-report 由 master 聚合），不用平台分片。
        if (request.processes() > 1) {
            argv.add("-n");
            argv.add(Integer.toString(request.processes()));
        }
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
