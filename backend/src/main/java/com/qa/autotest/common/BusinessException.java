package com.qa.autotest.common;

/**
 * 业务异常：携带错误码，由接口层的全局异常处理器翻译成统一响应体。
 *
 * <p>只用于「可预期的失败」（参数不对、状态非法、找不到）；不可预期的异常交给兜底处理，
 * 不要在业务代码里 catch 后包装成它。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
