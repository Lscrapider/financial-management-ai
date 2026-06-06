package com.scrapider.finance.service;

import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import java.util.List;

public interface HistoricalKlineProvider {

    List<StockKlinePO> getStockKlines(
            StockConfigPO stock,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Integer limit);

    List<IndexKlinePO> getIndexKlines(
            IndexConfigPO index,
            KlinePeriodTypeEnum periodType,
            Integer limit);

    List<BondKlinePO> getBondKlines(
            BondConfigPO bond,
            KlinePeriodTypeEnum periodType,
            Integer limit);
}
