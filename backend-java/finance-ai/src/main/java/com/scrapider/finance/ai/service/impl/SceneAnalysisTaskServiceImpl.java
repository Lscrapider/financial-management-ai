package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisConfigDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.ai.service.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.service.SceneReportPipelineService;
import com.scrapider.finance.ai.service.StockSceneDataEnsureService;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondDailyKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexDailyKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisTaskServiceImpl implements SceneAnalysisTaskService {

    private static final int MARKET_DAILY_KLINE_LIMIT = 250;
    private static final int DEFAULT_STOCK_DAILY_KLINE_LIMIT = 90;
    private static final int DEFAULT_STOCK_WEEKLY_KLINE_LIMIT = 52;
    private static final int DEFAULT_STOCK_MONTHLY_KLINE_LIMIT = 60;
    private static final int MIN_STOCK_DAILY_KLINE_LIMIT = 60;
    private static final int MAX_STOCK_KLINE_LIMIT = 250;
    private static final int INTRADAY_LIMIT = 240;
    private static final List<String> MESSAGE_OMITTED_FIELDS =
            List.of("id", "rawResponse", "createdAt", "updatedAt");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockConfigManage stockConfigManage;
    private final StockKlineManage stockKlineManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexConfigManage indexConfigManage;
    private final IndexDailyKlineManage indexDailyKlineManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondConfigManage bondConfigManage;
    private final BondDailyKlineManage bondDailyKlineManage;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final StockSceneDataEnsureService stockSceneDataEnsureService;
    private final SceneReportPipelineService sceneReportPipelineService;

    public SceneAnalysisTaskServiceImpl(
            ObjectMapper objectMapper,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockConfigManage stockConfigManage,
            StockKlineManage stockKlineManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexConfigManage indexConfigManage,
            IndexDailyKlineManage indexDailyKlineManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondConfigManage bondConfigManage,
            BondDailyKlineManage bondDailyKlineManage,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            StockSceneDataEnsureService stockSceneDataEnsureService,
            SceneReportPipelineService sceneReportPipelineService) {
        this.objectMapper = objectMapper;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockConfigManage = stockConfigManage;
        this.stockKlineManage = stockKlineManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexConfigManage = indexConfigManage;
        this.indexDailyKlineManage = indexDailyKlineManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondConfigManage = bondConfigManage;
        this.bondDailyKlineManage = bondDailyKlineManage;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.stockSceneDataEnsureService = stockSceneDataEnsureService;
        this.sceneReportPipelineService = sceneReportPipelineService;
    }

    @Override
    public SceneAnalysisSubmitVO submit(SceneAnalysisSubmitParam param) {
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String targetType = this.normalizeTargetType(param.targetType());
        String targetCode = StrUtil.trim(param.targetCode());
        if (StrUtil.isBlank(targetType) || StrUtil.isBlank(targetCode)) {
            throw new IllegalArgumentException("targetType and targetCode are required");
        }
        if (param.totalChunks() == null || param.totalChunks() <= 0) {
            throw new IllegalArgumentException("totalChunks is required and must be greater than 0");
        }
        Long userId = this.currentUserId();
        String taskNo = this.newTaskNo();
        SceneAnalysisMessageDTO message = switch (targetType) {
            case "STOCK" -> this.buildStockMessage(taskNo, targetCode, param);
            case "INDEX" -> this.buildIndexMessage(taskNo, targetCode, param);
            case "CONVERTIBLE_BOND" -> this.buildBondMessage(taskNo, targetCode, param);
            default -> throw new IllegalArgumentException("unsupported targetType: " + param.targetType());
        };
        this.sceneAnalysisTaskManage.saveTask(this.pendingTask(userId, message));
        try {
            this.sceneAnalysisMessagePublisher.publishCurrentSceneAnalysisMessage(message);
            this.sceneAnalysisTaskManage.markProcessing(taskNo);
        } catch (Exception ex) {
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
        return new SceneAnalysisSubmitVO(
                taskNo,
                message.target().type(),
                message.target().code(),
                message.config().profile(),
                SceneAnalysisTaskStatusEnum.PROCESSING_CURRENT_SCENES.getCode());
    }

    @Override
    public void callback(String taskNo, SceneAnalysisCallbackParam param) {
        if (StrUtil.isBlank(taskNo)) {
            throw new IllegalArgumentException("taskNo is required");
        }
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        SceneAnalysisCurrentScenesPayloadParam currentScenesPayload = param.currentScenesPayload();
        List<SceneRetrievalEmbeddingParam> retrievalEmbeddings = param.retrievalEmbeddings();
        if (currentScenesPayload == null && (retrievalEmbeddings == null || retrievalEmbeddings.isEmpty())) {
            throw new IllegalArgumentException("currentScenesPayload or retrievalEmbeddings is required");
        }
        try {
            if (currentScenesPayload != null) {
                this.sceneAnalysisTaskManage.markCurrentScenesReady(
                        taskNo,
                        this.objectMapper.valueToTree(currentScenesPayload));
                this.sceneReportPipelineService.start(taskNo, currentScenesPayload);
                return;
            }
            this.sceneReportPipelineService.continueWithRetrievalEmbeddings(taskNo, retrievalEmbeddings);
        } catch (Exception ex) {
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
    }

    private SceneAnalysisMessageDTO buildStockMessage(String taskNo, String stockCode, SceneAnalysisSubmitParam param) {
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
        List<Map<String, Object>> dailyKlines =
                this.fillDailyTurnoverRate(
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
                missing);
    }

    private SceneAnalysisMessageDTO buildIndexMessage(String taskNo, String indexCode, SceneAnalysisSubmitParam param) {
        List<String> missing = new ArrayList<>();
        IndexQuoteSnapshotPO quote = this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .eq(IndexQuoteSnapshotPO::getIndexCode, indexCode)
                        .last("LIMIT 1"));
        if (quote == null) {
            missing.add("index_quote_snapshot");
        }
        IndexConfigPO config = this.indexConfigManage.getOne(
                new LambdaQueryWrapper<IndexConfigPO>()
                        .eq(IndexConfigPO::getIndexCode, indexCode)
                        .last("LIMIT 1"));
        String targetName = this.firstNotBlank(
                param.targetName(),
                quote == null ? null : quote.getIndexName(),
                config == null ? null : config.getIndexName());
        SceneAnalysisTargetDTO target = new SceneAnalysisTargetDTO(
                "INDEX",
                indexCode,
                targetName,
                this.firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                this.firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                this.firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
        List<Map<String, Object>> dailyKlines = this.indexDailyKlineManage
                .listDailyKlines(indexCode, null, null, null, MARKET_DAILY_KLINE_LIMIT)
                .stream()
                .map(this::toMap)
                .toList();
        if (dailyKlines.isEmpty()) {
            missing.add("index_daily_kline");
        }
        return this.message(
                taskNo,
                param,
                target,
                this.toMap(quote),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                dailyKlines,
                List.of(),
                List.of(),
                List.of(),
                missing);
    }

    private SceneAnalysisMessageDTO buildBondMessage(String taskNo, String bondCode, SceneAnalysisSubmitParam param) {
        List<String> missing = new ArrayList<>();
        BondQuoteSnapshotPO quote = this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .eq(BondQuoteSnapshotPO::getBondCode, bondCode)
                        .last("LIMIT 1"));
        if (quote == null) {
            missing.add("bond_quote_snapshot");
        }
        BondConfigPO config = this.bondConfigManage.getOne(
                new LambdaQueryWrapper<BondConfigPO>()
                        .eq(BondConfigPO::getBondCode, bondCode)
                        .last("LIMIT 1"));
        String targetName = this.firstNotBlank(
                param.targetName(),
                quote == null ? null : quote.getBondName(),
                config == null ? null : config.getBondName());
        SceneAnalysisTargetDTO target = new SceneAnalysisTargetDTO(
                "CONVERTIBLE_BOND",
                bondCode,
                targetName,
                this.firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                this.firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                this.firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
        List<Map<String, Object>> dailyKlines = this.bondDailyKlineManage
                .listDailyKlines(bondCode, null, null, null, MARKET_DAILY_KLINE_LIMIT)
                .stream()
                .map(this::toMap)
                .toList();
        if (dailyKlines.isEmpty()) {
            missing.add("bond_daily_kline");
        }
        return this.message(
                taskNo,
                param,
                target,
                this.toMap(quote),
                this.bondValuationData(quote),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                dailyKlines,
                List.of(),
                List.of(),
                List.of(),
                missing);
    }

    private SceneAnalysisMessageDTO message(
            String taskNo,
            SceneAnalysisSubmitParam param,
            SceneAnalysisTargetDTO target,
            Map<String, Object> marketData,
            Map<String, Object> valuationData,
            Map<String, Object> industryData,
            List<Map<String, Object>> valuationHistory,
            List<Map<String, Object>> financialIndicators,
            List<Map<String, Object>> dividendHistory,
            List<Map<String, Object>> dailyKlines,
            List<Map<String, Object>> weeklyKlines,
            List<Map<String, Object>> monthlyKlines,
            List<Map<String, Object>> intradayData,
            List<String> missing) {
        return new SceneAnalysisMessageDTO(
                taskNo,
                LocalDateTime.now(),
                this.normalizeReportType(param.reportType()),
                param.totalChunks(),
                target,
                new SceneAnalysisConfigDTO(
                        StrUtil.blankToDefault(param.configProfile(), "system_recommended"),
                        SceneAnalysisUserConfigParam.effective(param.userOverrides(), target.type())),
                marketData,
                valuationData,
                industryData,
                valuationHistory,
                financialIndicators,
                dividendHistory,
                dailyKlines,
                weeklyKlines,
                monthlyKlines,
                intradayData,
                this.dataCompleteness(missing));
    }

    private String normalizeReportType(String reportType) {
        if (StrUtil.isBlank(reportType)) {
            return SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode();
        }
        return SceneAnalysisReportTypeEnum.of(reportType.trim()).getCode();
    }

    private SceneAnalysisTaskPO pendingTask(Long userId, SceneAnalysisMessageDTO message) {
        return SceneAnalysisTaskPO.createPending(
                message.taskNo(),
                userId,
                message.target().type(),
                message.target().code(),
                message.target().name(),
                message.reportType(),
                message.config().profile(),
                this.toJsonNode(message.config().parameters()));
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
                this.klineLimit(
                        param.dailyKlineLimit(),
                        DEFAULT_STOCK_DAILY_KLINE_LIMIT,
                        MIN_STOCK_DAILY_KLINE_LIMIT),
                this.klineLimit(param.weeklyKlineLimit(), DEFAULT_STOCK_WEEKLY_KLINE_LIMIT, 1),
                this.klineLimit(param.monthlyKlineLimit(), DEFAULT_STOCK_MONTHLY_KLINE_LIMIT, 1));
    }

    private int klineLimit(Integer value, int defaultValue, int minValue) {
        int limit = value == null || value <= 0 ? defaultValue : value;
        return Math.min(Math.max(limit, minValue), MAX_STOCK_KLINE_LIMIT);
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

    private Map<String, Object> bondValuationData(BondQuoteSnapshotPO quote) {
        if (quote == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bondRating", quote.getBondRating());
        return this.compactMap(data);
    }

    private Map<String, Object> dataCompleteness(List<String> missing) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complete", missing == null || missing.isEmpty());
        result.put("missing", missing == null ? List.of() : missing);
        return result;
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return this.compactMap(this.objectMapper.convertValue(value, MAP_TYPE));
    }

    private List<Map<String, Object>> toMapList(List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(this::toMap).toList();
    }

    private Map<String, Object> compactMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StrUtil.isBlank(key) || MESSAGE_OMITTED_FIELDS.contains(key) || value == null) {
                return;
            }
            Object compactValue = this.compactValue(value);
            if (compactValue != null) {
                result.put(key, compactValue);
            }
        });
        return result;
    }

    private Object compactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                if (!(nestedKey instanceof String key)
                        || StrUtil.isBlank(key)
                        || MESSAGE_OMITTED_FIELDS.contains(key)
                        || nestedValue == null) {
                    return;
                }
                Object compactNestedValue = this.compactValue(nestedValue);
                if (compactNestedValue != null) {
                    nested.put(key, compactNestedValue);
                }
            });
            return nested;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::compactValue)
                    .filter(item -> item != null)
                    .toList();
        }
        return value;
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return null;
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOCK" -> "STOCK";
            case "INDEX" -> "INDEX";
            case "CONVERTIBLE_BOND", "BOND" -> "CONVERTIBLE_BOND";
            default -> normalized;
        };
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String newTaskNo() {
        return "scene-" + UUID.randomUUID().toString().replace("-", "");
    }

    private JsonNode toJsonNode(Object value) {
        return this.objectMapper.valueToTree(value);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("login required");
        }
        Object principal = authentication.getPrincipal();
        try {
            Method getUser = principal.getClass().getMethod("getUser");
            Object user = getUser.invoke(principal);
            Method getId = user.getClass().getMethod("getId");
            Object id = getId.invoke(user);
            if (id instanceof Long userId) {
                return userId;
            }
            if (id instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("login user id is unavailable", ex);
        }
        throw new IllegalArgumentException("login user id is unavailable");
    }

    private record StockKlineLimits(int daily, int weekly, int monthly) {
    }
}
