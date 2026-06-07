package com.scrapider.finance.ai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.time.LocalDateTime;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public final class AiTokenUsageConverter {

    private static final String DEEPSEEK_PROVIDER = "deepseek";

    private AiTokenUsageConverter() {
    }

    public static AiTokenUsageLogPO fromChatResponse(
            ChatResponse response,
            Usage usage,
            ObjectMapper objectMapper) {
        AiTokenUsageLogPO log = new AiTokenUsageLogPO();
        log.setProvider(DEEPSEEK_PROVIDER);
        log.setResponseId(response.getMetadata().getId());
        log.setObjectType("chat.completion");
        log.setModel(response.getMetadata().getModel());
        log.setFinishReason(finishReason(response));
        log.setPromptTokens(usage.getPromptTokens());
        log.setCompletionTokens(usage.getCompletionTokens());
        log.setTotalTokens(usage.getTotalTokens());
        log.setCachedTokens(cachedTokens(usage, objectMapper));
        log.setReasoningTokens(reasoningTokens(usage, objectMapper));
        log.setRawResponse(objectMapper.valueToTree(response.getMetadata()).toString());
        log.setOccurredAt(LocalDateTime.now());
        return log;
    }

    private static String finishReason(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    private static Integer cachedTokens(Usage usage, ObjectMapper objectMapper) {
        JsonNode node = objectMapper.valueToTree(usage.getNativeUsage());
        return node.path("prompt_tokens_details").path("cached_tokens").asInt(0);
    }

    private static Integer reasoningTokens(Usage usage, ObjectMapper objectMapper) {
        JsonNode node = objectMapper.valueToTree(usage.getNativeUsage());
        return node.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
    }
}
