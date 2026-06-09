package com.scrapider.finance.ai.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.SceneTargetDataConverter;
import com.scrapider.finance.ai.converter.StockSceneDataConverter;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.service.SceneAssetDataEnsureService;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
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
    private final SceneAssetDataEnsureService sceneAssetDataEnsureService;

    public StockSceneTargetDataProvider(
            ObjectMapper objectMapper,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockConfigManage stockConfigManage,
            StockKlineManage stockKlineManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            SceneAssetDataEnsureService sceneAssetDataEnsureService) {
        super(objectMapper);
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockConfigManage = stockConfigManage;
        this.stockKlineManage = stockKlineManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.sceneAssetDataEnsureService = sceneAssetDataEnsureService;
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
        SceneAnalysisTargetDTO target = SceneTargetDataConverter.stockTarget(stockCode, targetName, quote, config);
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
                SceneTargetDataConverter.stockValuationData(quote),
                this.toMap(sceneData.industryInfo()),
                this.toMapList(sceneData.valuationHistory()),
                this.toMapList(sceneData.financialIndicators()),
                this.toMapList(sceneData.dividendHistory()),
                dailyKlines,
                weeklyKlines,
                monthlyKlines,
                intraday,
                SceneTargetDataConverter.stockAssetSpecificData(this.objectMapper(), sceneData),
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
        Map<String, Long> freeSharesByDate = SceneTargetDataConverter.freeSharesByDate(valuationHistory);
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
        return SceneTargetDataConverter.dailyKlineWithTurnoverRate(row, turnoverRate);
    }

    private StockSceneDataDTO queryStockSceneData(StockConfigPO config, List<String> missing) {
        if (config == null) {
            return StockSceneDataConverter.empty();
        }
        StockSceneDataDTO sceneData = this.sceneAssetDataEnsureService.ensureStockSceneData(config);
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

    private record StockKlineLimits(int daily, int weekly, int monthly) {
    }
}
