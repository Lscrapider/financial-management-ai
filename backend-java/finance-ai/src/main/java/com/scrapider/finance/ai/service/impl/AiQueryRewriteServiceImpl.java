package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;
import com.scrapider.finance.ai.service.AiQueryRewriteService;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

@Service
public class AiQueryRewriteServiceImpl implements AiQueryRewriteService {

    private static final String SYSTEM_PROMPT = """
            你负责把用户的理财分析问题改写成后端可执行的数据需求。
            必须返回标准 JSON 对象。只返回 JSON，不要返回 Markdown，不要返回解释文本。
            字段必须包含：
            enabled: boolean，只有理财分析、股票、指数、市场、资产配置、投资研究、财务指标解释类问题才为 true
            disabledReason: string，enabled=false 时说明原因，enabled=true 时为空字符串
            intent: general_chat | stock_analysis | index_analysis | market_overview | concept_explain | knowledge_search
            requiresMarketData: boolean
            targetType: stock | index | market | concept | none
            targetName: string
            stockCode: string
            indexCode: string
            timeRange: intraday | recent_7d | recent_30d | recent_60d | recent_120d | recent_250d | none
            dataScopes: string[]，可选值 quote, intraday_trend, daily_kline, financial_indicator, news, knowledge_base
            dataRequests: object[]，只允许使用以下 queryType：
              stock_quote_by_code: 查询单只股票最新行情，targetCode 填 stockCode
              stock_intraday_by_code: 查询单只股票最新一批盘中分时，targetCode 填 stockCode，limit 不超过 240
              stock_quote_list: 查询股票最新行情列表
              index_quote_by_code: 查询单个指数最新行情，targetCode 填 indexCode
              index_quote_list: 查询指数最新行情列表
              index_daily_kline_by_code: 查询指数日K，targetCode 填 indexCode
            dataRequests 每项字段：
              source: stock_quote_snapshot | index_quote_snapshot | index_daily_kline
              queryType: 上面列出的白名单 queryType
              targetCode: string
              targetName: string
              limit: number
            rewrittenQuestion: string，改写成清晰、完整、适合最终回答模型理解的问题
            如果用户问题不属于上述范围，enabled=false，requiresMarketData=false，dataScopes=[]，dataRequests=[]。
            如果用户问题属于理财分析但不需要查询行情或知识库，enabled=true，requiresMarketData=false，dataScopes=[]，dataRequests=[]。
            如果用户指定股票或指数，优先生成按代码查询的 dataRequests。
            如果用户询问个股趋势、盘中走势、封板、开板、量价变化，应同时请求 stock_quote_by_code 和 stock_intraday_by_code。
            如果用户没有指定具体股票或指数，但询问市场概览，生成 stock_quote_list 和 index_quote_list，limit 不超过 100。
            如果无法确定股票或指数代码，代码字段返回空字符串，不要猜代码；可以用 targetName 保留用户提到的名称。
            """;

    private static final OpenAiChatOptions JSON_OBJECT_OPTIONS = OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder()
                    .type(ResponseFormat.Type.JSON_OBJECT)
                    .build())
            .reasoningEffort("high")
            .build();

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiTokenUsageService aiTokenUsageService;

    public AiQueryRewriteServiceImpl(ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            AiTokenUsageService aiTokenUsageService) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.aiTokenUsageService = aiTokenUsageService;
    }

    @Override
    public AiQueryRewriteVO rewrite(String message) {
        ChatResponse response = this.chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .options(JSON_OBJECT_OPTIONS)
                .call()
                .chatResponse();
        this.aiTokenUsageService.recordChatResponse(response);
        String content = this.content(response);
        return this.parseRewrite(content, message);
    }

    private String content(ChatResponse response) {
        if (response == null) {
            return "";
        } else {
            response.getResult();
        }
        return response.getResult().getOutput().getText();
    }

    private AiQueryRewriteVO parseRewrite(String content, String message) {
        try {
            return this.objectMapper.readValue(this.extractJson(content), AiQueryRewriteVO.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return AiQueryRewriteVO.fallback(message);
        }
    }

    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty rewrite response");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("rewrite response is not json");
        }
        return content.substring(start, end + 1);
    }
}
