package com.scrapider.finance.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DeepSeekChatCompletionApi {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String completionsPath;
    private final String model;
    private final double temperature;
    private final String reasoningEffort;
    private final boolean thinkingEnabled;
    private final String thinkingType;

    public DeepSeekChatCompletionApi(
            RestTemplate restTemplate,
            @Value("${spring.ai.openai.api-key:${DEEPSEEK_API_KEY:}}") String apiKey,
            @Value("${spring.ai.openai.base-url:${DEEPSEEK_BASE_URL:https://api.deepseek.com}}") String baseUrl,
            @Value("${spring.ai.openai.chat.completions-path:${DEEPSEEK_COMPLETIONS_PATH:/chat/completions}}")
                    String completionsPath,
            @Value("${spring.ai.openai.chat.options.model:${DEEPSEEK_MODEL:deepseek-v4-pro}}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:${DEEPSEEK_TEMPERATURE:0.3}}") double temperature,
            @Value("${spring.ai.openai.chat.options.reasoning-effort:${DEEPSEEK_REASONING_EFFORT:max}}")
                    String reasoningEffort,
            @Value("${deepseek.chat.thinking-enabled:${DEEPSEEK_THINKING_ENABLED:true}}") boolean thinkingEnabled,
            @Value("${deepseek.chat.thinking-type:${DEEPSEEK_THINKING_TYPE:enabled}}") String thinkingType) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.completionsPath = completionsPath;
        this.model = model;
        this.temperature = temperature;
        this.reasoningEffort = reasoningEffort;
        this.thinkingEnabled = thinkingEnabled;
        this.thinkingType = thinkingType;
    }

    public JsonNode generateJsonReport(String systemPrompt, String userPrompt) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key 未配置。");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", this.model);
        body.put("reasoning_effort", this.reasoningEffort);
        if (this.thinkingEnabled) {
            body.put("thinking", Map.of("type", this.thinkingType));
        }
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.apiKey);
        return this.restTemplate.postForObject(this.url(), new HttpEntity<>(body, headers), JsonNode.class);
    }

    public String model() {
        return this.model;
    }

    private String url() {
        return this.baseUrl.replaceAll("/+$", "") + "/" + this.completionsPath.replaceAll("^/+", "");
    }
}
