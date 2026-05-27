package com.scrapider.finance.domain.param;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class StockAlertConfigSaveParam {

    private Long id;
    private String stockCode;
    private BigDecimal thresholdPercent;
    private Boolean enabled;
}
