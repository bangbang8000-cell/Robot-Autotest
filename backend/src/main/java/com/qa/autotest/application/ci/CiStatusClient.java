package com.qa.autotest.application.ci;

/**
 * GitHub commit status 客户端端口（PLN-03）。
 *
 * <p>令牌作为独立参数传入，<b>不放进请求对象</b>，避免对象被日志打印时带出密钥。
 */
public interface CiStatusClient {

    CiStatusResponse post(CiStatusRequest request, String token);

    /**
     * 回写请求（不含令牌）。
     *
     * @param repo     GitHub owner/repo
     * @param state    success / failure
     * @param targetUrl 报告页地址
     */
    record CiStatusRequest(String apiBaseUrl,
                           String repo,
                           String commitSha,
                           String state,
                           String targetUrl,
                           String description,
                           String context,
                           int timeoutSeconds) {
    }

    /** 回写响应；只保留状态码与截断后的响应体，便于排障且防止超长日志。 */
    record CiStatusResponse(int statusCode, String body) {

        public boolean accepted() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
