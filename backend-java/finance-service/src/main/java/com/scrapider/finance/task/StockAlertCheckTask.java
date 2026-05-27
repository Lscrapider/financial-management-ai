package com.scrapider.finance.task;

import com.scrapider.finance.service.StockAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockAlertCheckTask {

    private final StockAlertService stockAlertService;

    @Value("${stock.alert.enabled:true}")
    private boolean enabled;

    public StockAlertCheckTask(StockAlertService stockAlertService) {
        this.stockAlertService = stockAlertService;
    }

    @Scheduled(
            initialDelayString = "${stock.alert.initial-delay-ms:30000}",
            fixedDelayString = "${stock.alert.fixed-delay-ms:120000}")
    public void checkAlerts() {
        if (!this.enabled) {
            log.debug("Stock alert check task is disabled.");
            return;
        }
        this.stockAlertService.checkAlerts();
    }
}
