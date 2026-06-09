package com.scrapider.finance.ai.chat;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scrapider.finance.ai.converter.AiMarketDataConverter;
import com.scrapider.finance.ai.domain.vo.AiDataRequestVO;
import com.scrapider.finance.ai.domain.vo.AiDatabaseContextVO;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;
import com.scrapider.finance.domain.enums.BondQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.IndexQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.IndexKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
@Service
public class AiMarketDataQueryService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_INTRADAY_LIMIT = 240;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexKlineManage indexKlineManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondKlineManage bondKlineManage;
    public AiMarketDataQueryService(
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexKlineManage indexKlineManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondKlineManage bondKlineManage) {
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexKlineManage = indexKlineManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondKlineManage = bondKlineManage;
    }
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
        return AiMarketDataConverter.databaseContext(results);
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
            case "index_kline_by_code" -> this.queryIndexKlines(request);
            case "bond_quote_by_code" -> this.queryBondQuoteByCode(request);
            case "bond_quote_list" -> this.queryBondQuoteList(request);
            case "bond_kline_by_code" -> this.queryBondKlines(request);
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
        return AiMarketDataConverter.result(
                request,
                quote == null ? List.of() : List.of(AiMarketDataConverter.stockQuoteToMap(quote)));
    }
    private Map<String, Object> queryStockQuoteList(AiDataRequestVO request) {
        List<Map<String, Object>> rows = this.stockQuoteSnapshotManage.listSnapshots(
                        null,
                        this.normalizeLimit(request.limit()),
                        StockQuoteSortFieldEnum.CHANGE_PERCENT,
                        SortOrderEnum.DESC)
                .stream()
                .map(AiMarketDataConverter::stockQuoteToMap)
                .toList();
        return AiMarketDataConverter.result(request, rows);
    }
    private Map<String, Object> queryStockIntradayByCode(AiDataRequestVO request) {
        request = this.resolveStockRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        List<Map<String, Object>> rows = this.stockIntradayTrendInfluxManage
                .listLatestTradingTrends(request.targetCode())
                .stream()
                .limit(this.normalizeIntradayLimit(request.limit()))
                .map(AiMarketDataConverter::stockIntradayToMap)
                .toList();
        return AiMarketDataConverter.result(request, rows);
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
        return AiMarketDataConverter.result(
                request,
                quote == null ? List.of() : List.of(AiMarketDataConverter.indexQuoteToMap(quote)));
    }
    private Map<String, Object> queryIndexQuoteList(AiDataRequestVO request) {
        List<Map<String, Object>> rows = this.indexQuoteSnapshotManage.listSnapshots(
                        null,
                        this.normalizeLimit(request.limit()),
                        IndexQuoteSortFieldEnum.INDEX_CODE,
                        SortOrderEnum.ASC)
                .stream()
                .map(AiMarketDataConverter::indexQuoteToMap)
                .toList();
        return AiMarketDataConverter.result(request, rows);
    }
    private Map<String, Object> queryIndexKlines(AiDataRequestVO request) {
        request = this.resolveIndexRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        List<Map<String, Object>> rows = this.indexKlineManage.listKlines(
                        request.targetCode(),
                        null,
                        KlinePeriodTypeEnum.DAILY,
                        null,
                        null,
                        this.normalizeLimit(request.limit()))
                .stream()
                .map(AiMarketDataConverter::indexKlineToMap)
                .toList();
        return AiMarketDataConverter.result(request, rows);
    }
    private Map<String, Object> queryBondQuoteByCode(AiDataRequestVO request) {
        request = this.resolveBondRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        BondQuoteSnapshotPO quote = this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .eq(BondQuoteSnapshotPO::getBondCode, request.targetCode())
                        .last("LIMIT 1"));
        return AiMarketDataConverter.result(
                request,
                quote == null ? List.of() : List.of(AiMarketDataConverter.bondQuoteToMap(quote)));
    }
    private Map<String, Object> queryBondQuoteList(AiDataRequestVO request) {
        List<Map<String, Object>> rows = this.bondQuoteSnapshotManage.listSnapshots(
                        null,
                        this.normalizeLimit(request.limit()),
                        BondQuoteSortFieldEnum.CHANGE_PERCENT,
                        SortOrderEnum.DESC)
                .stream()
                .map(AiMarketDataConverter::bondQuoteToMap)
                .toList();
        return AiMarketDataConverter.result(request, rows);
    }
    private Map<String, Object> queryBondKlines(AiDataRequestVO request) {
        request = this.resolveBondRequest(request);
        if (StrUtil.isBlank(request.targetCode())) {
            return Map.of();
        }
        List<Map<String, Object>> rows = this.bondKlineManage.listKlines(
                        request.targetCode(),
                        null,
                        KlinePeriodTypeEnum.DAILY,
                        null,
                        null,
                        this.normalizeLimit(request.limit()))
                .stream()
                .map(AiMarketDataConverter::bondKlineToMap)
                .toList();
        return AiMarketDataConverter.result(request, rows);
    }
    private AiDataRequestVO resolveStockRequest(AiDataRequestVO request) {
        if (StrUtil.isNotBlank(request.targetCode()) || StrUtil.isBlank(request.targetName())) {
            return request;
        }
        StockQuoteSnapshotPO quote = this.findStockQuoteByName(request.targetName());
        if (quote == null || StrUtil.isBlank(quote.getStockCode())) {
            return request;
        }
        return AiMarketDataConverter.resolvedRequest(request, quote.getStockCode());
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
        return AiMarketDataConverter.resolvedRequest(request, quote.getIndexCode());
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
    private AiDataRequestVO resolveBondRequest(AiDataRequestVO request) {
        if (StrUtil.isNotBlank(request.targetCode()) || StrUtil.isBlank(request.targetName())) {
            return request;
        }
        BondQuoteSnapshotPO quote = this.findBondQuoteByName(request.targetName());
        if (quote == null || StrUtil.isBlank(quote.getBondCode())) {
            return request;
        }
        return AiMarketDataConverter.resolvedRequest(request, quote.getBondCode());
    }
    private BondQuoteSnapshotPO findBondQuoteByName(String bondName) {
        BondQuoteSnapshotPO quote = this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .eq(BondQuoteSnapshotPO::getBondName, bondName)
                        .last("LIMIT 1"));
        if (quote != null) {
            return quote;
        }
        return this.bondQuoteSnapshotManage.getOne(
                new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                        .like(BondQuoteSnapshotPO::getBondName, bondName)
                        .last("LIMIT 1"));
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
