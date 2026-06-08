package com.scrapider.finance.websocket;

import com.scrapider.finance.security.LoginUser;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class AiChatWebSocketSessionRegistry {

    private final ConcurrentMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        LoginUser loginUser = (LoginUser) session.getAttributes()
                .get(AiChatWebSocketHandshakeInterceptor.LOGIN_USER_ATTRIBUTE);
        if (loginUser != null) {
            this.sessions.put(session.getId(), new SessionInfo(session, loginUser));
        }
    }

    public void unregister(String sessionId) {
        this.sessions.remove(sessionId);
    }

    public Optional<LoginUser> loginUser(String sessionId) {
        return Optional.ofNullable(this.sessions.get(sessionId))
                .map(SessionInfo::loginUser);
    }

    public Map<String, LoginUser> sessions() {
        Map<String, LoginUser> result = new ConcurrentHashMap<>();
        this.sessions.forEach((sessionId, sessionInfo) -> result.put(sessionId, sessionInfo.loginUser()));
        return Map.copyOf(result);
    }

    public void sendToUser(Long userId, String payload) throws IOException {
        for (SessionInfo sessionInfo : this.sessions.values()) {
            WebSocketSession session = sessionInfo.session();
            if (sessionInfo.loginUser().getUser().getId().equals(userId) && session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        }
    }

    public void sendToConversation(Long userId, String conversationId, String payload) throws IOException {
        for (SessionInfo sessionInfo : this.sessions.values()) {
            WebSocketSession session = sessionInfo.session();
            String boundConversationId = (String) session.getAttributes()
                    .get(AiChatWebSocketHandshakeInterceptor.CONVERSATION_ID_ATTRIBUTE);
            if (sessionInfo.loginUser().getUser().getId().equals(userId)
                    && conversationId.equals(boundConversationId)
                    && session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        }
    }

    private record SessionInfo(WebSocketSession session, LoginUser loginUser) {
    }
}
