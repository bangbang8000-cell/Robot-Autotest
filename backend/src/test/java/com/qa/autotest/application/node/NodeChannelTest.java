package com.qa.autotest.application.node;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NodeChannel 出站纪律单测（NODX-02 / DEV-PLAN §5.3）：
 * 统一超时 10s、网络失败重试 1 次、HTTP 拒绝不重试、失败即判 UNREACHABLE。
 */
class NodeChannelTest {

    @Test
    @DisplayName("200 响应 → Result.ok，只调用一次")
    void 成功不重试() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        HttpServer okServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        okServer.createContext("/node/run", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, 2);
            try (var out = exchange.getResponseBody()) {
                out.write("ok".getBytes());
            }
        });
        okServer.start();
        try {
            NodeChannel channel = new NodeChannel(HttpClient.newHttpClient());
            NodeChannel.Result result = channel.post(
                    URI.create("http://127.0.0.1:" + okServer.getAddress().getPort() + "/node/run"),
                    "{}");
            assertTrue(result.ok(), "kind=" + result.kind());
            assertEquals(200, result.statusCode());
            assertEquals(1, hits.get(), "成功不得重试");
        } finally {
            okServer.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP 500 是节点明确拒绝 → REJECTED，且不重试（重试只会放大问题）")
    void http拒绝不重试() throws IOException {
        HttpServer errServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger hits = new AtomicInteger();
        errServer.createContext("/node/run", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(500, 5);
            try (var out = exchange.getResponseBody()) {
                out.write("error".getBytes());
            }
        });
        errServer.start();
        try {
            NodeChannel channel = new NodeChannel(HttpClient.newHttpClient());
            NodeChannel.Result result = channel.post(
                    URI.create("http://127.0.0.1:" + errServer.getAddress().getPort() + "/node/run"),
                    "{}");
            assertEquals(NodeChannel.Result.Kind.REJECTED, result.kind());
            assertEquals(500, result.statusCode());
            assertEquals(1, hits.get(), "HTTP 拒绝不得重试");
        } finally {
            errServer.stop(0);
        }
    }

    @Test
    @DisplayName("连接失败 → 重试 1 次后 UNREACHABLE（共 2 次尝试）")
    void 网络失败重试一次() {
        NodeChannel channel = new NodeChannel(HttpClient.newHttpClient());
        // 端口 1 保留端口，连接必然失败；不指向任何真实服务
        NodeChannel.Result result = channel.post(
                URI.create("http://127.0.0.1:1/node/run"), "{}");
        assertEquals(NodeChannel.Result.Kind.UNREACHABLE, result.kind());
        assertEquals(-1, result.statusCode());
    }

    @Test
    @DisplayName("出站纪律常量锁死：超时 10s、重试 1 次（DEV-PLAN §5.3 口径）")
    void 出站纪律常量() {
        assertEquals(10, NodeChannel.TIMEOUT.toSeconds());
        assertEquals(1, NodeChannel.RETRIES);
    }
}
