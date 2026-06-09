package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.converter.AiAgentDomainToolDataConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockDividendHistoryManage;
import com.scrapider.finance.manage.StockFinancialIndicatorManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.manage.StockValuationHistoryManage;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StockFundamentalContextActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "stock.fundamental_context";

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 30;
    private static final int VALUATION_HISTORY_LIMIT = 120;
    private static final int DIVIDEND_HISTORY_LIMIT = 10;
    private static final String SECTION_VALUATION = "valuation";
    private static final String SECTION_FINANCIAL_INDICATORS = "financialIndicators";

    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;

    public StockFundamentalContextActionHandler(
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage) {
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        String targetCode = this.textParam(params, "targetCode");
        String targetName = this.textParam(params, "targetName");
        Set<String> sections = this.normalizeSections(params);
        int limit = this.normalizeLimit(params);
        ResolvedStockTarget target = this.resolveTarget(targetCode, targetName);

        List<StockValuationHistoryPO> valuations = List.of();
        List<StockDividendHistoryPO> dividends = List.of();
        if (sections.contains(SECTION_VALUATION) && StrUtil.isNotBlank(target.targetCode())) {
            valuations = this.stockValuationHistoryManage.listByStockCode(target.targetCode(), VALUATION_HISTORY_LIMIT);
            dividends = this.stockDividendHistoryManage.listByStockCode(target.targetCode(), DIVIDEND_HISTORY_LIMIT);
        }

        List<StockFinancialIndicatorPO> indicators = List.of();
        if (sections.contains(SECTION_FINANCIAL_INDICATORS) && StrUtil.isNotBlank(target.targetCode())) {
            indicators = this.stockFinancialIndicatorManage.listByStockCode(target.targetCode(), limit);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("target", AiAgentDomainToolDataConverter.stockTarget(target.targetCode(), target.targetName()));
        row.put("valuation", sections.contains(SECTION_VALUATION)
                ? AiAgentDomainToolDataConverter.stockValuation(valuations, dividends)
                : Map.of());
        row.put("financialIndicators", sections.contains(SECTION_FINANCIAL_INDICATORS)
                ? AiAgentDomainToolDataConverter.stockFinancialIndicators(indicators)
                : List.of());
        row.put("dataCompleteness", this.dataCompleteness(target, sections, valuations, dividends, indicators));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queriedAt", OffsetDateTime.now().toString());
        metadata.put("targetType", "stock");
        metadata.put("targetCode", StrUtil.nullToEmpty(target.targetCode()));
        metadata.put("targetName", StrUtil.nullToEmpty(target.targetName()));
        metadata.put("sections", List.copyOf(sections));
        metadata.put("limit", limit);
        metadata.put("valuationHistoryLimit", VALUATION_HISTORY_LIMIT);
        metadata.put("dividendHistoryLimit", DIVIDEND_HISTORY_LIMIT);
        return new AgentDataGatewayResponseVO(param.action(), true, List.of(row), metadata, null);
    }

    private ResolvedStockTarget resolveTarget(String targetCode, String targetName) {
        if (StrUtil.isNotBlank(targetCode)) {
            StockConfigPO config = this.stockConfigManage.getByStockCode(targetCode);
            if (config != null) {
                return new ResolvedStockTarget(
                        config.getStockCode(),
                        StrUtil.blankToDefault(config.getStockName(), targetName),
                        true);
            }
            List<StockQuoteSnapshotPO> quotes = this.stockQuoteSnapshotManage.listByStockCodes(List.of(targetCode));
            if (!quotes.isEmpty()) {
                StockQuoteSnapshotPO quote = quotes.get(0);
                return new ResolvedStockTarget(
                        quote.getStockCode(),
                        StrUtil.blankToDefault(quote.getStockName(), targetName),
                        true);
            }
            return new ResolvedStockTarget(targetCode, targetName, true);
        }
        if (StrUtil.isBlank(targetName)) {
            return new ResolvedStockTarget(null, null, false);
        }
        StockConfigPO config = this.findStockConfigByName(targetName);
        if (config != null) {
            return new ResolvedStockTarget(
                    config.getStockCode(),
                    StrUtil.blankToDefault(config.getStockName(), targetName),
                    true);
        }
        StockQuoteSnapshotPO quote = this.findStockQuoteByName(targetName);
        if (quote != null) {
            return new ResolvedStockTarget(
                    quote.getStockCode(),
                    StrUtil.blankToDefault(quote.getStockName(), targetName),
                    true);
        }
        return new ResolvedStockTarget(null, targetName, false);
    }

    private StockConfigPO findStockConfigByName(String targetName) {
        StockConfigPO config = this.stockConfigManage.getOne(
                new LambdaQueryWrapper<StockConfigPO>()
                        .eq(StockConfigPO::getStockName, targetName)
                        .last("LIMIT 1"));
        if (config != null) {
            return config;
        }
        return this.stockConfigManage.getOne(
                new LambdaQueryWrapper<StockConfigPO>()
                        .like(StockConfigPO::getStockName, targetName)
                        .last("LIMIT 1"));
    }

    private StockQuoteSnapshotPO findStockQuoteByName(String targetName) {
        StockQuoteSnapshotPO quote = this.stockQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .eq(StockQuoteSnapshotPO::getStockName, targetName)
                        .last("LIMIT 1"));
        if (quote != null) {
            return quote;
        }
        return this.stockQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .like(StockQuoteSnapshotPO::getStockName, targetName)
                        .last("LIMIT 1"));
    }

    private Map<String, Object> dataCompleteness(
            ResolvedStockTarget target,
            Set<String> sections,
            List<StockValuationHistoryPO> valuations,
            List<StockDividendHistoryPO> dividends,
            List<StockFinancialIndicatorPO> indicators) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("targetResolved", target.resolved());
        row.put("requestedSections", List.copyOf(sections));
        row.put("valuationRequested", sections.contains(SECTION_VALUATION));
        row.put("valuationHistoryCount", valuations.size());
        row.put("dividendHistoryCount", dividends.size());
        row.put("financialIndicatorsRequested", sections.contains(SECTION_FINANCIAL_INDICATORS));
        row.put("financialIndicatorCount", indicators.size());
        return row;
    }

    private Set<String> normalizeSections(JsonNode params) {
        Set<String> sections = new LinkedHashSet<>();
        if (params == null || params.isNull() || !params.has("sections") || params.get("sections").isNull()) {
            sections.add(SECTION_VALUATION);
            sections.add(SECTION_FINANCIAL_INDICATORS);
            return sections;
        }
        JsonNode sectionNode = params.get("sections");
        if (sectionNode.isArray()) {
            sectionNode.forEach(item -> this.addSection(sections, item.asText()));
        } else {
            for (String item : sectionNode.asText("").split(",")) {
                this.addSection(sections, item);
            }
        }
        if (sections.isEmpty()) {
            sections.add(SECTION_VALUATION);
            sections.add(SECTION_FINANCIAL_INDICATORS);
        }
        return sections;
    }

    private void addSection(Set<String> sections, String value) {
        String section = StrUtil.trimToNull(value);
        if (section == null) {
            return;
        }
        if (SECTION_VALUATION.equals(section)) {
            sections.add(SECTION_VALUATION);
        }
        if (SECTION_FINANCIAL_INDICATORS.equals(section) || "financial_indicators".equals(section)) {
            sections.add(SECTION_FINANCIAL_INDICATORS);
        }
    }

    private int normalizeLimit(JsonNode params) {
        if (params == null || params.isNull() || !params.has("limit")) {
            return DEFAULT_LIMIT;
        }
        int limit = params.get("limit").asInt(DEFAULT_LIMIT);
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String textParam(JsonNode params, String fieldName) {
        if (params == null || params.isNull()) {
            return null;
        }
        JsonNode value = params.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return StrUtil.trimToNull(value.asText());
    }

    private record ResolvedStockTarget(String targetCode, String targetName, boolean resolved) {
    }
}
