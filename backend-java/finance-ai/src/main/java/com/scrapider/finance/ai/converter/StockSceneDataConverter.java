package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.util.List;

public final class StockSceneDataConverter {

    private StockSceneDataConverter() {
    }

    public static StockSceneDataDTO empty() {
        return new StockSceneDataDTO(null, List.of(), List.of(), List.of());
    }

    public static StockSceneDataDTO toDTO(
            StockIndustryInfoPO industryInfo,
            List<StockValuationHistoryPO> valuationHistory,
            List<StockFinancialIndicatorPO> financialIndicators,
            List<StockDividendHistoryPO> dividendHistory) {
        return new StockSceneDataDTO(industryInfo, valuationHistory, financialIndicators, dividendHistory);
    }
}
