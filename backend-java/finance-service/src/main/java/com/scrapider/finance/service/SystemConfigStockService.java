package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.StockConfigAddParam;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;

public interface SystemConfigStockService {

    StockConfigAddResultVO addStock(StockConfigAddParam param);
}
