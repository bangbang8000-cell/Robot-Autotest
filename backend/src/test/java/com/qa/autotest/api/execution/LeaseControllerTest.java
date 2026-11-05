package com.qa.autotest.api.execution;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.execution.LeaseAdminService;
import com.qa.autotest.application.execution.LeaseAdminService.ForceReleaseResult;
import com.qa.autotest.application.execution.LeaseView;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ResourceLeaseRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EXEC-07 接口层：只读列表的字段透传、强制释放的参数校验与错误码映射。
 *
 * <p>服务层逻辑由 {@code LeaseAdminServiceTest} 覆盖，这里只验"路由与参数接线"——
 * 参数名写错（`resourceKey` / `confirm`）、错误码没映射，单测服务层是发现不了的。
 */
class LeaseControllerTest {

    private ResourceLeaseRepository leases;
    private LeaseAdminService admin;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        leases = mock(ResourceLeaseRepository.class);
        admin = mock(LeaseAdminService.class);
        LeaseController controller = new LeaseController(leases, mock(AccessControlService.class), admin);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("租约列表返回资源键、占用运行与剩余秒数")
    void list_字段透传() throws Exception {
        when(leases.listByProject(7L)).thenReturn(List.of(
                new LeaseView("port:DUT-A:eth0", "RUN-1", 5L, 7L,
                        Instant.parse("2026-11-05T07:00:00Z"),
                        Instant.parse("2026-11-05T08:00:00Z"), 125L, "held")));
        mvc.perform(get("/api/execution/leases").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].resourceKey").value("port:DUT-A:eth0"))
                .andExpect(jsonPath("$.data[0].runId").value("RUN-1"))
                .andExpect(jsonPath("$.data[0].remainingSeconds").value(125))
                .andExpect(jsonPath("$.data[0].state").value("held"));
    }

    @Test
    @DisplayName("强制释放：三个查询参数原样进服务，返回改动后的运行状态")
    void forceRelease_参数接线() throws Exception {
        when(admin.forceRelease(7L, "port:DUT-A:eth0", "RUN-1"))
                .thenReturn(new ForceReleaseResult("RUN-1", "FAILED", 1));
        mvc.perform(post("/api/execution/leases/force-release")
                        .param("projectId", "7")
                        .param("resourceKey", "port:DUT-A:eth0")
                        .param("confirm", "RUN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("RUN-1"))
                .andExpect(jsonPath("$.data.runState").value("FAILED"))
                .andExpect(jsonPath("$.data.releasedLeases").value(1));
    }

    @Test
    @DisplayName("无生效租约 → 404；confirm 不匹配 → 400")
    void forceRelease_错误码映射() throws Exception {
        when(admin.forceRelease(7L, "port:DUT-A:eth0", "RUN-1"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "该资源没有生效中的租约"));
        mvc.perform(post("/api/execution/leases/force-release")
                        .param("projectId", "7")
                        .param("resourceKey", "port:DUT-A:eth0")
                        .param("confirm", "RUN-1"))
                .andExpect(status().isNotFound());

        doThrow(new BusinessException(ErrorCode.INVALID_ARGUMENT, "二次确认不足"))
                .when(admin).forceRelease(7L, "port:DUT-A:eth0", "");
        mvc.perform(post("/api/execution/leases/force-release")
                        .param("projectId", "7")
                        .param("resourceKey", "port:DUT-A:eth0")
                        .param("confirm", ""))
                .andExpect(status().isBadRequest());
    }
}
