package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scrapider.finance.ai.domain.vo.AiDataRequestVO;
import com.scrapider.finance.ai.domain.vo.AiDatabaseContextVO;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;
import com.scrapider.finance.ai.service.AiMarketDataQueryService;
import com.scrapider.finance.domain.enums.IndexQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.po.IndexDailyKlinePO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.IndexDailyKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiMarketDataQueryServiceImpl implements AiMarketDataQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_INTRADAY_LIMIT = 240;

    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexDailyKlineManage indexDailyKlineManage;

    public AiMarketDataQueryServiceImpl(
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexDailyKlineManage indexDailyKlineManage) {
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexDailyKlineManage = indexDailyKlineManage;
    }

    @Override
    public AiDatabaseContextVO query(AiQueryRewriteVO queryRewrite) {
        if (queryRewrite == null
                || !Boolean.TRUE.equals(queryRewrite.enabled())
                || !Boolean.TRUE.equals(queryRewrite.requiresMarketData())
                || CollUtil.isEmpty(queryRewrite.dataRequests())) {
            return AiDatabaseContextVO.empty();
        }
        List<Map<String, Object>> results = queryRewrite.dataRequests().stream()
                .map(this::queryOne)
                .filter(item -> !item.isEmpty())
                .toList();
        return new AiDatabaseContextVO(results);
    }

    private Map<String, Object> queryOne(AiDataRequestVO request) {
        if (request == null || StrUtil.isBlank(request.queryType())) {
            return Map.of();
        }
        return switch (request.queryType()) {
            case "stock_quote_by_code" -> this.queryStockQuoteByCode(request);
            case "stock_intraday_by_code" -> this.queryStockIntradayByCode(request);
            case "stock_quote_list" -> this.queryStockQuoteList(request);
            case "index_quote_by_code" -> this.queryIndexQuoteByCode(request);
            case "index_quote_list" -> this.queryIndexQuoteList(request);
            case "index_daily_kline_by_code" -> this.queryIndexDailyKlines(request);
            default -> Map.of();
        };
    }

    private Map<String, Object> queryStockQuoteByCode(AiDataRequestVO request) {
        request = this.resolveStockRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        StockQuoteSnapshotPO quote = this.stockQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .eq(StockQuoteSnapshotPO::getStockCode, request.targetCode())
                        .last("LIMIT 1"));
        return this.result(request, quote == null ? List.of() : List.of(this.stockQuoteToMap(quote)));
    }

    private Map<String, Object> queryStockQuoteList(AiDataRequestVO request) {
        List<Map<String, Object>> rows = this.stockQuoteSnapshotManage.listSnapshots(
                        null,
                        this.normalizeLimit(request.limit()),
                        StockQuoteSortFieldEnum.CHANGE_PERCENT,
                        SortOrderEnum.DESC)
                .stream()
                .map(this::stockQuoteToMap)
                .toList();
        return this.result(request, rows);
    }

    private Map<String, Object> queryStockIntradayByCode(AiDataRequestVO request) {
        request = this.resolveStockRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        String latestBatchNo = this.stockIntradayTrendInfluxManage.getLatestBatchNo(request.targetCode());
        if (StrUtil.isBlank(latestBatchNo)) {
            return this.result(request, List.of());
        }
        List<Map<String, Object>> rows = this.stockIntradayTrendInfluxManage
                .listByBatchNo(request.targetCode(), latestBatchNo)
                .stream()
                .limit(this.normalizeIntradayLimit(request.limit()))
                .map(this::stockIntradayToMap)
                .toList();
        Map<String, Object> result = this.result(request, rows);
        result.put("syncBatchNo", latestBatchNo);
        return result;
    }

    private Map<String, Object> queryIndexQuoteByCode(AiDataRequestVO request) {
        request = this.resolveIndexRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        IndexQuoteSnapshotPO quote = this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .eq(IndexQuoteSnapshotPO::getIndexCode, request.targetCode())
                        .last("LIMIT 1"));
        return this.result(request, quote == null ? List.of() : List.of(this.indexQuoteToMap(quote)));
    }

    private Map<String, Object> queryIndexQuoteList(AiDataRequestVO request) {
        List<Map<String, Object>> rows = this.indexQuoteSnapshotManage.listSnapshots(
                        null,
                        this.normalizeLimit(request.limit()),
                        IndexQuoteSortFieldEnum.INDEX_CODE,
                        SortOrderEnum.ASC)
                .stream()
                .map(this::indexQuoteToMap)
                .toList();
        return this.result(request, rows);
    }

    private Map<String, Object> queryIndexDailyKlines(AiDataRequestVO request) {
        request = this.resolveIndexRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        List<Map<String, Object>> rows = this.indexDailyKlineManage.listDailyKlines(
                        request.targetCode(),
                        null,
                        null,
                        null,
                        this.normalizeLimit(request.limit()))
                .stream()
                .map(this::indexDailyKlineToMap)
                .toList();
        return this.result(request, rows);
    }

    private Map<String, Object> result(AiDataRequestVO request, List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryType", request.queryType());
        result.put("source", request.source());
        result.put("targetCode", request.targetCode());
        result.put("targetName", request.targetName());
        result.put("rows", rows);
        return result;
    }

    private AiDataRequestVO resolveStockRequest(AiDataRequestVO request) {
        if (StrUtil.isNotBlank(request.targetCode()) || StrUtil.isBlank(request.targetName())) {
            return request;
        }
        StockQuoteSnapshotPO quote = this.findStockQuoteByName(request.targetName());
        if (quote == null || StrUtil.isBlank(quote.getStockCode())) {
            return request;
        }
        return new AiDataRequestVO(
                request.source(),
                request.queryType(),
                quote.getStockCode(),
                request.targetName(),
                request.limit());
    }

    private StockQuoteSnapshotPO findStockQuoteByName(String stockName) {
        StockQuoteSnapshotPO quote = this.stockQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .eq(StockQuoteSnapshotPO::getStockName, stockName)
                        .last("LIMIT 1"));
        if (quote != null) {
            return quote;
        }
        return this.stockQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .like(StockQuoteSnapshotPO::getStockName, stockName)
                        .last("LIMIT 1"));
    }

    private AiDataRequestVO resolveIndexRequest(AiDataRequestVO request) {
        if (StrUtil.isNotBlank(request.targetCode()) || StrUtil.isBlank(request.targetName())) {
            return request;
        }
        IndexQuoteSnapshotPO quote = this.findIndexQuoteByName(request.targetName());
        if (quote == null || StrUtil.isBlank(quote.getIndexCode())) {
            return request;
        }
        return new AiDataRequestVO(
                request.source(),
                request.queryType(),
                quote.getIndexCode(),
                request.targetName(),
                request.limit());
    }

    private IndexQuoteSnapshotPO findIndexQuoteByName(String indexName) {
        IndexQuoteSnapshotPO quote = this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .eq(IndexQuoteSnapshotPO::getIndexName, indexName)
                        .last("LIMIT 1"));
        if (quote != null) {
            return quote;
        }
        return this.indexQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .like(IndexQuoteSnapshotPO::getIndexName, indexName)
                        .last("LIMIT 1"));
    }

    private Map<String, Object> stockQuoteToMap(StockQuoteSnapshotPO quote) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stockCode", quote.getStockCode());
        row.put("stockName", quote.getStockName());
        row.put("latestPrice", quote.getLatestPrice());
        row.put("changePercent", quote.getChangePercent());
        row.put("turnoverRate", quote.getTurnoverRate());
        row.put("volume", quote.getVolume());
        row.put("turnoverAmount", quote.getTurnoverAmount());
        row.put("peTtm", quote.getPeTtm());
        row.put("pbRatio", quote.getPbRatio());
        row.put("syncedAt", quote.getSyncedAt());
        return row;
    }

    private Map<String, Object> stockIntradayToMap(StockIntradayTrendPO trend) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stockCode", trend.getStockCode());
        row.put("stockName", trend.getStockName());
        row.put("trendTime", trend.getTrendTime());
        row.put("closePrice", trend.getClosePrice());
        row.put("averagePrice", trend.getAveragePrice());
        row.put("volume", trend.getVolume());
        row.put("turnoverAmount", trend.getTurnoverAmount());
        row.put("previousClosePrice", trend.getPreviousClosePrice());
        row.put("syncedAt", trend.getSyncedAt());
        return row;
    }

    private Map<String, Object> indexQuoteToMap(IndexQuoteSnapshotPO quote) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("indexCode", quote.getIndexCode());
        row.put("indexName", quote.getIndexName());
        row.put("latestPrice", quote.getLatestPrice());
        row.put("changePercent", quote.getChangePercent());
        row.put("volume", quote.getVolume());
        row.put("turnoverAmount", quote.getTurnoverAmount());
        row.put("syncedAt", quote.getSyncedAt());
        return row;
    }

    private Map<String, Object> indexDailyKlineToMap(IndexDailyKlinePO kline) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("indexCode", kline.getIndexCode());
        row.put("indexName", kline.getIndexName());
        row.put("tradeDate", kline.getTradeDate());
        row.put("openPrice", kline.getOpenPrice());
        row.put("closePrice", kline.getClosePrice());
        row.put("highPrice", kline.getHighPrice());
        row.put("lowPrice", kline.getLowPrice());
        row.put("changePercent", kline.getChangePercent());
        row.put("volume", kline.getVolume());
        row.put("turnoverAmount", kline.getTurnoverAmount());
        return row;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeIntradayLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return MAX_INTRADAY_LIMIT;
        }
        return Math.min(limit, MAX_INTRADAY_LIMIT);
    }
}
