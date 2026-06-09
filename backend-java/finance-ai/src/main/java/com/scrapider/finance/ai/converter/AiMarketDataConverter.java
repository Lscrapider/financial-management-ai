package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.vo.AiDataRequestVO;
import com.scrapider.finance.ai.domain.vo.AiDatabaseContextVO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.BondIntradayTrendPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.IndexIntradayTrendPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiMarketDataConverter {

    private AiMarketDataConverter() {
    }

    public static Map<String, Object> result(AiDataRequestVO request, List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryType", request.queryType());
        result.put("source", request.source());
        result.put("targetCode", request.targetCode());
        result.put("targetName", request.targetName());
        result.put("rows", rows);
        return result;
    }

    public static AiDatabaseContextVO databaseContext(List<Map<String, Object>> results) {
        return new AiDatabaseContextVO(results);
    }

    public static AiDataRequestVO resolvedRequest(AiDataRequestVO request, String targetCode) {
        return new AiDataRequestVO(
                request.source(),
                request.queryType(),
                targetCode,
                request.targetName(),
                request.limit());
    }

    public static Map<String, Object> stockQuoteToMap(StockQuoteSnapshotPO quote) {
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

    public static Map<String, Object> stockIntradayToMap(StockIntradayTrendPO trend) {
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

    public static Map<String, Object> stockKlineToMap(StockKlinePO kline) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stockCode", kline.getStockCode());
        row.put("stockName", kline.getStockName());
        row.put("tradeDate", kline.getTradeDate());
        row.put("openPrice", kline.getOpenPrice());
        row.put("closePrice", kline.getClosePrice());
        row.put("highPrice", kline.getHighPrice());
        row.put("lowPrice", kline.getLowPrice());
        row.put("changePercent", kline.getChangePercent());
        row.put("volume", kline.getVolume());
        row.put("turnoverAmount", kline.getTurnoverAmount());
        row.put("amplitude", kline.getAmplitude());
        row.put("turnoverRate", kline.getTurnoverRate());
        row.put("ma5", kline.getMa5());
        row.put("ma10", kline.getMa10());
        row.put("ma20", kline.getMa20());
        return row;
    }

    public static Map<String, Object> indexQuoteToMap(IndexQuoteSnapshotPO quote) {
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

    public static Map<String, Object> indexIntradayToMap(IndexIntradayTrendPO trend) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("indexCode", trend.getIndexCode());
        row.put("indexName", trend.getIndexName());
        row.put("trendTime", trend.getTrendTime());
        row.put("closePrice", trend.getClosePrice());
        row.put("averagePrice", trend.getAveragePrice());
        row.put("volume", trend.getVolume());
        row.put("turnoverAmount", trend.getTurnoverAmount());
        row.put("previousClosePrice", trend.getPreviousClosePrice());
        row.put("syncedAt", trend.getSyncedAt());
        return row;
    }

    public static Map<String, Object> indexKlineToMap(IndexKlinePO kline) {
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

    public static Map<String, Object> bondQuoteToMap(BondQuoteSnapshotPO quote) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bondCode", quote.getBondCode());
        row.put("bondName", quote.getBondName());
        row.put("latestPrice", quote.getLatestPrice());
        row.put("changePercent", quote.getChangePercent());
        row.put("volume", quote.getVolume());
        row.put("turnoverAmount", quote.getTurnoverAmount());
        row.put("bondRating", quote.getBondRating());
        row.put("syncedAt", quote.getSyncedAt());
        return row;
    }

    public static Map<String, Object> bondIntradayToMap(BondIntradayTrendPO trend) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bondCode", trend.getBondCode());
        row.put("bondName", trend.getBondName());
        row.put("trendTime", trend.getTrendTime());
        row.put("closePrice", trend.getClosePrice());
        row.put("averagePrice", trend.getAveragePrice());
        row.put("volume", trend.getVolume());
        row.put("turnoverAmount", trend.getTurnoverAmount());
        row.put("previousClosePrice", trend.getPreviousClosePrice());
        row.put("syncedAt", trend.getSyncedAt());
        return row;
    }

    public static Map<String, Object> bondKlineToMap(BondKlinePO kline) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bondCode", kline.getBondCode());
        row.put("bondName", kline.getBondName());
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
}
