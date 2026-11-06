package com.qa.autotest.api.node;

import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.application.node.NodeResultService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 节点回传端点（NODX-03，主控侧）：agent 跑完任务后把 output.xml 与产物回传到这里。
 *
 * <p>鉴权不走用户会话（{@code WebConfig} 白名单放行 {@code /api/agent/**}），
 * 以 {@code X-Node-Token} 明文令牌认证（与心跳同一套哈希比对口径）。
 */
@RestController
@RequestMapping("/api/agent")
public class NodeResultController {

    private final NodeResultService results;

    public NodeResultController(NodeResultService results) {
        this.results = results;
    }

    /** 节点回传结果：验令牌 → 落产物 → 报告解析入库。 */
    @PostMapping("/result")
    public ApiResponse<Map<String, String>> result(
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestBody NodeResultService.NodeResult body) {
        String xmlPath = results.receive(token, body);
        return ApiResponse.ok(Map.of("outputXml", xmlPath));
    }
}
