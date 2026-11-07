package com.qa.autotest.api.metrics;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.metrics.MetricsService;
import com.qa.autotest.application.metrics.MetricsService.AlertView;
import com.qa.autotest.application.metrics.MetricsService.MetricsView;
import com.qa.autotest.application.metrics.MetricsService.NodeMetrics;
import com.qa.autotest.application.metrics.MetricsService.QueueMetrics;
import com.qa.autotest.application.metrics.MetricsService.RunMetrics;
import com.qa.autotest.application.metrics.MetricsService.ShardMetrics;
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

/** W4 指标接口：查询参数名、字段名与告警数组的结构（前端按这些键取值）。 */
class MetricsControllerTest {

    private MetricsService metrics;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        metrics = mock(MetricsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MetricsController(metrics))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/metrics?projectId= 透传 projectId 并返回嵌套读数")
    void returnsNestedMetrics() throws Exception {
        when(metrics.snapshot(29L)).thenReturn(new MetricsView(29L, "2026-11-07T03:00:00Z",
                new RunMetrics(20, 18, 2, 1, 0.1),
                new QueueMetrics(1, 0, 1),
                new NodeMetrics(2, 2, 0, 0, 1.0),
                new ShardMetrics(12, 1, 0, 2),
                List.of(new AlertView("queueDepth", "WARN", 1, 5, "队列深度 1 已到阈值 5"))));

        mvc.perform(get("/api/metrics").param("projectId", "29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(29))
                .andExpect(jsonPath("$.data.runs.failureRate").value(0.1))
                .andExpect(jsonPath("$.data.queue.depth").value(1))
                .andExpect(jsonPath("$.data.nodes.onlineRate").value(1.0))
                .andExpect(jsonPath("$.data.shards.recoveredAttempts").value(2))
                .andExpect(jsonPath("$.data.alerts[0].key").value("queueDepth"));

        verify(metrics).snapshot(29L);
    }
}
