package com.scrapider.finance.provider.impl;

import com.scrapider.finance.api.EastMoneyApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.provider.StockFundamentalProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.stock-fundamental", havingValue = "eastmoney", matchIfMissing = true)
public class EastMoneyStockFundamentalProvider implements StockFundamentalProvider {

    private final EastMoneyApi eastMoneyApi;

    public EastMoneyStockFundamentalProvider(EastMoneyApi eastMoneyApi) {
        this.eastMoneyApi = eastMoneyApi;
    }

    @Override
    public StockIndustryInfoPO getIndustryInfo(StockConfigPO stockConfig) {
//        StockMarketDataDTO response = this.eastMoneyApi.getStockInfo(stockConfig.getSecid());
//        return StockIndustryInfoPO.fromEastMoneyStockGetResponse(stockConfig, response.data());
        // 这个接口不稳定，不使用
        return null;
    }

    @Override
    public List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit) {
        StockMarketDataDTO response = this.eastMoneyApi.getValuationHistory(stockConfig.getStockCode(), limit);
        return StockValuationHistoryPO.fromEastMoneyResponse(stockConfig, response.data());
    }

    @Override
    public List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit) {
        StockMarketDataDTO response = this.eastMoneyApi.getMainFinancialIndicators(
                this.toSecucode(stockConfig),
                limit);
        return StockFinancialIndicatorPO.fromEastMoneyResponse(stockConfig, response.data());
    }

    @Override
    public List<StockDividendHistoryPO> getDividendHistory(StockConfigPO stockConfig, int limit) {
        StockMarketDataDTO response = this.eastMoneyApi.getDividendHistory(stockConfig.getStockCode(), limit);
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
