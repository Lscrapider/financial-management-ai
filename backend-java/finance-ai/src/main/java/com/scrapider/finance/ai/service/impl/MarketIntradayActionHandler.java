package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.converter.AiMarketDataConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.BondIntradayTrendInfluxManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexIntradayTrendInfluxManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarketIntradayActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "market.intraday";

    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage;
    private final BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;

    public MarketIntradayActionHandler(
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage,
            BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage) {
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.indexIntradayTrendInfluxManage = indexIntradayTrendInfluxManage;
        this.bondIntradayTrendInfluxManage = bondIntradayTrendInfluxManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在分析分时走势";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        String targetType = this.normalizeTargetType(this.textParam(params, "targetType"));
        String targetCode = this.textParam(params, "targetCode");
        String targetName = this.textParam(params, "targetName");
        ResolvedTarget target = this.resolveTarget(targetType, targetCode, targetName);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", targetType);
        data.put("targetCode", target.targetCode());
        data.put("targetName", target.targetName());
        data.put("intradayData", this.queryIntraday(targetType, target.targetCode()));

        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                List.of(data),
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "targetType", targetType,
                        "targetCode", StrUtil.nullToEmpty(target.targetCode()),
                        "targetName", StrUtil.nullToEmpty(target.targetName())),
                null);
    }

    private List<Map<String, Object>> queryIntraday(String targetType, String targetCode) {
        if (StrUtil.isBlank(targetCode)) {
            return List.of();
        }
        return switch (targetType) {
            case "index" -> this.indexIntradayTrendInfluxManage
                    .listLatestTradingTrends(targetCode)
                    .stream()
                    .map(AiMarketDataConverter::indexIntradayToMap)
                    .toList();
            case "bond" -> this.bondIntradayTrendInfluxManage
                    .listLatestTradingTrends(targetCode)
                    .stream()
                    .map(AiMarketDataConverter::bondIntradayToMap)
                    .toList();
            default -> this.stockIntradayTrendInfluxManage
                    .listLatestTradingTrends(targetCode)
                    .stream()
                    .map(AiMarketDataConverter::stockIntradayToMap)
                    .toList();
        };
    }

    private ResolvedTarget resolveTarget(String targetType, String targetCode, String targetName) {
        if (StrUtil.isNotBlank(targetCode)) {
            return new ResolvedTarget(targetCode, targetName);
        }
        if (StrUtil.isBlank(targetName)) {
            return new ResolvedTarget(targetCode, targetName);
        }
        return switch (targetType) {
            case "index" -> this.resolveIndexTarget(targetName);
            case "bond" -> this.resolveBondTarget(targetName);
            default -> this.resolveStockTarget(targetName);
        };
    }

    private ResolvedTarget resolveStockTarget(String targetName) {
        StockQuoteSnapshotPO quote = this.findStockQuoteByName(targetName);
        if (quote == null) {
            return new ResolvedTarget(null, targetName);
        }
        return new ResolvedTarget(quote.getStockCode(), StrUtil.blankToDefault(quote.getStockName(), targetName));
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

    private ResolvedTarget resolveIndexTarget(String targetName) {
        IndexQuoteSnapshotPO quote = this.findIndexQuoteByName(targetName);
        if (quote == null) {
            return new ResolvedTarget(null, targetName);
        }
        return new ResolvedTarget(quote.getIndexCode(), StrUtil.blankToDefault(quote.getIndexName(), targetName));
    }

    private IndexQuoteSnapshotPO findIndexQuoteByName(String targetName) {
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

    private ResolvedTarget resolveBondTarget(String targetName) {
        BondQuoteSnapshotPO quote = this.findBondQuoteByName(targetName);
        if (quote == null) {
            return new ResolvedTarget(null, targetName);
        }
        return new ResolvedTarget(quote.getBondCode(), StrUtil.blankToDefault(quote.getBondName(), targetName));
    }

    private BondQuoteSnapshotPO findBondQuoteByName(String targetName) {
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

    private String normalizeTargetType(String value) {
        String targetType = StrUtil.blankToDefault(value, "stock").toLowerCase(Locale.ROOT);
        if ("index".equals(targetType) || "bond".equals(targetType)) {
            return targetType;
        }
        return "stock";
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

    private record ResolvedTarget(String targetCode, String targetName) {
    }
}
