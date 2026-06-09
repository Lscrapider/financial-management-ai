package com.scrapider.finance.ai.config;

import com.scrapider.finance.ai.websocket.AiChatWebSocketHandler;
import com.scrapider.finance.ai.websocket.AiChatWebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AiChatWebSocketHandler aiChatWebSocketHandler;
    private final AiChatWebSocketHandshakeInterceptor aiChatWebSocketHandshakeInterceptor;

    public WebSocketConfig(
            AiChatWebSocketHandler aiChatWebSocketHandler,
            AiChatWebSocketHandshakeInterceptor aiChatWebSocketHandshakeInterceptor) {
        this.aiChatWebSocketHandler = aiChatWebSocketHandler;
        this.aiChatWebSocketHandshakeInterceptor = aiChatWebSocketHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.aiChatWebSocketHandler, "/ws/ai-chat", "/api/ws/ai-chat")
                .addInterceptors(this.aiChatWebSocketHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
