package com.qa.autotest.common;

/**
 * 统一响应体。所有控制器一律返回这个结构，前端只写一套解包逻辑。
 *
 * @param code    错误码数值，0 表示成功
 * @param codeName 错误码英文名，便于日志与文档引用
 * @param message 给人看的说明（可为空）
 * @param data    业务数据（失败时为 null）
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(int code, String codeName, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.codeName(), "OK", data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), errorCode.codeName(), message, null);
    }

    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.code();
    }
}
