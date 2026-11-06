package com.qa.autotest.api.execution;

import com.qa.autotest.application.execution.RunMetadataService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行记录 metadata 补录（HRD-02：{@code run:metadata-patch}）。
 *
 * <p>路径挂在 {@code /api/execution/runs/{runId}} 下，但写操作需要「执行-更新」权限，
 * 与查看运行（执行-读）区分开。
 */
@RestController
@RequestMapping("/api/execution/runs/{runId}/metadata")
public class RunMetadataController {

    private final RunMetadataService service;

    public RunMetadataController(RunMetadataService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> list(@PathVariable String runId,
                                                 @RequestParam long projectId) {
        return ApiResponse.ok(service.list(projectId, runId));
    }

    @PatchMapping
    public ApiResponse<Map<String, String>> patch(@PathVariable String runId,
                                                  @RequestParam long projectId,
                                                  @RequestBody(required = false) PatchRequest body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "metadata 不能为空");
        }
        return ApiResponse.ok(service.patch(projectId, runId, body.metadata()));
    }

    /** 补录请求体：{@code {"metadata": {"DUT": "GPU-01"}}}，空串表示删除该项。 */
    public record PatchRequest(Map<String, String> metadata) {
    }
}
