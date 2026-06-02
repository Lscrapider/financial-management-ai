package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisConfigDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.ai.service.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondDailyKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexDailyKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockDailyKlineManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.lang.reflect.Method;
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

    private static final int DAILY_KLINE_LIMIT = 250;
    private static final int INTRADAY_LIMIT = 240;
    private static final List<String> MESSAGE_OMITTED_FIELDS =
            List.of("id", "rawResponse", "createdAt", "updatedAt");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockConfigManage stockConfigManage;
    private final StockDailyKlineManage stockDailyKlineManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexConfigManage indexConfigManage;
    private final IndexDailyKlineManage indexDailyKlineManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondConfigManage bondConfigManage;
    private final BondDailyKlineManage bondDailyKlineManage;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;

    public SceneAnalysisTaskServiceImpl(
            ObjectMapper objectMapper,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockConfigManage stockConfigManage,
            StockDailyKlineManage stockDailyKlineManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexConfigManage indexConfigManage,
            IndexDailyKlineManage indexDailyKlineManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondConfigManage bondConfigManage,
            BondDailyKlineManage bondDailyKlineManage,
            SceneAnalysisTaskManage sceneAnalysisTaskManage) {
        this.objectMapper = objectMapper;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockConfigManage = stockConfigManage;
        this.stockDailyKlineManage = stockDailyKlineManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexConfigManage = indexConfigManage;
        this.indexDailyKlineManage = indexDailyKlineManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondConfigManage = bondConfigManage;
        this.bondDailyKlineManage = bondDailyKlineManage;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
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
                SceneAnalysisTaskPO.STATUS_PROCESSING);
    }

    @Override
    public void callback(String taskNo, SceneAnalysisCallbackParam param) {
        if (StrUtil.isBlank(taskNo)) {
            throw new IllegalArgumentException("taskNo is required");
        }
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String status = StrUtil.blankToDefault(param.status(), SceneAnalysisTaskPO.STATUS_SUCCESS);
        if (SceneAnalysisTaskPO.STATUS_FAILED.equals(status)) {
            this.sceneAnalysisTaskManage.markFailed(taskNo, param.errorMessage());
            return;
        }
        this.sceneAnalysisTaskManage.markSuccess(
                taskNo,
                param.currentScenesPayload(),
                param.reportPayload(),
                param.reportText());
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
        List<Map<String, Object>> intraday = this.queryStockIntraday(stockCode, missing);
        List<Map<String, Object>> dailyKlines = this.queryStockDailyKlines(stockCode, missing);
        return this.message(
                taskNo,
                param,
                target,
                this.toMap(quote),
                this.stockValuationData(quote),
                dailyKlines,
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
                .listDailyKlines(indexCode, null, null, null, DAILY_KLINE_LIMIT)
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
                dailyKlines,
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
                .listDailyKlines(bondCode, null, null, null, DAILY_KLINE_LIMIT)
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
                dailyKlines,
                List.of(),
                missing);
    }

    private SceneAnalysisMessageDTO message(
            String taskNo,
            SceneAnalysisSubmitParam param,
            SceneAnalysisTargetDTO target,
            Map<String, Object> marketData,
            Map<String, Object> valuationData,
            List<Map<String, Object>> dailyKlines,
            List<Map<String, Object>> intradayData,
            List<String> missing) {
        return new SceneAnalysisMessageDTO(
                taskNo,
                LocalDateTime.now(),
                StrUtil.blankToDefault(param.reportType(), "quick_analysis"),
                target,
                new SceneAnalysisConfigDTO(
                        StrUtil.blankToDefault(param.configProfile(), "system_recommended"),
                        SceneAnalysisUserConfigParam.effective(param.userOverrides(), target.type())),
                marketData,
                valuationData,
                dailyKlines,
                intradayData,
                this.dataCompleteness(missing));
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
        String batchNo = this.stockIntradayTrendInfluxManage.getLatestBatchNo(stockCode);
        if (StrUtil.isBlank(batchNo)) {
            missing.add("stock_intraday_trend");
            return List.of();
        }
        List<Map<String, Object>> rows = this.stockIntradayTrendInfluxManage.listByBatchNo(stockCode, batchNo).stream()
                .limit(INTRADAY_LIMIT)
                .map(this::toMap)
                .toList();
        if (rows.isEmpty()) {
            missing.add("stock_intraday_trend");
        }
        return rows;
    }

    private List<Map<String, Object>> queryStockDailyKlines(String stockCode, List<String> missing) {
        List<Map<String, Object>> rows = this.stockDailyKlineManage
                .listDailyKlines(stockCode, null, null, null, DAILY_KLINE_LIMIT)
                .stream()
                .map(this::toMap)
                .toList();
        if (rows.isEmpty()) {
            missing.add("stock_daily_kline");
        }
        return rows;
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
}
