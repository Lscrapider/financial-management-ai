package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import lombok.Data;

@Data
public class StockAlertStockOptionVO {

    private String targetType;
    private String targetCode;
    private String targetName;
    private String marketCode;
    private String exchangeCode;

    public static StockAlertStockOptionVO fromStockPO(StockConfigPO po) {
        StockAlertStockOptionVO vo = new StockAlertStockOptionVO();
        vo.setTargetType("STOCK");
        vo.setTargetCode(po.getStockCode());
        vo.setTargetName(po.getStockName());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        return vo;
    }

    public static StockAlertStockOptionVO fromIndexPO(IndexConfigPO po) {
        StockAlertStockOptionVO vo = new StockAlertStockOptionVO();
        vo.setTargetType("INDEX");
        vo.setTargetCode(po.getIndexCode());
        vo.setTargetName(po.getIndexName());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        return vo;
    }

    public static StockAlertStockOptionVO fromBondPO(BondConfigPO po) {
        StockAlertStockOptionVO vo = new StockAlertStockOptionVO();
        vo.setTargetType("BOND");
        vo.setTargetCode(po.getBondCode());
        vo.setTargetName(po.getBondName());
        vo.setMarketCode(po.getMarketCode());
        vo.setExchangeCode(po.getExchangeCode());
        return vo;
    }
}
