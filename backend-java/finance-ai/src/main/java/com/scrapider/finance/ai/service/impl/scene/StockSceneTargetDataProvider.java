package com.scrapider.finance.ai.service.impl.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.ai.service.StockSceneDataEnsureService;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StockSceneTargetDataProvider extends AbstractSceneTargetDataProvider implements SceneTargetDataProvider {

    private static final int DEFAULT_DAILY_KLINE_LIMIT = 90;
    private static final int DEFAULT_WEEKLY_KLINE_LIMIT = 52;
    private static final int DEFAULT_MONTHLY_KLINE_LIMIT = 60;
    private static final int MIN_DAILY_KLINE_LIMIT = 60;
    private static final int MAX_KLINE_LIMIT = 250;
    private static final int INTRADAY_LIMIT = 240;

    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockConfigManage stockConfigManage;
    private final StockKlineManage stockKlineManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final StockSceneDataEnsureService stockSceneDataEnsureService;

    public StockSceneTargetDataProvider(
            ObjectMapper objectMapper,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockConfigManage stockConfigManage,
            StockKlineManage stockKlineManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            StockSceneDataEnsureService stockSceneDataEnsureService) {
        super(objectMapper);
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockConfigManage = stockConfigManage;
        this.stockKlineManage = stockKlineManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.stockSceneDataEnsureService = stockSceneDataEnsureService;
    }

    @Override
    public boolean supports(String targetType) {
        return "STOCK".equals(targetType);
    }

    @Override
    public SceneAnalysisMessageDTO buildMessage(String taskNo, String stockCode, SceneAnalysisSubmitParam param) {
        List<String> missing = new ArrayList<>();
        StockQuoteSnapshotPO quote = this.stockQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .eq(StockQuoteSnapshotPO::getStockCode, stockCode)
                        .last("LIMIT 1"));
        if (quote == null) {
            missing.add("stock_quote_snapshot");
        }
        StockConfigPO config = this.stockConfigManage.getOne(
                new LambdaQueryWrapper<StockConfigPO>()
                        .eq(StockConfigPO::getStockCode, stockCode)
                        .last("LIMIT 1"));
        if (config == null) {
            missing.add("stock_config");
        }
        String targetName = this.firstNotBlank(
                param.targetName(),
                quote == null ? null : quote.getStockName(),
                config == null ? null : config.getStockName());
        SceneAnalysisTargetDTO target = new SceneAnalysisTargetDTO(
                "STOCK",
                stockCode,
                targetName,
                this.firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                this.firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                this.firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
        StockSceneDataDTO sceneData = this.queryStockSceneData(config, missing);
        List<Map<String, Object>> intraday = this.queryStockIntraday(stockCode, missing);
        StockKlineLimits klineLimits = this.stockKlineLimits(param);
        List<Map<String, Object>> dailyKlines = this.fillDailyTurnoverRate(
                this.queryStockKlines(
                        stockCode,
                        KlinePeriodTypeEnum.DAILY,
                        klineLimits.daily(),
                        "stock_daily_kline",
                        missing),
                sceneData.valuationHistory());
        List<Map<String, Object>> weeklyKlines = this.queryStockKlines(
                stockCode,
                KlinePeriodTypeEnum.WEEKLY,
                klineLimits.weekly(),
                "stock_weekly_kline",
                missing);
        List<Map<String, Object>> monthlyKlines = this.queryStockKlines(
                stockCode,
                KlinePeriodTypeEnum.MONTHLY,
                klineLimits.monthly(),
                "stock_monthly_kline",
                missing);
        return this.message(
                taskNo,
                param,
                target,
                this.toMap(quote),
                this.stockValuationData(quote),
                this.toMap(sceneData.industryInfo()),
                this.toMapList(sceneData.valuationHistory()),
                this.toMapList(sceneData.financialIndicators()),
                this.toMapList(sceneData.dividendHistory()),
                dailyKlines,
                weeklyKlines,
                monthlyKlines,
                intraday,
                this.stockAssetSpecificData(sceneData),
                missing);
    }

    private List<Map<String, Object>> queryStockIntraday(String stockCode, List<String> missing) {
        List<Map<String, Object>> rows = this.stockIntradayTrendInfluxManage.listLatestTradingTrends(stockCode).stream()
                .limit(INTRADAY_LIMIT)
                .map(this::toMap)
                .toList();
        if (rows.isEmpty()) {
            missing.add("stock_intraday_trend");
        }
        return rows;
    }

    private StockKlineLimits stockKlineLimits(SceneAnalysisSubmitParam param) {
        return new StockKlineLimits(
                this.klineLimit(param.dailyKlineLimit(), DEFAULT_DAILY_KLINE_LIMIT, MIN_DAILY_KLINE_LIMIT),
                this.klineLimit(param.weeklyKlineLimit(), DEFAULT_WEEKLY_KLINE_LIMIT, 1),
                this.klineLimit(param.monthlyKlineLimit(), DEFAULT_MONTHLY_KLINE_LIMIT, 1));
    }

    private int klineLimit(Integer value, int defaultValue, int minValue) {
        int limit = value == null || value <= 0 ? defaultValue : value;
        return Math.min(Math.max(limit, minValue), MAX_KLINE_LIMIT);
    }

    private List<Map<String, Object>> queryStockKlines(
            String stockCode,
            KlinePeriodTypeEnum periodType,
            Integer limit,
            String missingKey,
            List<String> missing) {
        List<Map<String, Object>> rows = this.stockKlineManage
                .listKlines(stockCode, null, periodType, KlineAdjustTypeEnum.HFQ, null, null, limit)
                .stream()
                .map(this::toMap)
                .toList();
        if (rows.isEmpty()) {
            missing.add(missingKey);
        }
        return rows;
    }

    private List<Map<String, Object>> fillDailyTurnoverRate(
            List<Map<String, Object>> dailyKlines,
            List<StockValuationHistoryPO> valuationHistory) {
        if (dailyKlines.isEmpty() || valuationHistory.isEmpty()) {
            return dailyKlines;
        }
        Map<String, Long> freeSharesByDate = new LinkedHashMap<>();
        valuationHistory.forEach(valuation -> {
            if (valuation.getTradeDate() != null && valuation.getFreeSharesA() != null) {
                freeSharesByDate.put(valuation.getTradeDate().toString(), valuation.getFreeSharesA());
            }
        });
        if (freeSharesByDate.isEmpty()) {
            return dailyKlines;
        }
        return dailyKlines.stream()
                .map(row -> this.fillDailyTurnoverRate(row, freeSharesByDate))
                .toList();
    }

    private Map<String, Object> fillDailyTurnoverRate(Map<String, Object> row, Map<String, Long> freeSharesByDate) {
        if (row.get("turnoverRate") != null) {
            return row;
        }
        Object tradeDate = row.get("tradeDate");
        Object volumeValue = row.get("volume");
        if (tradeDate == null || volumeValue == null) {
            return row;
        }
        Long freeShares = freeSharesByDate.get(tradeDate.toString());
        if (freeShares == null || freeShares <= 0) {
            return row;
        }
        BigDecimal volume = new BigDecimal(volumeValue.toString());
        BigDecimal turnoverRate = volume
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(freeShares), 6, RoundingMode.HALF_UP);
        Map<String, Object> filled = new LinkedHashMap<>(row);
        filled.put("turnoverRate", turnoverRate);
        return filled;
    }

    private StockSceneDataDTO queryStockSceneData(StockConfigPO config, List<String> missing) {
        if (config == null) {
            return new StockSceneDataDTO(null, List.of(), List.of(), List.of());
        }
        StockSceneDataDTO sceneData = this.stockSceneDataEnsureService.ensureStockSceneData(config);
        if (sceneData.industryInfo() == null) {
            missing.add("stock_industry_info");
        }
        if (sceneData.valuationHistory().isEmpty()) {
            missing.add("stock_valuation_history");
        }
        if (sceneData.financialIndicators().isEmpty()) {
            missing.add("stock_financial_indicator");
        }
        if (sceneData.dividendHistory().isEmpty()) {
            missing.add("stock_dividend_history");
        }
        return sceneData;
    }

    private Map<String, Object> stockValuationData(StockQuoteSnapshotPO quote) {
        if (quote == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("peTtm", quote.getPeTtm());
        data.put("peDynamic", quote.getPeDynamic());
        data.put("peStatic", quote.getPeStatic());
        data.put("pbRatio", quote.getPbRatio());
        data.put("totalMarketValue", quote.getTotalMarketValue());
        data.put("floatMarketValue", quote.getFloatMarketValue());
        return this.compactMap(data);
    }

    private Map<String, Object> stockAssetSpecificData(StockSceneDataDTO sceneData) {
        if (sceneData == null) {
            return Map.of("stock", Map.of());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("industryData", this.toMap(sceneData.industryInfo()));
        data.put("valuationHistory", this.toMapList(sceneData.valuationHistory()));
        data.put("financialIndicators", this.toMapList(sceneData.financialIndicators()));
        data.put("dividendHistory", this.toMapList(sceneData.dividendHistory()));
        return Map.of("stock", this.compactMap(data));
    }

    private record StockKlineLimits(int daily, int weekly, int monthly) {
    }
}
