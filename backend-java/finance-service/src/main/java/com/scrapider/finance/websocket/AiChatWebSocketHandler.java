package com.scrapider.finance.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.AgentRunStartMessageDTO;
import com.scrapider.finance.domain.dto.AgentSessionDTO;
import com.scrapider.finance.domain.param.AiChatWebSocketMessageParam;
import com.scrapider.finance.domain.vo.AiChatWebSocketMessageVO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.AgentMessagePublisher;
import com.scrapider.finance.service.AgentSessionService;
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
    private final String dataGatewayUrl;
    private final String callbackUrl;

    public AiChatWebSocketHandler(
            ObjectMapper objectMapper,
            AiChatWebSocketSessionRegistry sessionRegistry,
            AgentSessionService agentSessionService,
            AgentMessagePublisher agentMessagePublisher,
            @Value("${finance.agent.data-gateway-url:http://localhost:8081/internal/agent/data/query}")
                    String dataGatewayUrl,
            @Value("${finance.agent.callback-url:http://localhost:8081/internal/agent/callback}")
                    String callbackUrl) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.agentSessionService = agentSessionService;
        this.agentMessagePublisher = agentMessagePublisher;
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
        if (StrUtil.hasBlank(param.conversationId(), param.messageId(), param.content())) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        LoginUser loginUser = this.sessionRegistry.loginUser(session.getId())
                .orElseThrow(() -> new IllegalStateException("websocket login user is missing"));
        try {
            AgentSessionDTO agentSession = this.agentSessionService.create(
                    loginUser.getUser().getId(),
                    loginUser.getUsername(),
                    param.conversationId(),
                    param.messageId());
            this.agentMessagePublisher.publishRunStart(AgentRunStartMessageDTO.from(
                    agentSession,
                    param.content(),
                    this.dataGatewayUrl,
                    this.callbackUrl));
        } catch (Exception ex) {
            this.send(session, AiChatWebSocketMessageVO.finalAnswer(
                    param.conversationId(),
                    param.messageId(),
                    "Agent 启动失败，请稍后再试。",
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            throw ex;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.sessionRegistry.unregister(session.getId());
    }

    private void send(WebSocketSession session, AiChatWebSocketMessageVO payload) throws IOException {
        session.sendMessage(new TextMessage(this.objectMapper.writeValueAsString(payload)));
    }
}
