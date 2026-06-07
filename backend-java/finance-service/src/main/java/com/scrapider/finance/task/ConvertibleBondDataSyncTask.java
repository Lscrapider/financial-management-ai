package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.service.ConvertibleBondDataProvider;
import com.scrapider.finance.service.MarketTradingCalendarService;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConvertibleBondDataSyncTask {

    private final BondConfigManage bondConfigManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider;
    private final MarketTradingCalendarService marketTradingCalendarService;
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    @Value("${bond.sync.convertible-data-enabled:true}")
    private boolean enabled;

    @Value("${bond.sync.convertible-data-catch-up-enabled:true}")
    private boolean catchUpEnabled;

    @Value("${bond.sync.convertible-data-run-after:19:00}")
    private String runAfter;

    @Value("${bond.sync.convertible-daily-limit:250}")
    private Integer dailyLimit;

    @Value("${bond.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${bond.sync.timezone:Asia/Shanghai}")
    private String timezone;

    public ConvertibleBondDataSyncTask(
            BondConfigManage bondConfigManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider,
            MarketTradingCalendarService marketTradingCalendarService) {
        this.bondConfigManage = bondConfigManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.convertibleBondDataProvider = convertibleBondDataProvider;
        this.marketTradingCalendarService = marketTradingCalendarService;
    }

    @Scheduled(
            cron = "${bond.sync.convertible-data-cron:0 0 19 * * ?}",
            zone = "${bond.sync.timezone:Asia/Shanghai}")
    public void syncConvertibleDailyData() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(this.timezone));
        if (!this.shouldRunOnSchedule(now)) {
            return;
        }
        this.runDailySyncIfIdle();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        this.catchUpAfterStartup(LocalDateTime.now(ZoneId.of(this.timezone)));
    }

    void catchUpAfterStartup(LocalDateTime now) {
        if (!this.shouldCatchUp(now)) {
            return;
        }
        this.runDailySyncIfIdle();
    }

    private boolean shouldRunOnSchedule(LocalDateTime now) {
        if (!this.enabled) {
            log.debug("Convertible bond daily sync task is disabled.");
            return false;
        }
        if (!this.marketTradingCalendarService.isTradingDay(now.toLocalDate())) {
            log.debug("Convertible bond daily sync task is outside trading day.");
            return false;
        }
        return true;
    }

    private boolean shouldCatchUp(LocalDateTime now) {
        if (!this.enabled || !this.catchUpEnabled) {
            return false;
        }
        if (!this.marketTradingCalendarService.isTradingDay(now.toLocalDate())) {
            return false;
        }
        if (now.toLocalTime().isBefore(LocalTime.parse(this.runAfter))) {
            return false;
        }
        return !this.convertibleBondDailyValuationManage.hasSyncedSince(now.toLocalDate().atStartOfDay());
    }

    private void runDailySyncIfIdle() {
        if (!this.syncing.compareAndSet(false, true)) {
            log.info("Convertible bond daily sync task is already running.");
            return;
        }
        try {
            this.doSyncConvertibleDailyData();
        } finally {
            this.syncing.set(false);
        }
    }

    private void doSyncConvertibleDailyData() {
        ConvertibleBondDataProvider provider = this.convertibleBondDataProvider.getIfAvailable();
        List<BondConfigPO> bonds = this.bondConfigManage.listEnabledBonds();
        if (provider == null || CollUtil.isEmpty(bonds)) {
            return;
        }
        log.info("Start syncing convertible bond daily data, bond count: {}", bonds.size());
        for (BondConfigPO bond : bonds) {
            if (StrUtil.isBlank(bond.getBondCode())) {
                continue;
            }
            try {
                this.convertibleBondDailyValuationManage.saveValuations(
                        provider.getDailyValuations(bond, this.dailyLimit));
                this.sleepForRateLimit();
            } catch (Exception ex) {
                log.warn("Failed to sync convertible bond daily data for bond: {}", bond.getBondCode(), ex);
            }
        }
        log.info("Finished syncing convertible bond daily data.");
    }

    private void sleepForRateLimit() throws InterruptedException {
        if (this.requestIntervalMs > 0) {
            Thread.sleep(this.requestIntervalMs);
        }
    }
}
