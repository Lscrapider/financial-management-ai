package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.converter.MarketQueryConverter;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.BondMarketQueryService;
import com.scrapider.finance.service.IndexMarketQueryService;
import com.scrapider.finance.service.StockMarketQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarketQuoteActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "market.quote";

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StockMarketQueryService stockMarketQueryService;
    private final IndexMarketQueryService indexMarketQueryService;
    private final BondMarketQueryService bondMarketQueryService;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final ObjectMapper objectMapper;

    public MarketQuoteActionHandler(
            StockMarketQueryService stockMarketQueryService,
            IndexMarketQueryService indexMarketQueryService,
            BondMarketQueryService bondMarketQueryService,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            ObjectMapper objectMapper) {
        this.stockMarketQueryService = stockMarketQueryService;
        this.indexMarketQueryService = indexMarketQueryService;
        this.bondMarketQueryService = bondMarketQueryService;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在查询最新行情";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        String targetType = StrUtil.blankToDefault(this.textParam(param.params(), "targetType"), "stock");
        String targetCode = this.textParam(param.params(), "targetCode");
        String targetName = this.textParam(param.params(), "targetName");
        int limit = this.normalizeLimit(param);
        List<Map<String, Object>> rows = switch (targetType) {
            case "index" -> this.queryIndexQuotes(param, targetCode, targetName, limit);
            case "bond" -> this.queryBondQuotes(param, targetCode, targetName, limit);
            default -> this.queryStockQuotes(param, targetCode, targetName, limit);
        };
        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                rows,
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "targetType", targetType,
                        "targetCode", StrUtil.nullToEmpty(targetCode),
                        "targetName", StrUtil.nullToEmpty(targetName),
                        "limit", limit),
                null);
    }

    private List<Map<String, Object>> queryStockQuotes(
            AgentDataQueryParam param,
            String targetCode,
            String targetName,
            int limit) {
        if (StrUtil.isNotBlank(targetCode)) {
            return this.toRows(MarketQueryConverter.toStockQuoteVOList(
                    this.stockQuoteSnapshotManage.listByStockCodes(List.of(targetCode))));
        }
        StockQuoteSnapshotPO stockQuote = this.findStockQuoteByName(targetName);
        if (stockQuote != null) {
            return this.toRows(MarketQueryConverter.toStockQuoteVOList(List.of(stockQuote)));
        }
        StockQuoteListParam queryParam = new StockQuoteListParam();
        queryParam.setMarketCode(this.textParam(param.params(), "marketCode"));
        queryParam.setLimit(limit);
        queryParam.setSortField(StrUtil.blankToDefault(this.textParam(param.params(), "sortField"), "changePercent"));
        queryParam.setSortOrder(StrUtil.blankToDefault(this.textParam(param.params(), "sortOrder"), "desc"));
        return this.toRows(this.stockMarketQueryService.listQuotes(queryParam));
    }

    private List<Map<String, Object>> queryIndexQuotes(
            AgentDataQueryParam param,
            String targetCode,
            String targetName,
            int limit) {
        if (StrUtil.isNotBlank(targetCode)) {
            return this.toRows(MarketQueryConverter.toIndexQuoteVOList(
                    this.indexQuoteSnapshotManage.listByIndexCodes(List.of(targetCode))));
        }
        IndexQuoteSnapshotPO indexQuote = this.findIndexQuoteByName(targetName);
        if (indexQuote != null) {
            return this.toRows(MarketQueryConverter.toIndexQuoteVOList(List.of(indexQuote)));
        }
        IndexQuoteListParam queryParam = new IndexQuoteListParam();
        queryParam.setMarketCode(this.textParam(param.params(), "marketCode"));
        queryParam.setLimit(limit);
        queryParam.setSortField(StrUtil.blankToDefault(this.textParam(param.params(), "sortField"), "indexCode"));
        queryParam.setSortOrder(StrUtil.blankToDefault(this.textParam(param.params(), "sortOrder"), "asc"));
        return this.toRows(this.indexMarketQueryService.listQuotes(queryParam));
    }

    private List<Map<String, Object>> queryBondQuotes(
            AgentDataQueryParam param,
            String targetCode,
            String targetName,
            int limit) {
        if (StrUtil.isNotBlank(targetCode)) {
            return this.toRows(MarketQueryConverter.toBondQuoteVOList(
                    this.bondQuoteSnapshotManage.listByBondCodes(List.of(targetCode))));
        }
        BondQuoteSnapshotPO bondQuote = this.findBondQuoteByName(targetName);
        if (bondQuote != null) {
            return this.toRows(MarketQueryConverter.toBondQuoteVOList(List.of(bondQuote)));
        }
        BondQuoteListParam queryParam = new BondQuoteListParam();
        queryParam.setMarketCode(this.textParam(param.params(), "marketCode"));
        queryParam.setLimit(limit);
        queryParam.setSortField(StrUtil.blankToDefault(this.textParam(param.params(), "sortField"), "changePercent"));
        queryParam.setSortOrder(StrUtil.blankToDefault(this.textParam(param.params(), "sortOrder"), "desc"));
        return this.toRows(this.bondMarketQueryService.listQuotes(queryParam));
    }

    private StockQuoteSnapshotPO findStockQuoteByName(String targetName) {
        if (StrUtil.isBlank(targetName)) {
            return null;
        }
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

    private IndexQuoteSnapshotPO findIndexQuoteByName(String targetName) {
        if (StrUtil.isBlank(targetName)) {
            return null;
        }
        IndexQuoteSnapshotPO quote = this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .eq(IndexQuoteSnapshotPO::getIndexName, targetName)
                        .last("LIMIT 1"));
        if (quote != null) {
            return quote;
        }
        return this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .like(IndexQuoteSnapshotPO::getIndexName, targetName)
                        .last("LIMIT 1"));
    }

    private BondQuoteSnapshotPO findBondQuoteByName(String targetName) {
        if (StrUtil.isBlank(targetName)) {
            return null;
        }
        BondQuoteSnapshotPO quote = this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .eq(BondQuoteSnapshotPO::getBondName, targetName)
                        .last("LIMIT 1"));
        if (quote != null) {
            return quote;
        }
        return this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .like(BondQuoteSnapshotPO::getBondName, targetName)
                        .last("LIMIT 1"));
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

    private int normalizeLimit(AgentDataQueryParam param) {
        Integer limit = param.limit();
        JsonNode params = param.params();
        if ((limit == null || limit <= 0) && params != null && params.has("limit")) {
            limit = params.get("limit").asInt(DEFAULT_LIMIT);
        }
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<Map<String, Object>> toRows(List<?> rows) {
        return rows.stream()
                .map(item -> this.objectMapper.convertValue(item, MAP_TYPE))
                .toList();
    }
}
