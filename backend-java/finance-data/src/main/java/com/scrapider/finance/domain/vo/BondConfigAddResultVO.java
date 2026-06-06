package com.scrapider.finance.domain.vo;

import lombok.Data;

@Data
public class BondConfigAddResultVO {

    private String bondCode;
    private String bondName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private String underlyingStockCode;
    private String underlyingStockName;
    private Boolean basicSynced;
    private Boolean underlyingStockSynced;
    private Boolean marketDataSynced;
    private Boolean dailyValuationSynced;
    private Boolean shareSynced;
    private StockConfigAddResultVO underlyingStock;
}
