package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import java.math.BigDecimal;

public interface StockAlertMailService {

    void sendAlert(AppUserPO user, StockAlertConfigPO config, BigDecimal latestPrice, BigDecimal changePercent, String syncedAt);
}
