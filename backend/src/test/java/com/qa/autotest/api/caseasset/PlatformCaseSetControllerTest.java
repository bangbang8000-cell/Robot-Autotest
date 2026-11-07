package com.qa.autotest.api.caseasset;

import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.application.caseasset.TestSetService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.common.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N9 接口层：平台用例集库的路由与错误映射（只读）。
 */
class PlatformCaseSetControllerTest {

    private TestSetService sets;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        sets = mock(TestSetService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformCaseSetController(sets))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("列表透出 keyword/page/size，size 上限 200")
    void 列表路由() throws Exception {
        when(sets.platformPage("login", 2, 200)).thenReturn(
                new PageResult<>(List.of(), 0L, 2, 200));
        mvc.perform(get("/api/platform/case-sets")
                        .param("keyword", "login")
                        .param("page", "2")
                        .param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));
        verify(sets).platformPage("login", 2, 200);
    }

    @Test
    @DisplayName("成员不存在映射 404")
    void 成员404() throws Exception {
        when(sets.platformItems(anyLong()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "平台用例集不存在: 404"));
        mvc.perform(get("/api/platform/case-sets/404/items"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("非管理员映射 403")
    void 非管理员403() throws Exception {
        when(sets.platformPage(null, 1, 50))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "平台用例集库只对管理员开放"));
        mvc.perform(get("/api/platform/case-sets"))
                .andExpect(status().isForbidden());
    }
}
