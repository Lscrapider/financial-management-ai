package com.scrapider.finance.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.AgentRunStartMessageDTO;
import com.scrapider.finance.domain.dto.AgentSessionDTO;
import com.scrapider.finance.domain.param.AiChatWebSocketMessageParam;
import com.scrapider.finance.domain.vo.AiChatWebSocketMessageVO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.AgentMessagePublisher;
import com.scrapider.finance.service.AgentSessionService;
import com.scrapider.finance.service.AiChatConversationService;
import cn.hutool.core.util.StrUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AiChatWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_MESSAGE_TYPE = "user_message";

    private final ObjectMapper objectMapper;
    private final AiChatWebSocketSessionRegistry sessionRegistry;
    private final AgentSessionService agentSessionService;
    private final AgentMessagePublisher agentMessagePublisher;
    private final AiChatConversationService aiChatConversationService;
    private final String dataGatewayUrl;
    private final String callbackUrl;

    public AiChatWebSocketHandler(
            ObjectMapper objectMapper,
            AiChatWebSocketSessionRegistry sessionRegistry,
            AgentSessionService agentSessionService,
            AgentMessagePublisher agentMessagePublisher,
            AiChatConversationService aiChatConversationService,
            @Value("${finance.agent.data-gateway-url:http://localhost:8081/internal/agent/data/query}")
                    String dataGatewayUrl,
            @Value("${finance.agent.callback-url:http://localhost:8081/internal/agent/callback}")
                    String callbackUrl) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.agentSessionService = agentSessionService;
        this.agentMessagePublisher = agentMessagePublisher;
        this.aiChatConversationService = aiChatConversationService;
        this.dataGatewayUrl = dataGatewayUrl;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.sessionRegistry.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        AiChatWebSocketMessageParam param = this.objectMapper.readValue(
                message.getPayload(),
                AiChatWebSocketMessageParam.class);
        if (!USER_MESSAGE_TYPE.equals(param.type())) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (StrUtil.hasBlank(param.messageId(), param.content())) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String boundConversationId = (String) session.getAttributes()
                .get(AiChatWebSocketHandshakeInterceptor.CONVERSATION_ID_ATTRIBUTE);
        if (StrUtil.isBlank(boundConversationId)) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        LoginUser loginUser = this.sessionRegistry.loginUser(session.getId())
                .orElseThrow(() -> new IllegalStateException("websocket login user is missing"));
        try {
            this.aiChatConversationService.saveUserMessage(
                    loginUser.getUser().getId(),
                    boundConversationId,
                    param.messageId(),
                    param.content());
            AgentSessionDTO agentSession = this.agentSessionService.create(
                    loginUser.getUser().getId(),
                    loginUser.getUsername(),
                    boundConversationId,
                    param.messageId());
            this.agentMessagePublisher.publishRunStart(AgentRunStartMessageDTO.from(
                    agentSession,
                    param.content(),
                    this.dataGatewayUrl,
                    this.callbackUrl));
        } catch (Exception ex) {
            this.send(session, AiChatWebSocketMessageVO.finalAnswer(
                    boundConversationId,
                    param.messageId(),
                    "Agent 启动失败，请稍后再试。",
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            throw ex;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(AiChatWebSocketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        String conversationId = (String) session.getAttributes()
                .get(AiChatWebSocketHandshakeInterceptor.CONVERSATION_ID_ATTRIBUTE);
        this.aiChatConversationService.release(userId, conversationId);
        this.sessionRegistry.unregister(session.getId());
    }

    private void send(WebSocketSession session, AiChatWebSocketMessageVO payload) throws IOException {
        session.sendMessage(new TextMessage(this.objectMapper.writeValueAsString(payload)));
    }
}
