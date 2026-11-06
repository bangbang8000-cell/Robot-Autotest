package com.qa.autotest.api.node;

import com.qa.autotest.application.node.NodeAgentRunner;
import com.qa.autotest.application.node.NodeTask;
import com.qa.autotest.application.node.TaskSigner;
import com.qa.autotest.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 节点收任务端点（NODX-03，仅 node profile 装配）。
 *
 * <p>鉴权：不走用户会话（{@code /api/agent/**} 在 WebConfig 白名单），
 * 任务必须带 {@code X-Task-Signature} = HMAC-SHA256(key = sha256(节点明文令牌), body)。
 * 验签失败一律 401，不区分原因（防枚举）。验签通过立即 200 受理，执行在
 * 单线程队列异步跑（节点并发由 pabot processes 控制，agent 不再叠层并发）。
 */
@RestController
@Profile("node")
public class NodeAgentController {

    private static final Logger log = LoggerFactory.getLogger(NodeAgentController.class);

    private final NodeAgentRunner runner;
    private final String tokenHash;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "node-agent-worker");
        thread.setDaemon(true);
        return thread;
    });

    public NodeAgentRunner runner() {
        return runner;
    }

    public NodeAgentController(NodeAgentRunner runner,
                               @org.springframework.beans.factory.annotation.Value("${node.token:}")
                               String nodeToken) {
        this.runner = runner;
        this.tokenHash = nodeToken == null ? "" : nodeToken.trim();
    }

    @PostMapping(value = "/api/agent/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, String>> accept(
            @RequestHeader(value = "X-Task-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        // key = sha256(明文令牌)——与主控存的 token_hash 同一 把 key（TaskSigner 口径）
        String key = TaskSigner.sha256Hex(tokenHash);
        if (!TaskSigner.verify(key, rawBody, signature)) {
            throw new com.qa.autotest.common.BusinessException(
                    com.qa.autotest.common.ErrorCode.UNAUTHENTICATED, "任务签名无效");
        }
        NodeTask task;
        try {
            task = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawBody, NodeTask.class);
        } catch (Exception parseFailure) {
            throw new com.qa.autotest.common.BusinessException(
                    com.qa.autotest.common.ErrorCode.INVALID_ARGUMENT,
                    "任务载荷解析失败: " + parseFailure.getMessage());
        }
        worker.submit(() -> runner.run(task));
        log.info("node-agent: 任务 {} 已受理（projectId={}）", task.runId(), task.projectId());
        return ApiResponse.ok(Map.of("accepted", String.valueOf(task.runId())));
    }
}
