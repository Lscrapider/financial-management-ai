package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StockAlertConfigVO {

    private String id;
    private String userId;
    private String username;
    private String realName;
    private String email;
    private Boolean emailNotification;
    private String stockCode;
    private String stockName;
    private BigDecimal thresholdPercent;
    private Boolean enabled;
    private Boolean outOfThreshold;
    private BigDecimal latestPrice;
    private BigDecimal changePercent;
    private LocalDateTime syncedAt;
    private LocalDateTime lastAlertedAt;

    public static StockAlertConfigVO fromPO(StockAlertConfigPO config, StockQuoteSnapshotPO quote) {
        StockAlertConfigVO vo = new StockAlertConfigVO();
        vo.setId(String.valueOf(config.getId()));
        vo.setUserId(String.valueOf(config.getUserId()));
        vo.setStockCode(config.getStockCode());
        vo.setStockName(config.getStockName());
        vo.setThresholdPercent(config.getThresholdPercent());
        vo.setEnabled(config.getEnabled());
        vo.setLastAlertedAt(config.getLastAlertedAt());
        if (quote != null) {
            vo.setLatestPrice(quote.getLatestPrice());
            vo.setChangePercent(quote.getChangePercent());
            vo.setSyncedAt(quote.getSyncedAt());
        }
        vo.setOutOfThreshold(isOutOfThreshold(vo.getChangePercent(), vo.getThresholdPercent()));
        return vo;
    }

    public void fillUser(String username, String realName, String email, Boolean emailNotification) {
        this.username = username;
        this.realName = realName;
        this.email = email;
        this.emailNotification = emailNotification;
    }

    private static boolean isOutOfThreshold(BigDecimal changePercent, BigDecimal thresholdPercent) {
        if (changePercent == null || thresholdPercent == null) {
            return false;
        }
        return changePercent.compareTo(thresholdPercent) > 0
                || changePercent.compareTo(thresholdPercent.negate()) < 0;
    }
}
