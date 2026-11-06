package com.qa.autotest.api.node;

import com.qa.autotest.application.node.ExecutionNodeService;
import com.qa.autotest.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行节点 API（NODX-01，FR-v2.1-13/14）。
 *
 * <p>平台级资源：注册 / 列表 / 停启用走管理员会话；心跳走节点令牌
 * （{@code X-Node-Token} 头），与会话体系分开——agent 上没有用户登录态。
 */
@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final ExecutionNodeService nodes;

    public NodeController(ExecutionNodeService nodes) {
        this.nodes = nodes;
    }

    public record RegisterRequest(String name, Boolean rfAvailable, Boolean pytestAvailable,
                                  Integer maxConcurrency,
                                  /** 节点 agent 接入地址（NODX-03），可空。 */
                                  String agentBaseUrl) {
    }

    @PostMapping("/register")
    public ApiResponse<ExecutionNodeService.RegisterResult> register(
            @RequestBody RegisterRequest request) {
        return ApiResponse.ok(nodes.register(
                request.name(),
                Boolean.TRUE.equals(request.rfAvailable()),
                Boolean.TRUE.equals(request.pytestAvailable()),
                request.maxConcurrency() == null ? 1 : request.maxConcurrency(),
                request.agentBaseUrl()));
    }

    @PostMapping("/{id}/heartbeat")
    public ApiResponse<ExecutionNodeService.NodeView> heartbeat(
            @PathVariable long id,
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestBody(required = false) HeartbeatRequest request) {
        HeartbeatRequest body = request == null ? new HeartbeatRequest(null, null) : request;
        return ApiResponse.ok(nodes.heartbeat(id, token,
                Boolean.TRUE.equals(body.rfAvailable()),
                Boolean.TRUE.equals(body.pytestAvailable())));
    }

    public record HeartbeatRequest(Boolean rfAvailable, Boolean pytestAvailable) {
    }

    @GetMapping
    public ApiResponse<List<ExecutionNodeService.NodeView>> list() {
        return ApiResponse.ok(nodes.list());
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<ExecutionNodeService.NodeView> disable(@PathVariable long id) {
        return ApiResponse.ok(nodes.disable(id));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<ExecutionNodeService.NodeView> enable(@PathVariable long id) {
        return ApiResponse.ok(nodes.enable(id));
    }
}
