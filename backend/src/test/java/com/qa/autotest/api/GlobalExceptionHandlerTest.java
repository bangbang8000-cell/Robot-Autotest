package com.qa.autotest.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常映射的状态码口径。
 *
 * <p>重点在<b>不该落兜底 500 的那几类</b>：请求本身不成立（路径不存在、方法不支持）是客户端问题，
 * 报 500 会让对外看起来像服务故障，对内把真正的 500 淹进噪声。
 *
 * <p>本类的存在原因很具体：2026-11-04 启动自检（{@code scripts/boot-check.sh}）真实启动应用后
 * 发现 {@code GET /} 返回 500 而不是 404 —— 一个 450 条单测 + 6 条集成测试都没抓到的缺陷，
 * 因为此前没有任何测试起过 Spring 上下文。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("路径不存在返回 404（不是 500）")
    void 未匹配路径返回404() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.NOT_FOUND.code());
        assertThat(response.getBody().message()).contains("路径不存在").contains("/");
    }

    @Test
    @DisplayName("方法不支持仍返回 405（改动不得回退这条）")
    void 方法不支持返回405() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.code());
    }

    @Test
    @DisplayName("未预期异常兜底仍是 500（控制组：别把兜底改没了）")
    void 未预期异常仍是500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
        // 兜底分支不得把异常细节泄给调用方
        assertThat(response.getBody().message()).doesNotContain("boom");
    }
}
