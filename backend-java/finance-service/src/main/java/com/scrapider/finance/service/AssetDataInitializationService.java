package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;

/**
 * 新增标的后的后台初始化调度入口。
 */
public interface AssetDataInitializationService {

    boolean scheduleStockInitialization(StockConfigPO stock);

    boolean scheduleConvertibleBondInitialization(BondConfigPO bond);
}
