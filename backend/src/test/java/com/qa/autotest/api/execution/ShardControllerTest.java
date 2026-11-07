package com.qa.autotest.api.execution;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.execution.ShardService;
import com.qa.autotest.application.execution.ShardService.RunShardsView;
import com.qa.autotest.application.execution.ShardService.ShardView;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2 分片看板接口层：路由、查询参数透传、plan body 解析与错误码映射。
 *
 * <p>服务层语义由 {@code ShardServiceTest} 覆盖；这里只验「参数名对不对、body 有没有解析对」——
 * 前端按 {@code suitePaths} / {@code nodeCount} 传，若这里写成别的名字，单测服务层发现不了。
 */
class ShardControllerTest {

    private ShardService shards;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        shards = mock(ShardService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ShardController(shards))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static RunShardsView view(int total, int terminal, String aggregate) {
        return new RunShardsView("RUN-W2", List.of(
                new ShardView(0, "DONE", 11L, "node-11",
                        "2026-11-07T02:00:30Z", 2L, 1, 6, 1,
                        List.of("suites/a.robot", "suites/b.robot"),
                        "2026-11-07T01:59:40Z", "2026-11-07T02:00:30Z"),
                new ShardView(1, "LEASED", 12L, "node-12",
                        "2026-11-07T02:05:00Z", 1L, 1, 0, 0,
                        List.of("suites/c.robot"), "2026-11-07T02:00:10Z", null)),
                total, terminal, total == terminal ? 9 : 6, 1,
                total == terminal, aggregate);
    }

    @Test
    @DisplayName("列表：projectId / runId 透传，分片字段与聚合状态原样返回")
    void list_字段透传() throws Exception {
        when(shards.view(7L, "RUN-W2")).thenReturn(view(2, 1, "IN_PROGRESS"));

        mvc.perform(get("/api/execution/runs/RUN-W2/shards").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value("RUN-W2"))
                .andExpect(jsonPath("$.data.totalShards").value(2))
                .andExpect(jsonPath("$.data.terminalShards").value(1))
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.aggregateState").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.shards[0].shardIndex").value(0))
                .andExpect(jsonPath("$.data.shards[0].state").value("DONE"))
                .andExpect(jsonPath("$.data.shards[0].nodeId").value(11))
                .andExpect(jsonPath("$.data.shards[0].fencingToken").value(2))
                .andExpect(jsonPath("$.data.shards[0].suitePaths[0]").value("suites/a.robot"))
                .andExpect(jsonPath("$.data.shards[1].state").value("LEASED"))
                .andExpect(jsonPath("$.data.shards[1].finishedAt").doesNotExist());

        verify(shards).view(7L, "RUN-W2");
    }

    @Test
    @DisplayName("规划：suitePaths / nodeCount 从 body 解析并透传，operatorId 由会话补")
    void plan_body解析() throws Exception {
        when(shards.plan(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(view(2, 0, "IN_PROGRESS"));

        mvc.perform(post("/api/execution/runs/RUN-W2/shards/plan")
                        .param("projectId", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suitePaths\":[\"suites/a.robot\",\"suites/b.robot\","
                                + "\"suites/c.robot\"],\"nodeCount\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalShards").value(2))
                .andExpect(jsonPath("$.data.complete").value(false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> paths = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Integer> nodes = ArgumentCaptor.forClass(Integer.class);
        verify(shards).plan(eq(7L), eq("RUN-W2"), paths.capture(), nodes.capture(), eq(null));
        assertEquals(List.of("suites/a.robot", "suites/b.robot", "suites/c.robot"), paths.getValue());
        assertEquals(2, nodes.getValue());
    }

    @Test
    @DisplayName("规划：nodeCount 缺省传 null，由服务层按 4 节点上限决定")
    void plan_nodeCount可缺省() throws Exception {
        when(shards.plan(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(view(1, 0, "IN_PROGRESS"));

        mvc.perform(post("/api/execution/runs/RUN-W2/shards/plan")
                        .param("projectId", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suitePaths\":[\"suites/a.robot\"]}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Integer> nodes = ArgumentCaptor.forClass(Integer.class);
        verify(shards).plan(eq(7L), eq("RUN-W2"), any(), nodes.capture(), eq(null));
        assertNull(nodes.getValue());
    }

    @Test
    @DisplayName("聚合：run 不存在 → 404")
    void aggregate_错误码映射() throws Exception {
        when(shards.aggregate(7L, "RUN-W2"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: RUN-W2"));

        mvc.perform(post("/api/execution/runs/RUN-W2/shards/aggregate").param("projectId", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codeName").value("NOT_FOUND"));
    }
}
