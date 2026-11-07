package com.qa.autotest.api.metrics;

import com.qa.autotest.application.metrics.MetricsService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 容量与可观测指标接口（W4）：{@code GET /api/metrics?projectId=…}。
 *
 * <p>一次返回队列、失败率、节点在线率、分片恢复与告警列表；前端顶部指标条与
 * 节点健康页复用同一份读数，避免各自拼 SQL。
 */
@RestController
@RequestMapping("/api")
public class MetricsController {

    private final MetricsService metrics;

    public MetricsController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public ApiResponse<MetricsService.MetricsView> metrics(@RequestParam long projectId) {
        return ApiResponse.ok(metrics.snapshot(projectId));
    }
}
