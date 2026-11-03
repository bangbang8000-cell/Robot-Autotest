package com.qa.autotest.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.application.ci.CiStatusClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * GitHub commit status 客户端实现（PLN-03）。
 *
 * <p>调用 {@code POST /repos/{owner}/{repo}/statuses/{sha}}。令牌只出现在请求头，
 * <b>不写日志、不进异常消息</b>；异常只抛固定类型文案，失败原因由调用方落到
 * {@code ci_trigger.writeback_error}。
 */
@Component
public class GitHubCiStatusClient implements CiStatusClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int BODY_MAX = 300;

    private final HttpClient http;

    public GitHubCiStatusClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CiStatusResponse post(CiStatusRequest request, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", request.state());
        body.put("target_url", request.targetUrl());
        body.put("description", request.description());
        body.put("context", request.context());

        String json;
        try {
            json = JSON.writeValueAsString(body);
        } catch (IOException serialization) {
            throw new IllegalStateException("github_status_payload_error");
        }

        String base = request.apiBaseUrl() == null
                ? "https://api.github.com" : request.apiBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri = URI.create(base + "/repos/" + request.repo() + "/statuses/" + request.commitSha());

        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, request.timeoutSeconds())))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "qa-platform-ci")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return new CiStatusResponse(response.statusCode(), truncate(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("github_status_interrupted");
        } catch (IOException io) {
            throw new IllegalStateException("github_status_io_error");
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= BODY_MAX ? value : value.substring(0, BODY_MAX);
    }
}
