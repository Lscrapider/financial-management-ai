package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.ai.service.AiChatConversationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConversationHistoryActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "conversation.history";

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final AiChatConversationService aiChatConversationService;

    public ConversationHistoryActionHandler(AiChatConversationService aiChatConversationService) {
        this.aiChatConversationService = aiChatConversationService;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        int limit = this.normalizeLimit(param);
        List<Map<String, Object>> rows = this.aiChatConversationService.listHistory(
                session.userId(),
                session.conversationId(),
                session.messageId(),
                limit);
        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                rows,
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "conversationId", session.conversationId(),
                        "limit", limit),
                null);
    }

    private int normalizeLimit(AgentDataQueryParam param) {
        Integer limit = param.limit();
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
