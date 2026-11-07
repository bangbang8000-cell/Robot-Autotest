package com.qa.autotest.api.execution;

import com.qa.autotest.application.execution.FailureClusterService;
import com.qa.autotest.application.execution.FailureClusterView;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.execution.RunRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 失败聚类接口（ORCH-03，v2.1）。
 *
 * <p>路径 {@code /api/failure-clusters}，runId 用业务 ID（{@code run.run_id}）而不是自增主键
 * —— 与报告、打印页、对外文档引用的是同一个标识，避免两套 id 对不上。
 */
@RestController
@RequestMapping("/api/failure-clusters")
public class FailureClusterController {

    private final FailureClusterService clusters;

    public FailureClusterController(FailureClusterService clusters) {
        this.clusters = clusters;
    }

    @GetMapping
    public ApiResponse<FailureClusterView> cluster(@RequestParam long projectId,
                                                   @RequestParam String runId) {
        return ApiResponse.ok(clusters.cluster(projectId, runId));
    }

    /** 跨 run 聚类（W5）：最近 N 轮失败合并统计，scope 标记为 {@code recent:N}。 */
    @GetMapping("/recent")
    public ApiResponse<FailureClusterView> recent(@RequestParam long projectId,
                                                  @RequestParam(defaultValue = "5") int runs) {
        return ApiResponse.ok(clusters.clusterRecent(projectId, runs));
    }

    /** 按簇重跑：安全级拦截沿用 CaseRunService，不在本接口另判一遍。 */
    @PostMapping("/rerun")
    public ApiResponse<RunRecord> rerun(@RequestBody RerunRequest request) {
        return ApiResponse.ok(clusters.rerun(request.projectId(), request.runId(),
                request.clusterKey()));
    }

    public record RerunRequest(long projectId, String runId, String clusterKey) {
    }
}
