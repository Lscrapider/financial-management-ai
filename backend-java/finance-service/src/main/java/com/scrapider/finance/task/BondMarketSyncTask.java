package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondDailyKlinePO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondDailyKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BondMarketSyncTask {

    private final StockMarketApi stockMarketApi;
    private final BondConfigManage bondConfigManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondDailyKlineManage bondDailyKlineManage;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final ExecutorService manualSyncExecutor = Executors.newSingleThreadExecutor();

    @Value("${bond.sync.enabled:false}")
    private boolean enabled;

    @Value("${bond.sync.daily-kline-enabled:true}")
    private boolean dailyKlineEnabled;

    @Value("${bond.sync.daily-kline-limit:250}")
    private Integer dailyKlineLimit;

    @Value("${bond.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${bond.sync.start-time:09:29}")
    private String startTime;

    @Value("${bond.sync.end-time:16:00}")
    private String endTime;

    @Value("${bond.sync.timezone:Asia/Shanghai}")
    private String timezone;

    public BondMarketSyncTask(
            StockMarketApi stockMarketApi,
            BondConfigManage bondConfigManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondDailyKlineManage bondDailyKlineManage) {
        this.stockMarketApi = stockMarketApi;
        this.bondConfigManage = bondConfigManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondDailyKlineManage = bondDailyKlineManage;
    }

    @Scheduled(
            initialDelayString = "${bond.sync.initial-delay-ms}",
            fixedDelayString = "${bond.sync.fixed-delay-ms}")
    public void syncBondMarketData() {
        if (!this.enabled) {
            log.debug("Bond market sync task is disabled.");
            return;
        }
        if (!this.isInSyncWindow()) {
            log.debug("Bond market sync task is outside sync window.");
            return;
        }

        this.runSyncIfIdle();
    }

    public boolean startManualSync() {
        if (!this.syncing.compareAndSet(false, true)) {
            return false;
        }
        this.manualSyncExecutor.submit(() -> {
            try {
                this.doSyncBondMarketData();
            } finally {
                this.syncing.set(false);
            }
        });
        return true;
    }

    public boolean isSyncing() {
        return this.syncing.get();
    }

    private void runSyncIfIdle() {
        if (!this.syncing.compareAndSet(false, true)) {
            log.info("Bond market sync task is already running.");
            return;
        }
        try {
            this.doSyncBondMarketData();
        } finally {
            this.syncing.set(false);
        }
    }

    private void doSyncBondMarketData() {
        List<BondConfigPO> bonds = this.bondConfigManage.listEnabledBonds();
        if (CollUtil.isEmpty(bonds)) {
            log.info("No enabled bond config found.");
            return;
        }

        log.info("Start syncing bond market data, bond count: {}", bonds.size());
        this.batchSyncQuotes(bonds);
        log.info("Finished syncing bond market data.");
    }

    private void batchSyncQuotes(List<BondConfigPO> bonds) {
        List<BondConfigPO> valid = bonds.stream()
                .filter(s -> StrUtil.isNotBlank(s.getSecid()))
                .toList();
        if (valid.isEmpty()) {
            return;
        }

        Map<String, BondConfigPO> symbolToBond = new LinkedHashMap<>();
        for (BondConfigPO bond : valid) {
            symbolToBond.put(this.toTencentSymbol(bond.getSecid()), bond);
        }

        try {
            List<String> secids = new ArrayList<>(valid.size());
            for (BondConfigPO bond : valid) {
                secids.add(bond.getSecid());
            }
            StockMarketDataDTO quote = this.stockMarketApi.getQuotes(secids);
            List<BondQuoteSnapshotPO> snapshots = BondQuoteSnapshotPO
                    .fromBatchApiResponse(quote.data().asText(), symbolToBond);
            if (CollUtil.isNotEmpty(snapshots)) {
                this.bondQuoteSnapshotManage.saveQuotesBatch(snapshots);
            }
            log.info("Batch synced {} bond quotes", snapshots.size());
        } catch (Exception ex) {
            log.warn("Failed to batch sync bond quotes, count: {}", valid.size(), ex);
        }

        if (this.dailyKlineEnabled) {
            for (BondConfigPO bond : valid) {
                try {
                    StockMarketDataDTO dailyKlines =
                            this.stockMarketApi.getDailyKlines(bond.getSecid(), this.dailyKlineLimit);
                    this.bondDailyKlineManage.saveDailyKlines(
                            BondDailyKlinePO.fromApiResponse(bond, dailyKlines.data()));
                    this.sleepForRateLimit();
                } catch (Exception ex) {
                    log.warn("Failed to sync daily kline for bond: {}", bond.getBondCode(), ex);
                }
            }
        }
    }

    private void sleepForRateLimit() throws InterruptedException {
        if (this.requestIntervalMs > 0) {
            Thread.sleep(this.requestIntervalMs);
        }
    }

    private boolean isInSyncWindow() {
        LocalTime now = LocalTime.now(ZoneId.of(this.timezone));
        LocalTime start = LocalTime.parse(this.startTime);
        LocalTime end = LocalTime.parse(this.endTime);
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }
}
