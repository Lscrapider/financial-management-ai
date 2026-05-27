package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.StockConfigPO;
import lombok.Data;

@Data
public class StockAlertStockOptionVO {

    private String stockCode;
    private String stockName;
    private String marketCode;
    private String exchangeCode;

    public static StockAlertStockOptionVO fromPO(StockConfigPO po) {
        StockAlertStockOptionVO vo = new StockAlertStockOptionVO();
        vo.setStockCode(po.getStockCode());
        vo.setStockName(po.getStockName());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        return vo;
    }
}
