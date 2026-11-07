package com.qa.autotest.api.execution;

import com.qa.autotest.application.observability.RunTimelineService;
import com.qa.autotest.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运行时间线接口（W4）：执行过程可视化的事件源。 */
@RestController
@RequestMapping("/api/execution")
public class RunTimelineController {

    private final RunTimelineService timeline;

    public RunTimelineController(RunTimelineService timeline) {
        this.timeline = timeline;
    }

    @GetMapping("/runs/{runId}/timeline")
    public ApiResponse<RunTimelineService.RunTimelineView> timeline(@PathVariable String runId,
                                                                    @RequestParam long projectId) {
        return ApiResponse.ok(timeline.timeline(projectId, runId));
    }
}
