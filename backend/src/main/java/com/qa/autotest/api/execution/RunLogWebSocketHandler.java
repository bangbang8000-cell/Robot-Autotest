package com.qa.autotest.api.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.autotest.application.execution.RunLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 实时日志频道（EXE-04）：{@code /ws/runs/{runId}}。
 *
 * <p>连上先补最近 200 行，之后按 100ms/≤50 行推送；断开即退订。
 * 推送内容只含日志，不含凭据与 argv 中的口令（DUT 侧另有脱敏约束）。
 */
@Component
public class RunLogWebSocketHandler extends TextWebSocketHandler {

    private final RunLogService logs;
    private final ObjectMapper json;
    private final Map<String, RunLogService.Registration> sessions = new ConcurrentHashMap<>();

    public RunLogWebSocketHandler(RunLogService logs, ObjectMapper json) {
        this.logs = logs;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String runId = runIdOf(session);
        if (runId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        RunLogService.Registration registration =
                logs.subscribe(runId, lines -> send(session, runId, lines));
        sessions.put(session.getId(), registration);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RunLogService.Registration registration = sessions.remove(session.getId());
        if (registration != null) {
            registration.close();
        }
    }

    private void send(WebSocketSession session, String runId, List<RunLogService.LogLine> lines) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "log");
            payload.put("runId", runId);
            payload.put("lines", lines.stream().map(line -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("seq", line.seq());
                item.put("tsGen", line.tsGen().toString());
                item.put("source", line.source());
                item.put("level", line.level());
                item.put("text", line.text());
                return item;
            }).toList());
            synchronized (session) {
                session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        } catch (Exception ex) {
            // 单次发送失败不影响其他订阅者；断开由容器回收。
        }
    }

    private static String runIdOf(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        int index = path.lastIndexOf('/');
        String runId = index >= 0 ? path.substring(index + 1) : "";
        return runId.isBlank() ? null : runId;
    }
}
