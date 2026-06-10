package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.domain.vo.AiChatWebSocketMessageVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.ai.service.AgentDataGatewayService;
import com.scrapider.finance.ai.websocket.AiChatWebSocketSessionRegistry;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentDataGatewayServiceImpl implements AgentDataGatewayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentDataGatewayServiceImpl.class);

    private final Map<String, AgentDataActionHandler> actionHandlers;
    private final AiChatWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public AgentDataGatewayServiceImpl(
            List<AgentDataActionHandler> handlers,
            AiChatWebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper) {
        Map<String, AgentDataActionHandler> handlerMap = new LinkedHashMap<>();
        for (AgentDataActionHandler handler : handlers) {
            String action = handler.action();
            if (StrUtil.isBlank(action)) {
                throw new IllegalStateException("Agent data action handler action 不能为空: "
                        + handler.getClass().getName());
            }
            AgentDataActionHandler exists = handlerMap.putIfAbsent(action, handler);
            if (exists != null) {
                throw new IllegalStateException("Agent data action handler 重复注册: " + action);
            }
        }
        this.actionHandlers = Map.copyOf(handlerMap);
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentDataGatewayResponseVO query(AgentSessionDTO session, AgentDataQueryParam param) {
        if (param == null || StrUtil.isBlank(param.action())) {
            return this.error(null, "ACTION_REQUIRED", "数据查询 action 不能为空");
        }
        AgentDataActionHandler handler = this.actionHandlers.get(param.action());
        if (handler == null) {
            return this.error(param.action(), "UNSUPPORTED_ACTION", "暂不支持数据查询 action: " + param.action());
        }
        this.notifyProgress(session, handler, param, "running", handler.runningMessage(param));
        return handler.handle(session, param);
    }

    private void notifyProgress(
            AgentSessionDTO session,
            AgentDataActionHandler handler,
            AgentDataQueryParam param,
            String status,
            String content) {
        if (session == null || session.userId() == null || StrUtil.isBlank(session.conversationId())) {
            return;
        }
        try {
            AiChatWebSocketMessageVO message = AiChatWebSocketMessageVO.agentProgress(
                    session.conversationId(),
                    session.messageId(),
                    status,
                    StrUtil.isBlank(content) ? null : content,
                    OffsetDateTime.now().toString());
            this.sessionRegistry.sendToConversation(
                    session.userId(),
                    session.conversationId(),
                    this.objectMapper.writeValueAsString(message));
        } catch (Exception ex) {
            LOGGER.warn(
                    "agent data query progress notify failed action={} status={} handler={}",
                    param == null ? null : param.action(),
                    status,
                    handler.getClass().getSimpleName(),
                    ex);
        }
    }

    private AgentDataGatewayResponseVO error(String action, String code, String message) {
        return new AgentDataGatewayResponseVO(
                action,
                false,
                List.of(),
                Map.of("queriedAt", OffsetDateTime.now().toString()),
                new AgentDataGatewayResponseVO.Error(code, message));
    }
}
