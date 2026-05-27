package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;

public interface StockAlertMailService {

    void sendAlert(AppUserPO user, StockAlertConfigPO config, StockQuoteSnapshotPO quote);
}
