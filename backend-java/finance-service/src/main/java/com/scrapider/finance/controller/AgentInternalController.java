package com.scrapider.finance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.AgentSessionDTO;
import com.scrapider.finance.domain.param.AgentCallbackParam;
import com.scrapider.finance.domain.param.AgentDataQueryParam;
import com.scrapider.finance.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.domain.vo.AiChatWebSocketMessageVO;
import com.scrapider.finance.service.AgentSignatureService;
import com.scrapider.finance.websocket.AiChatWebSocketSessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent")
public class AgentInternalController {

    private final AgentSignatureService agentSignatureService;
    private final AiChatWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public AgentInternalController(
            AgentSignatureService agentSignatureService,
            AiChatWebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper) {
        this.agentSignatureService = agentSignatureService;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request, @RequestBody String rawBody) throws Exception {
        AgentSessionDTO session = this.agentSignatureService.verify(request, rawBody);
        AgentCallbackParam param = this.objectMapper.readValue(rawBody, AgentCallbackParam.class);
        this.requireSameSession(session, param);
        if ("final_answer".equals(param.eventType())) {
            AiChatWebSocketMessageVO message = AiChatWebSocketMessageVO.finalAnswer(
                    session.conversationId(),
                    session.messageId(),
                    this.answerContent(param.payload()),
                    OffsetDateTime.now().toString());
            this.sessionRegistry.sendToUser(session.userId(), this.objectMapper.writeValueAsString(message));
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/data/query")
    public ResponseEntity<AgentDataGatewayResponseVO> dataQuery(
            HttpServletRequest request,
            @RequestBody String rawBody) throws Exception {
        AgentSessionDTO session = this.agentSignatureService.verify(request, rawBody);
        AgentDataQueryParam param = this.objectMapper.readValue(rawBody, AgentDataQueryParam.class);
        if (param.action() == null || !session.scopes().contains(param.action())) {
            return ResponseEntity.status(403)
                    .body(new AgentDataGatewayResponseVO(
                            param.action(),
                            false,
                            java.util.List.of(),
                            java.util.Map.of(),
                            new AgentDataGatewayResponseVO.Error(
                                    "ACTION_FORBIDDEN",
                                    "当前 Agent Session 不允许调用 " + param.action())));
        }
        return ResponseEntity.ok(AgentDataGatewayResponseVO.empty(param.action()));
    }

    private void requireSameSession(AgentSessionDTO session, AgentCallbackParam param) {
        if (param == null
                || !session.agentSessionId().equals(param.agentSessionId())
                || !session.conversationId().equals(param.conversationId())
                || !session.messageId().equals(param.messageId())) {
            throw new IllegalArgumentException("agent callback session mismatch");
        }
    }

    private String answerContent(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "Agent 已返回结果，但内容为空。";
        }
        JsonNode answer = payload.get("answer");
        if (answer != null && answer.isTextual()) {
            return answer.asText();
        }
        JsonNode content = payload.get("content");
        if (content != null && content.isTextual()) {
            return content.asText();
        }
        return payload.toString();
    }
}
