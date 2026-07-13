package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;

/**
 * 确保标的查询所需数据已在本地可用。
 */
public interface AssetDataEnsureService {

    AssetDataEnsureResult ensureStockData(StockConfigPO stock);

    AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond);

    boolean ensureConvertibleBondDailyValuations(BondConfigPO bond);
}
