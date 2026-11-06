package com.qa.autotest.api;

import com.qa.autotest.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查（ENVX-02 / FR-v2.0-54）。
 *
 * <p>两个端点分工不同，不能合并：{@code /healthz} 由 8001 统一入口与容器探针打，
 * 不查库、不依赖任何外部服务，只证明「进程活着」；{@code /api/health} 由运维打，
 * 会真查一次库，证明「可服务」。把 DB 检查塞进存活探针，数据库抖一下就会触发无意义重启。
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 存活探针：不查库、不查依赖。 */
    @GetMapping("/healthz")
    public ApiResponse<Map<String, Object>> live() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("app", "robot-autotest");
        body.put("check", "liveness");
        return ApiResponse.ok(body);
    }

    /** 就绪探针：真查库；DB 不可用时明确标 DEGRADED，不吞异常也不抛 500。 */
    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "robot-autotest");
        body.put("check", "readiness");
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            boolean up = one != null && one == 1;
            body.put("database", up ? "UP" : "DOWN");
            body.put("status", up ? "UP" : "DEGRADED");
        } catch (RuntimeException ex) {
            body.put("database", "DOWN");
            body.put("status", "DEGRADED");
            body.put("reason", ex.getClass().getSimpleName());
        }
        return ApiResponse.ok(body);
    }
}
