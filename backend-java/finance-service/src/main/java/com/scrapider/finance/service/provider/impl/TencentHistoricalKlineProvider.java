package com.scrapider.finance.service.provider.impl;

import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.service.provider.HistoricalKlineProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.historical-kline", havingValue = "tencent")
public class TencentHistoricalKlineProvider implements HistoricalKlineProvider {

    private final StockMarketApi stockMarketApi;

    public TencentHistoricalKlineProvider(StockMarketApi stockMarketApi) {
        this.stockMarketApi = stockMarketApi;
    }

    @Override
    public List<StockKlinePO> getStockKlines(
            StockConfigPO stock,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Integer limit) {
        StockMarketDataDTO klines = this.stockMarketApi.getKlines(stock.getSecid(), periodType, adjustType, limit);
        return StockKlinePO.fromApiResponse(stock, klines.data(), periodType, adjustType);
    }

    @Override
    public List<IndexKlinePO> getIndexKlines(IndexConfigPO index, KlinePeriodTypeEnum periodType, Integer limit) {
        StockMarketDataDTO klines = this.stockMarketApi.getKlines(
                index.getSecid(),
                periodType,
                KlineAdjustTypeEnum.NONE,
                limit);
        return IndexKlinePO.fromApiResponse(index, klines.data(), periodType);
    }

    @Override
    public List<BondKlinePO> getBondKlines(BondConfigPO bond, KlinePeriodTypeEnum periodType, Integer limit) {
        StockMarketDataDTO klines = this.stockMarketApi.getKlines(
                bond.getSecid(),
                periodType,
                KlineAdjustTypeEnum.NONE,
                limit);
        return BondKlinePO.fromApiResponse(bond, klines.data(), periodType);
    }
}
