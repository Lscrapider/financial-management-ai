package com.scrapider.finance.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeepSeekChatRequestConfig {

    @Bean
    public RestClientCustomizer deepSeekThinkingRestClientCustomizer(ObjectMapper objectMapper,
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.chat.completions-path:/chat/completions}") String completionsPath,
            @Value("${deepseek.chat.thinking-enabled:true}") boolean thinkingEnabled,
            @Value("${deepseek.chat.thinking-type:enabled}") String thinkingType) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (!this.shouldEnhance(request.getURI(), baseUrl, completionsPath, thinkingEnabled)) {
                return execution.execute(request, body);
            }
            byte[] enhancedBody = this.addThinkingIfAbsent(objectMapper, body, thinkingType);
            return execution.execute(request, enhancedBody);
        });
    }

    private boolean shouldEnhance(URI uri, String baseUrl, String completionsPath, boolean thinkingEnabled) {
        if (!thinkingEnabled || uri == null || baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String uriText = uri.toString();
        return uriText.startsWith(baseUrl) && uriText.endsWith(completionsPath);
    }

    private byte[] addThinkingIfAbsent(ObjectMapper objectMapper, byte[] body, String thinkingType) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!(root instanceof ObjectNode objectNode) || objectNode.has("thinking")) {
            return body;
        }
        objectNode.putObject("thinking").put("type", thinkingType);
        return objectMapper.writeValueAsString(objectNode).getBytes(StandardCharsets.UTF_8);
    }
}
