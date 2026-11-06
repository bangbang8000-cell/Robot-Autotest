package com.qa.autotest.application.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * agent 心跳（NODX-03，仅 node profile）：周期向主控报 {@code /api/nodes/{id}/heartbeat}。
 *
 * <p>agent 崩溃 → 心跳停 → 主控 {@code NodeHeartbeatMonitor} 按连续 3 周期缺席判
 * OFFLINE，并把该节点 RUNNING 的 run 判 FAILED（node_agent_lost，不留悬挂 run）。
 * 心跳失败只打日志不重试——下一周期自然重试，符合「心跳是最终一致的」语义。
 */
@Component
@Profile("node")
public class NodeAgentHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(NodeAgentHeartbeat.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final long nodeId;
    private final String token;
    private final String masterUrl;

    public NodeAgentHeartbeat(@Value("${node.id:0}") long nodeId,
                              @Value("${node.token:}") String token,
                              @Value("${node.master-url:http://127.0.0.1:8081}") String masterUrl) {
        this.nodeId = nodeId;
        this.token = token == null ? "" : token.trim();
        this.masterUrl = masterUrl == null ? "" : masterUrl.replaceAll("/$", "");
    }

    /** 周期 = 主控离线阈值的三分之一（默认 30s），保证阈值窗口内至少 2 次成功心跳。 */
    @Scheduled(fixedDelayString = "${node.heartbeat-interval-seconds:30}000",
               initialDelay = 5_000)
    public void beat() {
        if (nodeId <= 0 || token.isEmpty()) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(masterUrl + "/api/nodes/" + nodeId + "/heartbeat"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Node-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"rfAvailable\":true,\"pytestAvailable\":false}"))
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("node-agent: 心跳被拒（HTTP {}）", response.statusCode());
            }
        } catch (Exception heartbeatFailure) {
            log.warn("node-agent: 心跳失败: {}", heartbeatFailure.getMessage());
        }
    }
}
