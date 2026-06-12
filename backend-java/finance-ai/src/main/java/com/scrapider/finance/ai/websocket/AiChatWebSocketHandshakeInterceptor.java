package com.scrapider.finance.ai.websocket;

import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.ai.domain.dto.AiChatConversationBindingDTO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.ai.service.AiChatConversationService;
import com.scrapider.finance.ai.service.AiChatWebSocketTicketService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AiChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String LOGIN_USER_ATTRIBUTE = "loginUser";
    public static final String CONVERSATION_ID_ATTRIBUTE = "conversationId";
    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String CLEANUP_VERSION_ATTRIBUTE = "cleanupVersion";

    private static final Logger LOGGER = LoggerFactory.getLogger(AiChatWebSocketHandshakeInterceptor.class);

    private final AiChatWebSocketTicketService aiChatWebSocketTicketService;
    private final AiChatConversationService aiChatConversationService;

    public AiChatWebSocketHandshakeInterceptor(
            AiChatWebSocketTicketService aiChatWebSocketTicketService,
            AiChatConversationService aiChatConversationService) {
        this.aiChatWebSocketTicketService = aiChatWebSocketTicketService;
        this.aiChatConversationService = aiChatConversationService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            LoginUser loginUser = this.aiChatWebSocketTicketService.consume(ticket)
                    .orElse(null);
            if (loginUser == null || !this.enabled(loginUser)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            AiChatConversationBindingDTO binding = this.aiChatConversationService.bind(loginUser.getUser().getId());
            attributes.put(LOGIN_USER_ATTRIBUTE, loginUser);
            attributes.put(USER_ID_ATTRIBUTE, binding.userId());
            attributes.put(CONVERSATION_ID_ATTRIBUTE, binding.conversationId());
            attributes.put(CLEANUP_VERSION_ATTRIBUTE, binding.cleanupVersion());
            return true;
        } catch (Exception ex) {
            LOGGER.debug("Invalid websocket ticket, reject handshake.", ex);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private boolean enabled(LoginUser loginUser) {
        AppUserPO user = loginUser.getUser();
        return user != null && Boolean.TRUE.equals(user.getEnabled());
    }
}
