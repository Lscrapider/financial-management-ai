package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.AiTokenUsageConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageTrendVO;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import com.scrapider.finance.domain.enums.AiTokenUsagePhaseEnum;
import com.scrapider.finance.domain.enums.AiTokenUsageSourceEnum;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import com.scrapider.finance.manage.AiTokenUsageLogManage;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AiTokenUsageServiceImpl implements AiTokenUsageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiTokenUsageServiceImpl.class);
    private static final int DEFAULT_OVERVIEW_DAYS = 7;
    private static final int MAX_OVERVIEW_DAYS = 365;
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 365;

    private final AiTokenUsageLogManage aiTokenUsageLogManage;
    private final ObjectMapper objectMapper;

    public AiTokenUsageServiceImpl(AiTokenUsageLogManage aiTokenUsageLogManage, ObjectMapper objectMapper) {
        this.aiTokenUsageLogManage = aiTokenUsageLogManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTokenUsageLogVO recordDeepSeekResponse(
            JsonNode response,
            Long userId,
            AiTokenUsageSourceEnum source,
            String sourceRefId,
            AiTokenUsagePhaseEnum phase) {
        if (response == null || response.isNull() || response.path("usage").isMissingNode()) {
            throw new IllegalArgumentException("DeepSeek response usage must not be empty");
        }
        AiTokenUsageLogPO saved = this.aiTokenUsageLogManage.saveLog(AiTokenUsageLogPO.fromDeepSeekResponse(
                response,
                userId,
                source,
                sourceRefId,
                phase));
        return AiTokenUsageLogVO.fromPO(saved);
    }

    @Override
    public void recordChatResponse(ChatResponse response) {
        if (response == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null || usage.getTotalTokens() <= 0) {
            return;
        }
        try {
            this.aiTokenUsageLogManage.saveLog(AiTokenUsageConverter.fromChatResponse(
                    response,
                    usage,
                    this.objectMapper));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record AI token usage", ex);
        }
    }

    @Override
    public void recordAgentTokenUsage(AgentSessionDTO session, JsonNode payload) {
        if (session == null || payload == null || payload.isNull()) {
            return;
        }
        JsonNode events = payload.path("tokenUsageEvents");
        if (!events.isArray() || events.size() == 0) {
            return;
        }
        for (JsonNode event : events) {
            this.recordAgentTokenUsageEvent(session, event);
        }
    }

    private void recordAgentTokenUsageEvent(AgentSessionDTO session, JsonNode event) {
        if (event == null || event.isNull()) {
            return;
        }
        AiTokenUsagePhaseEnum phase = AiTokenUsagePhaseEnum.fromCode(event.path("phase").asText(""));
        if (phase == null) {
            LOGGER.warn("Skip AI agent token usage with invalid phase sessionId={} phase={}",
                    session.agentSessionId(),
                    event.path("phase").asText(""));
            return;
        }
        if (event.path("totalTokens").asInt(0) <= 0) {
            return;
        }
        try {
            this.aiTokenUsageLogManage.saveLog(AiTokenUsageLogPO.fromAgentUsageEvent(
                    event,
                    session.userId(),
                    session.conversationId(),
                    phase));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record AI agent token usage sessionId={}", session.agentSessionId(), ex);
        }
    }

    @Override
    public AiTokenUsageOverviewVO overview(Integer days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(this.normalize(days, DEFAULT_OVERVIEW_DAYS, MAX_OVERVIEW_DAYS));
        return AiTokenUsageOverviewVO.fromDTO(this.aiTokenUsageLogManage.summarySince(startTime));
    }

    @Override
    public List<AiTokenUsageTrendVO> trends(Integer days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(this.normalize(days, DEFAULT_TREND_DAYS, MAX_TREND_DAYS));
        return this.aiTokenUsageLogManage.trendSince(startTime).stream()
                .map(AiTokenUsageTrendVO::fromDTO)
                .toList();
    }

    private int normalize(Integer value, int defaultValue, int maxValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
