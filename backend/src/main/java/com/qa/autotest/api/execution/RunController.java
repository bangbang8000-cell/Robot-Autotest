package com.qa.autotest.api.execution;

import com.qa.autotest.application.execution.CancelService;
import com.qa.autotest.application.execution.CaseRunService;
import com.qa.autotest.application.execution.ExecutionProperties;
import com.qa.autotest.application.execution.QuickRunService;
import com.qa.autotest.application.execution.RunDispatcher;
import com.qa.autotest.application.execution.RunLogFileService;
import com.qa.autotest.application.execution.RunLogService;
import com.qa.autotest.application.execution.RunService;
import com.qa.autotest.application.execution.RunService.CreateRunCommand;
import com.qa.autotest.application.execution.TestSetRunService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.execution.RunOrigin;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 运行记录接口（EXE-01 + EXEC-01）。
 *
 * <p>触发入口有三个：
 * <ul>
 *   <li>{@code POST /api/execution/quick-run}：页面「手动执行」，EXECUTION:EXECUTE，立即派发；</li>
 *   <li>{@code POST /api/execution/test-sets/{id}/run}：用例集「立即执行」，按用例编号解析套件；</li>
 *   <li>{@code POST /api/execution/cases/run}：勾选台账用例「执行所选」，按标签/测试名解析到测试级；</li>
 *   <li>{@code POST /api/execution/runs}：<b>CI 专用</b>直建端点，落库后同样交给公共派发器，
 *       不再留下永久 queued 的僵尸 run。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/execution")
public class RunController {

    private final RunService runs;
    private final CancelService cancels;
    private final RunLogService logs;
    private final RunLogFileService logFiles;
    private final ExecutionProperties props;
    private final QuickRunService quickRuns;
    private final TestSetRunService testSetRuns;
    private final CaseRunService caseRuns;
    private final RunDispatcher dispatcher;

    public RunController(RunService runs, CancelService cancels, RunLogService logs,
                         ExecutionProperties props, QuickRunService quickRuns,
                         TestSetRunService testSetRuns, CaseRunService caseRuns,
                         RunLogFileService logFiles, RunDispatcher dispatcher) {
        this.runs = runs;
        this.cancels = cancels;
        this.logs = logs;
        this.props = props;
        this.quickRuns = quickRuns;
        this.testSetRuns = testSetRuns;
        this.caseRuns = caseRuns;
        this.logFiles = logFiles;
        this.dispatcher = dispatcher;
    }

    /** 页面手动执行（EXEC-01 / FR-v0.2-59~60）。 */
    @PostMapping("/quick-run")
    public ApiResponse<RunRecord> quickRun(@RequestBody QuickRunRequest request) {
        return ApiResponse.ok(quickRuns.trigger(new QuickRunService.QuickRunCommand(
                request.projectId(),
                request.testSetId(),
                request.suitePaths(),
                request.environmentId(),
                request.deviceKeys(),
                request.concurrency(),
                request.timeoutSeconds(),
                request.includeTag(),
                request.runRequestId(),
                null,
                request.source() == null || request.source().isBlank()
                        ? "runs-view" : request.source())));
    }

    /** 用例集立即执行（EXEC-01 / FR-v0.2-63）。 */
    @PostMapping("/test-sets/{testSetId}/run")
    public ApiResponse<RunRecord> runTestSet(@PathVariable long testSetId,
                                              @RequestParam long projectId,
                                              @RequestBody(required = false) TestSetRunRequest request) {
        TestSetRunRequest body = request == null ? new TestSetRunRequest(null, null, null, null, null, null)
                : request;
        return ApiResponse.ok(testSetRuns.run(new TestSetRunService.TestSetRunCommand(
                projectId, testSetId, body.environmentId(), body.deviceKeys(),
                body.concurrency(), body.timeoutSeconds(), body.includeTag(),
                body.runRequestId())));
    }

    /** 勾选台账用例直接执行（EXEC-02，v0.4 候选提前实现）：后端解析绑定，前端只传用例 id。 */
    @PostMapping("/cases/run")
    public ApiResponse<RunRecord> runCases(@RequestParam long projectId,
                                           @RequestBody(required = false) CaseRunRequest request) {
        CaseRunRequest body = request == null
                ? new CaseRunRequest(null, null, null, null, null, null, null) : request;
        return ApiResponse.ok(caseRuns.run(new CaseRunService.CaseRunCommand(
                projectId, body.caseIds(), body.environmentId(), body.deviceKeys(),
                body.concurrency(), body.timeoutSeconds(), body.includeTag(),
                body.runRequestId())));
    }

    /** CI 专用直建端点（FR-v0.2-61）：落库后立即派发，不再停留 queued。 */
    @PostMapping("/runs")
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
                request.includeTag(),
                null);
        RunRecord created = runs.create(command, new RunOrigin(RunOrigin.CI, null, null));
        quickRuns.dispatch(created, command);
        return ApiResponse.ok(created);
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<RunRecord> get(@RequestParam long projectId,
                                      @PathVariable String runId) {
        return ApiResponse.ok(runs.get(projectId, runId));
    }

    @GetMapping("/runs")
    public ApiResponse<PageResult<RunRecord>> page(@RequestParam long projectId,
                                                   @RequestParam(required = false) String state,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        RunState filter = state == null || state.isBlank() ? null : RunState.fromDb(state);
        return ApiResponse.ok(runs.page(projectId, filter, page, Math.min(size, 100)));
    }

    @GetMapping("/runs/{runId}/log")
    public ApiResponse<List<RunLogService.LogLine>> log(@RequestParam long projectId,
                                                        @PathVariable String runId,
                                                        @RequestParam(defaultValue = "200") int limit) {
        runs.get(projectId, runId);
        int capped = Math.min(Math.max(limit, 1), RunLogService.RING_CAPACITY);
        return ApiResponse.ok(logs.recent(runId, capped));
    }

    /** 下载全量日志（EXEC-06 / FR-v0.4-21）：返回 run.log 原始文件，不走 JSON 信封。 */
    @GetMapping("/runs/{runId}/log/download")
    public ResponseEntity<Resource> downloadLog(@RequestParam long projectId,
                                                @PathVariable String runId) {
        RunRecord run = runs.get(projectId, runId);
        Resource resource = logFiles.download(run);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("run-" + runId + ".log", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(resource);
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<RunRecord> cancel(@PathVariable String runId,
                                         @RequestParam long projectId,
                                         @RequestBody(required = false) CancelRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(cancels.cancel(projectId, runId, reason));
    }

    /**
     * 排队位次（EXEC-10 残余 / FR-v0.4-14）。
     *
     * <p>先按 {@code projectId} 校验 run 归属（越权直接 403/404，由 {@link RunService#get} 抛错），
     * 再问派发器。派发器只在进程内知道位次，重启或 run 已结束时会查不到——此时按 DB 状态回答：
     * {@code queued} 表示「排队但位次未知（position=0）」，{@code running} 表示在执行，
     * {@code terminal} 表示已结束。
     */
    @GetMapping("/runs/{runId}/pending-activity")
    public ApiResponse<RunDispatcher.QueueStatus> pendingActivity(@PathVariable String runId,
                                                                  @RequestParam long projectId) {
        RunRecord run = runs.get(projectId, runId);
        int maxParallel = Math.max(1, props.getMaxParallel());
        return ApiResponse.ok(dispatcher.status(runId).orElseGet(() -> {
            String state = switch (run.state()) {
                case QUEUED -> "queued";
                case PREPARING, RUNNING -> "running";
                default -> "terminal";
            };
            return new RunDispatcher.QueueStatus(runId, state, 0, 0, 0, maxParallel);
        }));
    }

    public record CancelRequest(String reason) {
    }

    public record QuickRunRequest(Long projectId,
                                  Long testSetId,
                                  List<String> suitePaths,
                                  Long environmentId,
                                  List<String> deviceKeys,
                                  Integer concurrency,
                                  Integer timeoutSeconds,
                                  String includeTag,
                                  String runRequestId,
                                  String source) {
    }

    public record TestSetRunRequest(Long environmentId,
                                    List<String> deviceKeys,
                                    Integer concurrency,
                                    Integer timeoutSeconds,
                                    String includeTag,
                                    String runRequestId) {
    }

    public record CaseRunRequest(List<Long> caseIds,
                                 Long environmentId,
                                 List<String> deviceKeys,
                                 Integer concurrency,
                                 Integer timeoutSeconds,
                                 String includeTag,
                                 String runRequestId) {
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
