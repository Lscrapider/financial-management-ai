package com.scrapider.finance.ai.websocket;

import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.ai.domain.dto.AiChatConversationBindingDTO;
import com.scrapider.finance.security.JwtUtils;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.TokenStore;
import com.scrapider.finance.ai.service.AiChatConversationService;
import io.jsonwebtoken.Claims;
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

    private final JwtUtils jwtUtils;
    private final TokenStore tokenStore;
    private final AiChatConversationService aiChatConversationService;

    public AiChatWebSocketHandshakeInterceptor(
            JwtUtils jwtUtils,
            TokenStore tokenStore,
            AiChatConversationService aiChatConversationService) {
        this.jwtUtils = jwtUtils;
        this.tokenStore = tokenStore;
        this.aiChatConversationService = aiChatConversationService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("accessToken");
        if (token == null || token.isBlank() || this.tokenStore.isBlacklisted(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = this.jwtUtils.parseAccessToken(token);
            LoginUser loginUser = this.loginUser(claims);
            AiChatConversationBindingDTO binding = this.aiChatConversationService.bind(loginUser.getUser().getId());
            attributes.put(LOGIN_USER_ATTRIBUTE, loginUser);
            attributes.put(USER_ID_ATTRIBUTE, binding.userId());
            attributes.put(CONVERSATION_ID_ATTRIBUTE, binding.conversationId());
            attributes.put(CLEANUP_VERSION_ATTRIBUTE, binding.cleanupVersion());
            return true;
        } catch (Exception ex) {
            LOGGER.debug("Invalid websocket access token, reject handshake.", ex);
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

    private LoginUser loginUser(Claims claims) {
        AppUserPO user = new AppUserPO();
        user.setId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        user.setRoleCode(claims.get("role", String.class));
        return new LoginUser(user);
    }
}
