package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.converter.AiChatConverter;
import com.scrapider.finance.ai.domain.param.AiChatParam;
import com.scrapider.finance.ai.domain.vo.AiChatVO;
import com.scrapider.finance.ai.domain.vo.AiDatabaseContextVO;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;
import com.scrapider.finance.ai.service.AiChatService;
import com.scrapider.finance.ai.chat.AiMarketDataQueryService;
import com.scrapider.finance.ai.chat.AiQueryRewriteService;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
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
    private final AiQueryRewriteService aiQueryRewriteService;
    private final AiMarketDataQueryService aiMarketDataQueryService;
    private final AiTokenUsageService aiTokenUsageService;
    private final String model;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder,
            AiQueryRewriteService aiQueryRewriteService,
            AiMarketDataQueryService aiMarketDataQueryService,
            AiTokenUsageService aiTokenUsageService,
            @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.aiQueryRewriteService = aiQueryRewriteService;
        this.aiMarketDataQueryService = aiMarketDataQueryService;
        this.aiTokenUsageService = aiTokenUsageService;
        this.model = model;
    }

    @Override
    public AiChatVO chat(AiChatParam param) {
        String message = this.normalizeMessage(param);
        AiQueryRewriteVO queryRewrite = this.aiQueryRewriteService.rewrite(message);
        if (!Boolean.TRUE.equals(queryRewrite.enabled())) {
            String answer = queryRewrite.disabledReason() == null || queryRewrite.disabledReason().isBlank()
                    ? "当前助手只处理理财分析、行情、指数、资产配置和投资研究相关问题。"
                    : queryRewrite.disabledReason();
            return AiChatConverter.toVO(message, answer, this.model, queryRewrite, AiDatabaseContextVO.empty());
        }
        AiDatabaseContextVO databaseContext = this.aiMarketDataQueryService.query(queryRewrite);
        ChatResponse response = this.chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(this.buildUserPrompt(message, queryRewrite, databaseContext))
                .call()
                .chatResponse();
        this.aiTokenUsageService.recordChatResponse(response);
        String answer = this.content(response);
        return AiChatConverter.toVO(message, answer, this.model, queryRewrite, databaseContext);
    }

    private String content(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String normalizeMessage(AiChatParam param) {
        if (param == null || param.message() == null || param.message().isBlank()) {
            throw new IllegalArgumentException("message不能为空");
        }
        return param.message().trim();
    }

    private String buildUserPrompt(String message, AiQueryRewriteVO queryRewrite, AiDatabaseContextVO databaseContext) {
        return """
                原始问题：
                %s

                Query Rewrite 结果：
                intent=%s
                requiresMarketData=%s
                targetType=%s
                targetName=%s
                stockCode=%s
                indexCode=%s
                timeRange=%s
                dataScopes=%s
                dataRequests=%s
                rewrittenQuestion=%s

                数据库查询结果：
                %s

                请基于以上信息回答。若数据库查询结果为空或缺少用户需要的数据，请明确说明缺少哪些数据，不要编造具体数值。
                """.formatted(
                message,
                queryRewrite.intent(),
                queryRewrite.requiresMarketData(),
                queryRewrite.targetType(),
                queryRewrite.targetName(),
                queryRewrite.stockCode(),
                queryRewrite.indexCode(),
                queryRewrite.timeRange(),
                queryRewrite.dataScopes(),
                queryRewrite.dataRequests(),
                queryRewrite.rewrittenQuestion(),
                databaseContext.results());
    }
}
