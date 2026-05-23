package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.param.AiChatParam;
import com.scrapider.finance.ai.domain.vo.AiChatVO;
import com.scrapider.finance.ai.service.AiChatService;
import java.time.LocalDateTime;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final String SYSTEM_PROMPT = """
            你是一个面向个人投资研究的理财分析助手。
            回答要清晰、克制，并明确区分事实、推断和风险提示。
            在没有实时行情、财务数据或知识库依据时，不要编造具体数据。
            不提供确定性买卖建议，不承诺收益。
            """;

    private final ChatClient chatClient;
    private final String model;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.model = model;
    }

    @Override
    public AiChatVO chat(AiChatParam param) {
        String message = this.normalizeMessage(param);
        String answer = this.chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .call()
                .content();
        return new AiChatVO(message, answer, this.model, LocalDateTime.now());
    }

    private String normalizeMessage(AiChatParam param) {
        if (param == null || param.message() == null || param.message().isBlank()) {
            throw new IllegalArgumentException("message不能为空");
        }
        return param.message().trim();
    }
}
