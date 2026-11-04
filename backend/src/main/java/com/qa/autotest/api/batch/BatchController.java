package com.qa.autotest.api.batch;

import com.qa.autotest.application.batch.GenericBatchService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用批量端点（UX-02b / FR-v0.2-40）：资产（设备/链路/凭据）与用户列表复用同一契约。
 *
 * <p>与 {@code POST /api/projects/{id}/cases/batch} 并列，语义一致：
 * 部分成功 200 + 逐条结果，参数级错误才 400。
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class BatchController {

    private final GenericBatchService batches;

    public BatchController(GenericBatchService batches) {
        this.batches = batches;
    }

    @PostMapping("/batch")
    public ApiResponse<GenericBatchService.BatchResult> batch(
            @PathVariable long projectId,
            @RequestBody GenericBatchService.BatchRequest request) {
        return ApiResponse.ok(batches.batch(projectId, request));
    }
}
