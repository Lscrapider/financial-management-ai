package com.scrapider.finance.ai.domain.dto;

import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import java.util.List;

public record ConvertibleBondSceneDataDTO(
        ConvertibleBondBasicPO basic,
        ConvertibleBondSharePO latestShare,
        List<ConvertibleBondDailyValuationPO> valuationHistory) {

    public static ConvertibleBondSceneDataDTO empty() {
        return new ConvertibleBondSceneDataDTO(null, null, List.of());
    }

    public ConvertibleBondDailyValuationPO latestValuation() {
        if (this.valuationHistory == null || this.valuationHistory.isEmpty()) {
            return null;
        }
        return this.valuationHistory.get(0);
    }
}
