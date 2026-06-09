package com.scrapider.finance.service.provider;

import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.util.List;

public interface StockFundamentalProvider {

    StockIndustryInfoPO getIndustryInfo(StockConfigPO stockConfig);

    List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit);

    List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit);

    List<StockDividendHistoryPO> getDividendHistory(StockConfigPO stockConfig, int limit);
}
