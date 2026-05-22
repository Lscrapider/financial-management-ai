package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Data;

@Data
@TableName("stock_intraday_trend")
public class StockIntradayTrendPO {

    private static final DateTimeFormatter TREND_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long id;
    private String syncBatchNo;
    private String stockCode;
    private String stockName;
    private String secid;
    private LocalDateTime trendTime;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal averagePrice;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal previousClosePrice;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockIntradayTrendPO fromTrendLine(
            StockConfigPO stock,
            String line,
            BigDecimal previousClosePrice,
            LocalDateTime syncedAt,
            String syncBatchNo) {
        String[] parts = line.split(",");
        if (parts.length < 8) {
            return null;
        }

        StockIntradayTrendPO trend = new StockIntradayTrendPO();
        trend.setSyncBatchNo(syncBatchNo);
        trend.setStockCode(stock.getStockCode());
        trend.setStockName(stock.getStockName());
        trend.setSecid(stock.getSecid());
        trend.setTrendTime(LocalDateTime.parse(parts[0], TREND_TIME_FORMATTER));
        trend.setOpenPrice(StockMarketJsonParser.decimal(parts[1]));
        trend.setClosePrice(StockMarketJsonParser.decimal(parts[2]));
        trend.setHighPrice(StockMarketJsonParser.decimal(parts[3]));
        trend.setLowPrice(StockMarketJsonParser.decimal(parts[4]));
        trend.setVolume(StockMarketJsonParser.longValue(parts[5]));
        trend.setTurnoverAmount(StockMarketJsonParser.decimal(parts[6]));
        trend.setAveragePrice(StockMarketJsonParser.decimal(parts[7]));
        trend.setPreviousClosePrice(previousClosePrice);
        trend.setSyncedAt(syncedAt);
        return trend;
    }
}
