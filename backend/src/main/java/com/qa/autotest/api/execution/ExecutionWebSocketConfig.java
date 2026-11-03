package com.qa.autotest.api.execution;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 注册实时日志端点；同源限制交由部署层（反向代理）处理，本版放开通配以便本地联调。 */
@Configuration
@EnableWebSocket
public class ExecutionWebSocketConfig implements WebSocketConfigurer {

    private final RunLogWebSocketHandler handler;

    public ExecutionWebSocketConfig(RunLogWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/runs/*")
                .setAllowedOriginPatterns("*");
    }
}
