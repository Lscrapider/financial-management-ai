package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisTargetOptionVO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;

public final class SceneAnalysisTargetConverter {

    private SceneAnalysisTargetConverter() {
    }

    public static SceneAnalysisTargetOptionVO stockOption(StockConfigPO po) {
        return new SceneAnalysisTargetOptionVO(
                "STOCK",
                po.getStockCode(),
                po.getStockName(),
                po.getSecid(),
                po.getMarketCode(),
                po.getExchangeCode());
    }

    public static SceneAnalysisTargetOptionVO indexOption(IndexConfigPO po) {
        return new SceneAnalysisTargetOptionVO(
                "INDEX",
                po.getIndexCode(),
                po.getIndexName(),
                po.getSecid(),
                po.getMarketCode(),
                po.getExchangeCode());
    }

    public static SceneAnalysisTargetOptionVO bondOption(BondConfigPO po) {
        return new SceneAnalysisTargetOptionVO(
                "CONVERTIBLE_BOND",
                po.getBondCode(),
                po.getBondName(),
                po.getSecid(),
                po.getMarketCode(),
                po.getExchangeCode());
    }
}
