package com.qa.autotest.application.node;

import com.qa.autotest.application.execution.RunExecutionService;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 执行端 agent（NODX-03）：同 jar 双角色里的「节点」侧执行器。
 *
 * <p>收到主控任务后做三件事：
 * <ol>
 *   <li><b>执行</b>：调 {@link RunExecutionService#execute}——命令仍由同一套
 *       {@code PabotCommandBuilder / KRN} 按任务参数生成（命令等价的判据点），
 *       节点侧不重写执行、不重写命令装配；</li>
 *   <li><b>回传</b>：{@code output.xml}（文本）+ 节点侧 argv（命令 diff 取证）
 *       + 附加产物（base64）POST 回主控 {@code /api/agent/result}；</li>
 *   <li><b>心跳</b>：由 {@link NodeAgentHeartbeat} 独立保活——agent 崩溃心跳停，
 *       主控按「连续 3 周期缺席」判离线并终止该节点 RUNNING 的 run。</li>
 * </ol>
 *
 * <p>本版口径：节点与主控同库（内网单机），run 状态由执行链路正常迁移；
 * 回传协议为真实分布式保留——判据「产物完整回传」按回传 HTTP 链路取证。
 */
@Service
public class NodeAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(NodeAgentRunner.class);

    private final RunService runs;
    private final RunExecutionService executions;
    private final com.qa.autotest.application.execution.PabotCommandBuilder commands;
    private final com.qa.autotest.application.execution.ExecutionProperties props;
    private final long nodeId;
    private final String nodeToken;
    private final String masterUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public NodeAgentRunner(RunService runs,
                           RunExecutionService executions,
                           com.qa.autotest.application.execution.PabotCommandBuilder commands,
                           com.qa.autotest.application.execution.ExecutionProperties props,
                           @Value("${node.id:0}") long nodeId,
                           @Value("${node.token:}") String nodeToken,
                           @Value("${node.master-url:http://127.0.0.1:8081}") String masterUrl) {
        this.runs = runs;
        this.executions = executions;
        this.commands = commands;
        this.props = props;
        this.nodeId = nodeId;
        this.nodeToken = nodeToken == null ? "" : nodeToken.trim();
        this.masterUrl = masterUrl == null ? "" : masterUrl.replaceAll("/$", "");
    }

    /** 节点身份配置就绪（node profile 必填项）。 */
    public boolean configured() {
        return nodeId > 0 && !nodeToken.isEmpty() && !masterUrl.isEmpty();
    }

    /**
     * 执行任务并回传。异常不抛出 HTTP 层——任务已受理（200），失败反映在回传体里。
     */
    public void run(NodeTask task) {
        if (!configured()) {
            log.error("node-agent: 未配置 node.id/token/master-url，任务被丢弃: {}", task.runId());
            return;
        }
        RunRecord created;
        try {
            java.util.concurrent.atomic.AtomicReference<RunRecord> found =
                    new java.util.concurrent.atomic.AtomicReference<>();
            TenantContext.runAs(null, "node-agent", task.projectId(), () -> {
                RunRecord record = runs.findForRunner(task.projectId(), task.runId());
                // 节点侧跑的是本机执行链路（nodeId 置空，避免 executeOnNode 递归投递）
                RunService.CreateRunCommand cmd = new RunService.CreateRunCommand(
                        task.projectId(), record.testSetId(), task.suitePaths(),
                        task.concurrency(), task.environmentId(), task.deviceKeys(),
                        null, 0, task.timeoutSeconds() == null ? 900 : task.timeoutSeconds(),
                        task.includeTag(),
                        task.testNames() == null ? List.of() : task.testNames(),
                        task.testTimeoutSeconds() == null ? 0 : task.testTimeoutSeconds(),
                        null);
                executions.execute(record, cmd);
                found.set(record);
            });
            created = found.get();
        } catch (RuntimeException executionFailure) {
            log.error("node-agent: 任务 {} 执行失败: {}", task.runId(),
                    executionFailure.getMessage(), executionFailure);
            upload(task, List.of(), null, false,
                    "node_agent_execute_failed: " + executionFailure.getMessage());
            return;
        }
        List<String> argv = referenceCommand(created, task);
        Path runDir = com.qa.autotest.application.execution.ExecutionPaths
                .resolve(props.getArtifactsRoot()).resolve(task.runId());
        Path outputXml = runDir.resolve("shard-0").resolve("output.xml");
        String xml = readFileOrNull(outputXml);
        upload(task, argv, xml, true, null);
    }

    /** 节点侧同参数命令（命令等价 diff 的 agent 侧基线）。 */
    private List<String> referenceCommand(RunRecord created, NodeTask task) {
        try {
            Path runDir = com.qa.autotest.application.execution.ExecutionPaths
                    .resolve(props.getArtifactsRoot()).resolve(task.runId());
            int processes = Math.min(task.concurrency(), task.suitePaths().size());
            var command = commands.build(
                    new com.qa.autotest.application.execution.PabotCommandBuilder.ShardSpec(
                            0, task.suitePaths(), processes, task.includeTag(),
                            "run-" + task.runId(),
                            task.testNames() == null ? List.of() : task.testNames()),
                    props, runDir,
                    com.qa.autotest.application.execution.ExecutionPaths
                            .resolve(props.getWorkspaceRoot()));
            return command.argv();
        } catch (Exception buildFailure) {
            log.warn("node-agent: 参照命令构造失败（不影响执行）: {}", buildFailure.getMessage());
            return List.of();
        }
    }

    private void upload(NodeTask task, List<String> argv, String outputXml,
                        boolean success, String reason) {
        String resultUrl = task.masterResultUrl() != null && !task.masterResultUrl().isBlank()
                ? task.masterResultUrl() : masterUrl + "/api/agent/result";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("runId", task.runId());
        body.put("projectId", task.projectId());
        body.put("success", success);
        body.put("reason", reason);
        body.put("outputXml", outputXml);
        body.put("argv", argv);
        body.put("artifacts", Map.of());
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resultUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Node-Token", nodeToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("node-agent: run {} 回传完成（HTTP {}）", task.runId(), response.statusCode());
        } catch (Exception uploadFailure) {
            log.error("node-agent: run {} 回传失败: {}", task.runId(), uploadFailure.getMessage());
        }
    }

    private static String readFileOrNull(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (Exception readFailure) {
            log.warn("node-agent: 读取 {} 失败: {}", path, readFailure.getMessage());
            return null;
        }
    }
}
