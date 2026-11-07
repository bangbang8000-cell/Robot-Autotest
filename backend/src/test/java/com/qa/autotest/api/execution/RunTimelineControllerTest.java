package com.qa.autotest.api.execution;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.observability.RunTimelineService;
import com.qa.autotest.application.observability.RunTimelineService.RunTimelineView;
import com.qa.autotest.application.observability.RunTimelineService.TimelineEvent;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** W4 时间线接口：路径变量 + projectId 透传，事件数组字段与前端约定一致。 */
class RunTimelineControllerTest {

    private RunTimelineService timeline;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        timeline = mock(RunTimelineService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RunTimelineController(timeline))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/execution/runs/{runId}/timeline 返回事件流")
    void returnsTimeline() throws Exception {
        when(timeline.timeline(29L, "RUN-W4")).thenReturn(new RunTimelineView("RUN-W4", "FINISHED",
                "2026-11-07T03:00:00Z", "2026-11-07T03:00:05Z", "2026-11-07T03:02:00Z",
                List.of(new TimelineEvent("2026-11-07T03:00:00Z", "RUN_ENQUEUED", -1, null, 0, 0, 0, 0),
                        new TimelineEvent("2026-11-07T03:00:10Z", "SHARD_DONE", 0, 11L, 1, 2, 5, 0))));

        mvc.perform(get("/api/execution/runs/RUN-W4/timeline").param("projectId", "29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("RUN-W4"))
                .andExpect(jsonPath("$.data.state").value("FINISHED"))
                .andExpect(jsonPath("$.data.events.length()").value(2))
                .andExpect(jsonPath("$.data.events[0].type").value("RUN_ENQUEUED"))
                .andExpect(jsonPath("$.data.events[1].shardIndex").value(0))
                .andExpect(jsonPath("$.data.events[1].nodeId").value(11))
                .andExpect(jsonPath("$.data.events[1].fencingToken").value(2))
                .andExpect(jsonPath("$.data.events[1].testCount").value(5));

        verify(timeline).timeline(29L, "RUN-W4");
    }

    @Test
    @DisplayName("run 不存在 → 404 NOT_FOUND，而不是空事件流")
    void missingRunIs404() throws Exception {
        when(timeline.timeline(29L, "NOPE")).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: NOPE"));

        mvc.perform(get("/api/execution/runs/NOPE/timeline").param("projectId", "29"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codeName").value("NOT_FOUND"));
    }
}
