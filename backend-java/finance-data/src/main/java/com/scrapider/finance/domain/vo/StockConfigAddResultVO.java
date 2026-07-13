package com.scrapider.finance.domain.vo;

import lombok.Data;

@Data
public class StockConfigAddResultVO {

    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private Boolean quoteSynced;
    private Boolean trendSynced;
    private Boolean initializationScheduled;
    private StockQuoteVO quote;

    public static StockConfigAddResultVO of(
            StockQuoteVO quote,
            boolean trendSynced,
            boolean initializationScheduled) {
        StockConfigAddResultVO result = new StockConfigAddResultVO();
        result.setStockCode(quote.getStockCode());
        result.setStockName(quote.getStockName());
        result.setSecid(quote.getSecid());
        result.setMarketCode(quote.getMarketCode());
        result.setExchangeCode(quote.getExchangeCode());
        result.setQuoteSynced(true);
        result.setTrendSynced(trendSynced);
        result.setInitializationScheduled(initializationScheduled);
        result.setQuote(quote);
        return result;
    }
}
