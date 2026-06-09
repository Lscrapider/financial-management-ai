package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.ConvertibleBondSceneDataDTO;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockConfigPO;

public interface SceneAssetDataEnsureService {

    StockSceneDataDTO ensureStockSceneData(StockConfigPO stockConfig);

    ConvertibleBondSceneDataDTO ensureBondSceneData(
            BondConfigPO bond,
            BondQuoteSnapshotPO quote,
            Integer valuationLimit,
            Integer shareLimit);
}
