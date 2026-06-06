package com.scrapider.finance.ai.service;

import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.util.List;

public interface StockFundamentalProvider {

    List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit);

    List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit);

    List<StockDividendHistoryPO> getDividendHistory(StockConfigPO stockConfig, int limit);
}
