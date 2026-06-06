package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.api.EastMoneyDividendApi;
import com.scrapider.finance.ai.api.EastMoneyFinanceApi;
import com.scrapider.finance.ai.api.EastMoneyStockApi;
import com.scrapider.finance.ai.api.EastMoneyValuationApi;
import com.scrapider.finance.ai.service.StockFundamentalProvider;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.stock-fundamental", havingValue = "eastmoney", matchIfMissing = true)
public class EastMoneyStockFundamentalProvider implements StockFundamentalProvider {

    private final EastMoneyValuationApi eastMoneyValuationApi;
    private final EastMoneyFinanceApi eastMoneyFinanceApi;
    private final EastMoneyDividendApi eastMoneyDividendApi;
    private final EastMoneyStockApi eastMoneyStockApi;

    public EastMoneyStockFundamentalProvider(
            EastMoneyValuationApi eastMoneyValuationApi,
            EastMoneyFinanceApi eastMoneyFinanceApi,
            EastMoneyDividendApi eastMoneyDividendApi,
            EastMoneyStockApi eastMoneyStockApi) {
        this.eastMoneyValuationApi = eastMoneyValuationApi;
        this.eastMoneyFinanceApi = eastMoneyFinanceApi;
        this.eastMoneyDividendApi = eastMoneyDividendApi;
        this.eastMoneyStockApi = eastMoneyStockApi;
    }

    @Override
    public StockIndustryInfoPO getIndustryInfo(StockConfigPO stockConfig) {
        StockMarketDataDTO response = this.eastMoneyStockApi.getStockInfo(stockConfig.getSecid());
        return StockIndustryInfoPO.fromEastMoneyStockGetResponse(stockConfig, response.data());
    }

    @Override
    public List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit) {
        StockMarketDataDTO response = this.eastMoneyValuationApi.getValuationHistory(stockConfig.getStockCode(), limit);
        return StockValuationHistoryPO.fromEastMoneyResponse(stockConfig, response.data());
    }

    @Override
    public List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit) {
        StockMarketDataDTO response = this.eastMoneyFinanceApi.getMainFinancialIndicators(
                this.toSecucode(stockConfig),
                limit);
        return StockFinancialIndicatorPO.fromEastMoneyResponse(stockConfig, response.data());
    }

    @Override
    public List<StockDividendHistoryPO> getDividendHistory(StockConfigPO stockConfig, int limit) {
        StockMarketDataDTO response = this.eastMoneyDividendApi.getDividendHistory(stockConfig.getStockCode(), limit);
        return StockDividendHistoryPO.fromEastMoneyResponse(stockConfig, response.data());
    }

    private String toSecucode(StockConfigPO stockConfig) {
        String[] parts = stockConfig.getSecid().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + stockConfig.getSecid());
        }
        return "%s.%s".formatted(stockConfig.getStockCode(), "1".equals(parts[0]) ? "SH" : "SZ");
    }
}
