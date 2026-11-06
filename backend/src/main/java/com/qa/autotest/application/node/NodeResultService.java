package com.qa.autotest.application.node;

import com.qa.autotest.application.execution.ExecutionPaths;
import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.report.ReportIngestService;
import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.node.ExecutionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 节点回传接收（NODX-03）：验节点令牌 → 落产物 → 报告解析入库 → 回传清单取证。
 *
 * <p>认证模型：agent 无用户会话，{@code X-Node-Token} 明文经 SHA-256 与
 * {@code token_hash} 比对（与心跳同一套口径）；错误文案不区分
 * 「节点不存在 / 令牌不对」（防枚举）。入库前核对 {@code run.node_id} 与
 * 来报节点一致——防一个合法节点替别的节点伪造结果。
 */
@Service
public class NodeResultService {

    private static final Logger LOG = LoggerFactory.getLogger(NodeResultService.class);

    private final ExecutionNodeRepository nodes;
    private final RunRepository runs;
    private final ReportIngestService reports;
    private final ExecutionProperties props;

    public NodeResultService(ExecutionNodeRepository nodes, RunRepository runs,
                             ReportIngestService reports, ExecutionProperties props) {
        this.nodes = nodes;
        this.runs = runs;
        this.reports = reports;
        this.props = props;
    }

    /** 回传载荷（节点 → 主控）。 */
    public record NodeResult(String runId,
                             long projectId,
                             boolean success,
                             String reason,
                             String outputXml,
                             List<String> argv,
                             Map<String, String> artifacts) {
    }

    /**
     * 处理回传：返回写入的 output.xml 路径。
     * 幂等：run 已终态（节点同库执行链路已迁移状态）时只落产物与清单，不再动状态。
     */
    @Transactional
    public String receive(String token, NodeResult result) {
        ExecutionNode node = requireNodeWithToken(token);
        if (result == null || result.runId() == null || result.runId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        Long runNodeId = runs.findNodeId(result.projectId(), result.runId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "运行不存在: " + result.runId()));
        if (runNodeId == null || runNodeId != node.id()) {
            // 合法令牌也不能替别的节点交结果
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "run 不属于节点 " + node.name());
        }

        Path runDir = ExecutionPaths.resolve(props.getArtifactsRoot()).resolve(result.runId());
        Path xmlPath = runDir.resolve("shard-0").resolve("output.xml");
        try {
            if (result.outputXml() != null && !result.outputXml().isBlank()) {
                Files.createDirectories(xmlPath.getParent());
                Files.writeString(xmlPath, result.outputXml());
            }
            if (result.argv() != null && !result.argv().isEmpty()) {
                Files.createDirectories(runDir);
                Files.writeString(runDir.resolve("node-cmd-agent.json"),
                        "{\"runId\":\"" + result.runId() + "\",\"argv\":"
                                + toJsonList(result.argv()) + "}");
            }
            writeArtifacts(runDir, result.artifacts());
        } catch (Exception ioFailure) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "回传产物落盘失败: " + ioFailure.getMessage());
        }

        // 报告解析入库（metadata 门禁/计数/test_result 与本机完全同一条链路）
        TenantContext.runAs(null, "node-result", result.projectId(), () -> {
            if (result.outputXml() != null && !result.outputXml().isBlank()) {
                reports.parseAndIngestForAgent(result.projectId(), result.runId(),
                        xmlPath.toString());
            }
        });
        LOG.info("节点 {} 回传 run {}（success={}，output={}）",
                node.name(), result.runId(), result.success(), xmlPath);
        return xmlPath.toString();
    }

    private void writeArtifacts(Path runDir, Map<String, String> artifacts) throws Exception {
        if (artifacts == null) {
            return;
        }
        for (Map.Entry<String, String> entry : artifacts.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank() || name.contains("..") || name.contains("/")) {
                continue;
            }
            Path target = runDir.resolve("shard-0").resolve(name);
            Files.createDirectories(target.getParent());
            Files.write(target, Base64.getDecoder().decode(entry.getValue()));
        }
    }

    private static String toJsonList(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i).replace("\\", "\\\\")
                    .replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private ExecutionNode requireNodeWithToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "缺少节点令牌");
        }
        String hash = ExecutionNodeService.sha256(token);
        return nodes.list().stream()
                .filter(node -> hash.equals(node.tokenHash()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "节点令牌无效"));
    }
}
