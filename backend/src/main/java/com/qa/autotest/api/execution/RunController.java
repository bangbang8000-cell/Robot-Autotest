package com.qa.autotest.api.execution;

import com.qa.autotest.application.execution.CancelService;
import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.RunLogService;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.application.execution.RunService.CreateRunCommand;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运行记录接口（EXE-01）。取消与日志端点在 EXE-05 / EXE-04 中补齐。
 */
@RestController
@RequestMapping("/api/execution/runs")
public class RunController {

    private final RunService runs;
    private final CancelService cancels;
    private final RunLogService logs;
    private final ExecutionProperties props;

    public RunController(RunService runs, CancelService cancels, RunLogService logs,
                         ExecutionProperties props) {
        this.runs = runs;
        this.cancels = cancels;
        this.logs = logs;
        this.props = props;
    }

    @PostMapping
    public ApiResponse<RunRecord> create(@RequestBody CreateRunRequest request) {
        int timeout = request.timeoutSeconds() == null || request.timeoutSeconds() <= 0
                ? props.getRunTimeoutSeconds() : request.timeoutSeconds();
        CreateRunCommand command = new CreateRunCommand(
                request.projectId(),
                request.testSetId(),
                request.suitePaths() == null ? List.of() : request.suitePaths(),
                request.concurrency() == null ? 1 : request.concurrency(),
                request.environmentId(),
                request.deviceKeys() == null ? List.of() : request.deviceKeys(),
                request.runRequestId(),
                request.priority() == null ? 0 : request.priority(),
                timeout,
                request.includeTag());
        return ApiResponse.ok(runs.create(command));
    }

    @GetMapping("/{runId}")
    public ApiResponse<RunRecord> get(@RequestParam long projectId,
                                      @PathVariable String runId) {
        return ApiResponse.ok(runs.get(projectId, runId));
    }

    @GetMapping
    public ApiResponse<PageResult<RunRecord>> page(@RequestParam long projectId,
                                                   @RequestParam(required = false) String state,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        RunState filter = state == null || state.isBlank() ? null : RunState.fromDb(state);
        return ApiResponse.ok(runs.page(projectId, filter, page, Math.min(size, 100)));
    }

    @GetMapping("/{runId}/log")
    public ApiResponse<List<RunLogService.LogLine>> log(@RequestParam long projectId,
                                                        @PathVariable String runId,
                                                        @RequestParam(defaultValue = "200") int limit) {
        runs.get(projectId, runId);
        int capped = Math.min(Math.max(limit, 1), RunLogService.RING_CAPACITY);
        return ApiResponse.ok(logs.recent(runId, capped));
    }

    @PostMapping("/{runId}/cancel")
    public ApiResponse<RunRecord> cancel(@PathVariable String runId,
                                         @RequestParam long projectId,
                                         @RequestBody(required = false) CancelRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(cancels.cancel(projectId, runId, reason));
    }

    public record CancelRequest(String reason) {
    }

    public record CreateRunRequest(Long projectId,
                                   Long testSetId,
                                   List<String> suitePaths,
                                   Integer concurrency,
                                   Long environmentId,
                                   List<String> deviceKeys,
                                   String runRequestId,
                                   Integer priority,
                                   Integer timeoutSeconds,
                                   String includeTag) {
    }
}
