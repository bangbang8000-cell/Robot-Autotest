package com.qa.autotest.api;

import com.qa.autotest.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康探测端点（FR-v0.1-06）。
 *
 * <p>前端请求封装（FR-v0.1-11）与 CI 冒烟都打这个端点；它不查库、不依赖外部服务，
 * 用来证明「骨架活着」。
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/_ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("app", "robot-autotest");
        body.put("version", "v0.1");
        return ApiResponse.ok(body);
    }
}
