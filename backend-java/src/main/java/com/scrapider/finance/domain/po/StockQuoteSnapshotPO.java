package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("stock_quote_snapshot")
public class StockQuoteSnapshotPO {

    private Long id;
    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private BigDecimal latestPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClosePrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal volumeRatio;
    private BigDecimal limitUpPrice;
    private BigDecimal limitDownPrice;
    private BigDecimal totalMarketValue;
    private BigDecimal floatMarketValue;
    private BigDecimal peTtm;
    private BigDecimal pbRatio;
    private Integer tradeStatus;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockQuoteSnapshotPO fromApiResponse(StockConfigPO stock, JsonNode response) {
        JsonNode data = response.path("data");
        LocalDateTime now = LocalDateTime.now();

        StockQuoteSnapshotPO snapshot = new StockQuoteSnapshotPO();
        snapshot.setStockCode(StockMarketJsonParser.text(data, "f57", stock.getStockCode()));
        snapshot.setStockName(StockMarketJsonParser.text(data, "f58", stock.getStockName()));
        snapshot.setSecid(stock.getSecid());
        snapshot.setMarketCode(stock.getMarketCode());
        snapshot.setExchangeCode(stock.getExchangeCode());
        snapshot.setLatestPrice(StockMarketJsonParser.price(data, "f43"));
        snapshot.setHighPrice(StockMarketJsonParser.price(data, "f44"));
        snapshot.setLowPrice(StockMarketJsonParser.price(data, "f45"));
        snapshot.setOpenPrice(StockMarketJsonParser.price(data, "f46"));
        snapshot.setVolume(StockMarketJsonParser.longValue(data, "f47"));
        snapshot.setTurnoverAmount(StockMarketJsonParser.decimal(data, "f48"));
        snapshot.setVolumeRatio(StockMarketJsonParser.percentLike(data, "f50"));
        snapshot.setLimitUpPrice(StockMarketJsonParser.price(data, "f51"));
        snapshot.setLimitDownPrice(StockMarketJsonParser.price(data, "f52"));
        snapshot.setPreviousClosePrice(StockMarketJsonParser.price(data, "f60"));
        snapshot.setTotalMarketValue(StockMarketJsonParser.decimal(data, "f116"));
        snapshot.setFloatMarketValue(StockMarketJsonParser.decimal(data, "f117"));
        snapshot.setPeTtm(StockMarketJsonParser.percentLike(data, "f162"));
        snapshot.setPbRatio(StockMarketJsonParser.percentLike(data, "f167"));
        snapshot.setTurnoverRate(StockMarketJsonParser.percentLike(data, "f168"));
        snapshot.setChangeAmount(StockMarketJsonParser.price(data, "f169"));
        snapshot.setChangePercent(StockMarketJsonParser.percentLike(data, "f170"));
        snapshot.setAmplitude(StockMarketJsonParser.percentLike(data, "f171"));
        snapshot.setTradeStatus(StockMarketJsonParser.intValue(data, "f292"));
        snapshot.setRawResponse(response.toString());
        snapshot.setSyncedAt(now);
        return snapshot;
    }
}
