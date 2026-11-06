package com.qa.autotest.application.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 主控 → 节点出站通道（NODX-02 建骨架，NODX-03 接任务投递）。
 *
 * <p>统一出站纪律（DEV-PLAN §5.3，所有主控→节点请求必须经本类）：
 * <ol>
 *   <li><b>统一超时 10s</b>：连接与整体请求共用一个上限，节点卡死时不拖垮主控线程；</li>
 *   <li><b>重试 1 次</b>：只对网络层失败（IOException / 超时）重试一次，
 *       HTTP 4xx/5xx 是节点明确拒绝，重试只会放大问题；</li>
 *   <li><b>失败即判节点离线</b>：两次都失败返回 empty，由调用方决定处置
 *       （NODX-03 起：投递失败的 run 判 FAILED 并提示原因，不排队重投）。</li>
 * </ol>
 *
 * <p>本机执行路径<b>不经过</b>本类——v2.0 本机语义保持不变。
 */
@Component
public class NodeChannel {

    private static final Logger LOG = LoggerFactory.getLogger(NodeChannel.class);

    /** 出站统一超时（秒）。 */
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** 网络层失败后的额外重试次数。 */
    static final int RETRIES = 1;

    private final HttpClient http;

    public NodeChannel() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /** 可注入构造（单测用本地 HttpServer）。 */
    NodeChannel(HttpClient http) {
        this.http = http;
    }

    /**
     * POST JSON 到节点端点。
     *
     * @param uri  节点端点绝对地址（NODX-03 起来自节点登记的接入地址）
     * @param json 任务 JSON（NODX-03 起带签名）
     * @return 2xx 响应体；网络层失败（含重试后仍失败）返回 empty
     */
    public Result post(URI uri, String json) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        IOException lastFailure = null;
        for (int attempt = 0; attempt <= RETRIES; attempt++) {
            if (attempt > 0) {
                LOG.info("节点出站重试（第 {} 次）: {}", attempt, uri);
            }
            try {
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return Result.ok(response.statusCode(), response.body());
                }
                // 明确的 HTTP 拒绝：不重试，直接交调用方处置
                LOG.warn("节点端点拒绝（HTTP {}）: {}", response.statusCode(), uri);
                return Result.rejected(response.statusCode(), response.body());
            } catch (IOException networkFailure) {
                lastFailure = networkFailure;
                LOG.warn("节点出站网络失败（attempt={}）: {} - {}",
                        attempt + 1, uri, networkFailure.getMessage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Result.failed(new IOException("出站被中断", interrupted));
            }
        }
        return Result.failed(lastFailure);
    }

    /** 出站结果：三种终态（成功 / 节点明确拒绝 / 网络不可达）。 */
    public record Result(int statusCode, String body, Kind kind) {

        public enum Kind {OK, REJECTED, UNREACHABLE}

        static Result ok(int statusCode, String body) {
            return new Result(statusCode, body, Kind.OK);
        }

        static Result rejected(int statusCode, String body) {
            return new Result(statusCode, body, Kind.REJECTED);
        }

        static Result failed(IOException cause) {
            return new Result(-1, cause == null ? null : cause.getMessage(), Kind.UNREACHABLE);
        }

        public boolean ok() {
            return kind == Kind.OK;
        }
    }
}
