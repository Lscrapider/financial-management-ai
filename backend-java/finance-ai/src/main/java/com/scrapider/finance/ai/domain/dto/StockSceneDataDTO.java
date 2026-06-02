package com.scrapider.finance.ai.domain.dto;

import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.util.List;

public record StockSceneDataDTO(
        StockIndustryInfoPO industryInfo,
        List<StockValuationHistoryPO> valuationHistory,
        List<StockFinancialIndicatorPO> financialIndicators,
        List<StockDividendHistoryPO> dividendHistory) {
}
