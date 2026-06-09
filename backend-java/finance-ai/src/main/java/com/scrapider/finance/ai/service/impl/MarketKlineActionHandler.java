package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.converter.AiMarketDataConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarketKlineActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "market.kline";

    private static final int DAILY_LIMIT = 120;
    private static final int WEEKLY_LIMIT = 80;
    private static final int MONTHLY_LIMIT = 60;

    private final StockKlineManage stockKlineManage;
    private final IndexKlineManage indexKlineManage;
    private final BondKlineManage bondKlineManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;

    public MarketKlineActionHandler(
            StockKlineManage stockKlineManage,
            IndexKlineManage indexKlineManage,
            BondKlineManage bondKlineManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage) {
        this.stockKlineManage = stockKlineManage;
        this.indexKlineManage = indexKlineManage;
        this.bondKlineManage = bondKlineManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        String targetType = this.normalizeTargetType(this.textParam(params, "targetType"));
        String targetCode = this.textParam(params, "targetCode");
        String targetName = this.textParam(params, "targetName");
        ResolvedTarget target = this.resolveTarget(targetType, targetCode, targetName);
        int dailyLimit = this.normalizeLimit(params, "dailyLimit", DAILY_LIMIT);
        int weeklyLimit = this.normalizeLimit(params, "weeklyLimit", WEEKLY_LIMIT);
        int monthlyLimit = this.normalizeLimit(params, "monthlyLimit", MONTHLY_LIMIT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", targetType);
        data.put("targetCode", target.targetCode());
        data.put("targetName", target.targetName());
        data.put("dailyKlines", this.queryKlines(targetType, target.targetCode(), KlinePeriodTypeEnum.DAILY, dailyLimit));
        data.put("weeklyKlines", this.queryKlines(targetType, target.targetCode(), KlinePeriodTypeEnum.WEEKLY, weeklyLimit));
        data.put("monthlyKlines", this.queryKlines(targetType, target.targetCode(), KlinePeriodTypeEnum.MONTHLY, monthlyLimit));

        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                List.of(data),
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "targetType", targetType,
                        "targetCode", StrUtil.nullToEmpty(target.targetCode()),
                        "targetName", StrUtil.nullToEmpty(target.targetName()),
                        "dailyLimit", dailyLimit,
                        "weeklyLimit", weeklyLimit,
                        "monthlyLimit", monthlyLimit),
                null);
    }

    private List<Map<String, Object>> queryKlines(
            String targetType,
            String targetCode,
            KlinePeriodTypeEnum periodType,
            int limit) {
        if (StrUtil.isBlank(targetCode)) {
            return List.of();
        }
        return switch (targetType) {
            case "index" -> this.indexKlineManage
                    .listKlines(targetCode, null, periodType, null, null, limit)
                    .stream()
                    .map(AiMarketDataConverter::indexKlineToMap)
                    .toList();
            case "bond" -> this.bondKlineManage
                    .listKlines(targetCode, null, periodType, null, null, limit)
                    .stream()
                    .map(AiMarketDataConverter::bondKlineToMap)
                    .toList();
            default -> this.stockKlineManage
                    .listKlines(targetCode, null, periodType, KlineAdjustTypeEnum.HFQ, null, null, limit)
                    .stream()
                    .map(AiMarketDataConverter::stockKlineToMap)
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

    private int normalizeLimit(JsonNode params, String fieldName, int maxLimit) {
        if (params == null || params.isNull() || !params.has(fieldName)) {
            return maxLimit;
        }
        int limit = params.get(fieldName).asInt(maxLimit);
        if (limit <= 0) {
            return maxLimit;
        }
        return Math.min(limit, maxLimit);
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
