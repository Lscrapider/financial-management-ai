package com.scrapider.finance.converter;

import com.scrapider.finance.domain.param.StockConfigAddParam;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.vo.BondConfigAddResultVO;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;

public final class SystemConfigConverter {

    private SystemConfigConverter() {
    }

    public static StockConfigPO toStockConfig(
            String stockCode,
            String stockName,
            String exchangeCode,
            String marketCode,
            String secid) {
        StockConfigPO stock = new StockConfigPO();
        stock.setStockCode(stockCode);
        stock.setStockName(stockName);
        stock.setExchangeCode(exchangeCode);
        stock.setMarketCode(marketCode);
        stock.setSecid(secid);
        stock.setEnabled(true);
        stock.setRemark("系统配置页面新增股票");
        return stock;
    }

    public static StockConfigAddParam toStockConfigAddParam(String stockCode, String stockName) {
        StockConfigAddParam stockParam = new StockConfigAddParam();
        stockParam.setStockCode(stockCode);
        stockParam.setStockName(stockName);
        return stockParam;
    }

    public static BondConfigPO toBondConfig(
            String bondCode,
            String bondName,
            String exchangeCode,
            String secid) {
        BondConfigPO bond = new BondConfigPO();
        bond.setBondCode(bondCode);
        bond.setBondName(bondName);
        bond.setMarketCode("BOND");
        bond.setExchangeCode(exchangeCode);
        bond.setSecid(secid);
        bond.setEnabled(true);
        bond.setRemark("系统配置页面新增可转债");
        return bond;
    }

    public static BondConfigAddResultVO toBondConfigAddResult(
            BondConfigPO bond,
            ConvertibleBondBasicPO basic,
            String underlyingStockCode,
            StockConfigAddResultVO underlyingStock,
            boolean marketDataSynced,
            boolean convertibleDataSynced) {
        BondConfigAddResultVO result = new BondConfigAddResultVO();
        result.setBondCode(bond.getBondCode());
        result.setBondName(bond.getBondName());
        result.setSecid(bond.getSecid());
        result.setMarketCode(bond.getMarketCode());
        result.setExchangeCode(bond.getExchangeCode());
        result.setUnderlyingStockCode(underlyingStockCode);
        result.setUnderlyingStockName(basic.getUnderlyingStockName());
        result.setBasicSynced(true);
        result.setUnderlyingStockSynced(underlyingStock != null);
        result.setMarketDataSynced(marketDataSynced);
        result.setDailyValuationSynced(convertibleDataSynced);
        result.setShareSynced(convertibleDataSynced);
        result.setUnderlyingStock(underlyingStock);
        return result;
    }
}
