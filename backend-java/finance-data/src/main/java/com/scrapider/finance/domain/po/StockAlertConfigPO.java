package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.scrapider.finance.domain.param.StockAlertConfigSaveParam;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("stock_alert_config")
public class StockAlertConfigPO {

    private Long id;
    private Long userId;
    private String targetType;
    private String stockCode;
    private String stockName;
    private BigDecimal thresholdPercent;
    private Boolean enabled;
    private Boolean alertActive;
    private BigDecimal lastAlertChangePercent;
    private LocalDateTime lastAlertedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockAlertConfigPO fromSaveParam(
            Long userId,
            String targetCode,
            String targetName,
            StockAlertConfigSaveParam param) {
        StockAlertConfigPO config = new StockAlertConfigPO();
        config.setUserId(userId);
        config.setTargetType(param.getTargetType());
        config.setStockCode(targetCode);
        config.setStockName(targetName);
        config.setThresholdPercent(param.getThresholdPercent());
        config.setEnabled(param.getEnabled() == null || Boolean.TRUE.equals(param.getEnabled()));
        config.setAlertActive(false);
        return config;
    }
}
