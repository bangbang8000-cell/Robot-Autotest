package com.qa.autotest.api.node;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.execution.ShardService;
import com.qa.autotest.application.execution.ShardService.ClaimResult;
import com.qa.autotest.application.execution.ShardService.RunShardsView;
import com.qa.autotest.application.node.ExecutionNodeService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2 agent 侧分片端点接线：节点令牌头、默认租约 300 秒、无片可领返回空 data、回写参数透传。
 *
 * <p>这两条走节点令牌而不是用户会话，所以放在 NodeController 上；这里只验路由与反序列化。
 */
class NodeShardEndpointTest {

    private ExecutionNodeService nodes;
    private ShardService shards;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        nodes = mock(ExecutionNodeService.class);
        shards = mock(ShardService.class);
        mvc = MockMvcBuilders.standaloneSetup(new NodeController(nodes, shards))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("领取：X-Node-Token 透传，leaseSeconds 缺省 300，返回分片与 fencingToken")
    void claim_默认租约与字段() throws Exception {
        when(shards.claim(anyLong(), anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(new ClaimResult(1, List.of("suites/b.robot"), 3L, 2,
                        "2026-11-07T02:05:00Z"));

        mvc.perform(post("/api/nodes/11/shards/claim")
                        .header("X-Node-Token", "token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":7,\"runId\":\"RUN-W2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shardIndex").value(1))
                .andExpect(jsonPath("$.data.suitePaths[0]").value("suites/b.robot"))
                .andExpect(jsonPath("$.data.fencingToken").value(3))
                .andExpect(jsonPath("$.data.attempt").value(2))
                .andExpect(jsonPath("$.data.leaseUntil").value("2026-11-07T02:05:00Z"));

        verify(shards).claim(11L, "token-abc", 7L, "RUN-W2", 300);
    }

    @Test
    @DisplayName("领取：显式 leaseSeconds 不被默认值覆盖；无片可领 data 为空")
    void claim_指定租约与空结果() throws Exception {
        when(shards.claim(11L, "token-abc", 7L, "RUN-W2", 60)).thenReturn(null);

        mvc.perform(post("/api/nodes/11/shards/claim")
                        .header("X-Node-Token", "token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":7,\"runId\":\"RUN-W2\",\"leaseSeconds\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(shards).claim(11L, "token-abc", 7L, "RUN-W2", 60);
    }

    @Test
    @DisplayName("领取：令牌错 → 401 由服务层异常映射")
    void claim_令牌错映射() throws Exception {
        when(shards.claim(anyLong(), anyString(), anyLong(), anyString(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED, "节点令牌校验失败"));

        mvc.perform(post("/api/nodes/11/shards/claim")
                        .header("X-Node-Token", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":7,\"runId\":\"RUN-W2\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("回写：shardIndex / fencingToken / 计数 / success 全部透传")
    void complete_参数透传() throws Exception {
        when(shards.complete(anyLong(), anyString(), anyLong(), anyString(), anyInt(),
                anyLong(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new RunShardsView("RUN-W2", List.of(), 1, 1, 6, 1, true, "FAILED"));

        mvc.perform(post("/api/nodes/11/shards/complete")
                        .header("X-Node-Token", "token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":7,\"runId\":\"RUN-W2\",\"shardIndex\":0,"
                                + "\"fencingToken\":3,\"success\":false,\"testCount\":6,"
                                + "\"failCount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.aggregateState").value("FAILED"));

        verify(shards).complete(11L, "token-abc", 7L, "RUN-W2", 0, 3L, false, 6, 1);
    }

    @Test
    @DisplayName("回写：fencing_token 过期 → 409")
    void complete_令牌过期映射() throws Exception {
        when(shards.complete(anyLong(), anyString(), anyLong(), anyString(), anyInt(),
                anyLong(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT,
                        "分片租约已被重派，本次回写被拒（fencing_token 过期）"));

        mvc.perform(post("/api/nodes/11/shards/complete")
                        .header("X-Node-Token", "token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":7,\"runId\":\"RUN-W2\",\"shardIndex\":0,"
                                + "\"fencingToken\":1,\"success\":true,\"testCount\":3,"
                                + "\"failCount\":0}"))
                .andExpect(status().isConflict());
    }
}
