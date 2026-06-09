package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.enums.AiTokenUsagePhaseEnum;
import com.scrapider.finance.domain.enums.AiTokenUsageSourceEnum;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.Data;

@Data
@TableName("ai_token_usage_log")
public class AiTokenUsageLogPO {

    private static final String DEEPSEEK_PROVIDER = "deepseek";
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private Long id;
    private String provider;
    private String responseId;
    private String objectType;
    private String model;
    private String finishReason;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer cachedTokens;
    private Integer reasoningTokens;
    private Integer promptCacheHitTokens;
    private Integer promptCacheMissTokens;
    private Long userId;
    private String source;
    private String sourceRefId;
    private String phase;
    private String rawResponse;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;

    public static AiTokenUsageLogPO fromDeepSeekResponse(JsonNode response) {
        JsonNode usage = response.path("usage");
        JsonNode choice = response.path("choices").path(0);
        JsonNode promptTokenDetails = usage.path("prompt_tokens_details");
        JsonNode completionTokenDetails = usage.path("completion_tokens_details");

        AiTokenUsageLogPO log = new AiTokenUsageLogPO();
        log.setProvider(DEEPSEEK_PROVIDER);
        log.setResponseId(text(response, "id"));
        log.setObjectType(text(response, "object"));
        log.setModel(text(response, "model"));
        log.setFinishReason(text(choice, "finish_reason"));
        log.setPromptTokens(intValue(usage, "prompt_tokens"));
        log.setCompletionTokens(intValue(usage, "completion_tokens"));
        log.setTotalTokens(intValue(usage, "total_tokens"));
        log.setCachedTokens(intValue(promptTokenDetails, "cached_tokens"));
        log.setReasoningTokens(intValue(completionTokenDetails, "reasoning_tokens"));
        log.setPromptCacheHitTokens(intValue(usage, "prompt_cache_hit_tokens"));
        log.setPromptCacheMissTokens(intValue(usage, "prompt_cache_miss_tokens"));
        log.setRawResponse(response.toString());
        log.setOccurredAt(createdAt(response));
        return log;
    }

    public static AiTokenUsageLogPO fromDeepSeekResponse(
            JsonNode response,
            Long userId,
            AiTokenUsageSourceEnum source,
            String sourceRefId,
            AiTokenUsagePhaseEnum phase) {
        AiTokenUsageLogPO log = fromDeepSeekResponse(response);
        log.applyAttribution(userId, source, sourceRefId, phase);
        return log;
    }

    public static AiTokenUsageLogPO fromAgentUsageEvent(
            JsonNode event,
            Long userId,
            String conversationId,
            AiTokenUsagePhaseEnum phase) {
        AiTokenUsageLogPO log = new AiTokenUsageLogPO();
        log.setProvider(DEEPSEEK_PROVIDER);
        log.setResponseId(text(event, "responseId"));
        log.setObjectType(text(event, "objectType"));
        log.setModel(text(event, "model"));
        log.setFinishReason(text(event, "finishReason"));
        log.setPromptTokens(intValue(event, "promptTokens"));
        log.setCompletionTokens(intValue(event, "completionTokens"));
        log.setTotalTokens(intValue(event, "totalTokens"));
        log.setCachedTokens(intValue(event, "cachedTokens"));
        log.setReasoningTokens(intValue(event, "reasoningTokens"));
        log.setRawResponse(rawResponse(event));
        log.setOccurredAt(LocalDateTime.now());
        log.applyAttribution(userId, AiTokenUsageSourceEnum.AGENT, conversationId, phase);
        return log;
    }

    public void applyAttribution(
            Long userId,
            AiTokenUsageSourceEnum source,
            String sourceRefId,
            AiTokenUsagePhaseEnum phase) {
        this.setUserId(userId);
        this.setSource(source == null ? null : source.getCode());
        this.setSourceRefId(sourceRefId);
        this.setPhase(phase == null ? null : phase.getCode());
    }

    private static LocalDateTime createdAt(JsonNode response) {
        long created = response.path("created").asLong(0L);
        if (created <= 0L) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(created), SYSTEM_ZONE);
    }

    private static Integer intValue(JsonNode node, String fieldName) {
        return node.path(fieldName).asInt(0);
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String rawResponse(JsonNode event) {
        JsonNode rawResponse = event.path("rawResponse");
        if (rawResponse.isMissingNode() || rawResponse.isNull()) {
            return event.toString();
        }
        if (rawResponse.isTextual()) {
            return rawResponse.asText();
        }
        return rawResponse.toString();
    }
}
