package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageLogVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageTrendVO;
import com.scrapider.finance.ai.service.AiTokenUsageService;
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
    private static final String DEEPSEEK_PROVIDER = "deepseek";

    private final AiTokenUsageLogManage aiTokenUsageLogManage;
    private final ObjectMapper objectMapper;

    public AiTokenUsageServiceImpl(AiTokenUsageLogManage aiTokenUsageLogManage, ObjectMapper objectMapper) {
        this.aiTokenUsageLogManage = aiTokenUsageLogManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTokenUsageLogVO recordDeepSeekResponse(JsonNode response) {
        if (response == null || response.isNull() || response.path("usage").isMissingNode()) {
            throw new IllegalArgumentException("DeepSeek response usage must not be empty");
        }
        AiTokenUsageLogPO saved = this.aiTokenUsageLogManage.saveLog(AiTokenUsageLogPO.fromDeepSeekResponse(response));
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
            this.aiTokenUsageLogManage.saveLog(this.toTokenUsageLog(response, usage));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record AI token usage", ex);
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

    private AiTokenUsageLogPO toTokenUsageLog(ChatResponse response, Usage usage) {
        AiTokenUsageLogPO log = new AiTokenUsageLogPO();
        log.setProvider(DEEPSEEK_PROVIDER);
        log.setResponseId(response.getMetadata().getId());
        log.setObjectType("chat.completion");
        log.setModel(response.getMetadata().getModel());
        log.setFinishReason(this.finishReason(response));
        log.setPromptTokens(usage.getPromptTokens());
        log.setCompletionTokens(usage.getCompletionTokens());
        log.setTotalTokens(usage.getTotalTokens());
        log.setCachedTokens(this.cachedTokens(usage));
        log.setReasoningTokens(this.reasoningTokens(usage));
        log.setRawResponse(this.objectMapper.valueToTree(response.getMetadata()).toString());
        log.setOccurredAt(LocalDateTime.now());
        return log;
    }

    private String finishReason(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    private Integer cachedTokens(Usage usage) {
        JsonNode node = this.objectMapper.valueToTree(usage.getNativeUsage());
        return node.path("prompt_tokens_details").path("cached_tokens").asInt(0);
    }

    private Integer reasoningTokens(Usage usage) {
        JsonNode node = this.objectMapper.valueToTree(usage.getNativeUsage());
        return node.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
    }
}
