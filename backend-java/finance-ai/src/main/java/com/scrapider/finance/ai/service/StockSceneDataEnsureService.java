package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.domain.po.StockConfigPO;

public interface StockSceneDataEnsureService {

    StockSceneDataDTO ensureStockSceneData(StockConfigPO stockConfig);
}
