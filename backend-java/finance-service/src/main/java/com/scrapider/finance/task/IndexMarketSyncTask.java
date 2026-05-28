package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexDailyKlinePO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexDailyKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IndexMarketSyncTask {

    private final StockMarketApi stockMarketApi;
    private final IndexConfigManage indexConfigManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexDailyKlineManage indexDailyKlineManage;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final ExecutorService manualSyncExecutor = Executors.newSingleThreadExecutor();

    @Value("${index.sync.enabled:false}")
    private boolean enabled;

    @Value("${index.sync.daily-kline-enabled:true}")
    private boolean dailyKlineEnabled;

    @Value("${index.sync.daily-kline-limit:250}")
    private Integer dailyKlineLimit;

    @Value("${index.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${index.sync.start-time:09:29}")
    private String startTime;

    @Value("${index.sync.end-time:16:00}")
    private String endTime;

    @Value("${index.sync.timezone:Asia/Shanghai}")
    private String timezone;

    public IndexMarketSyncTask(
            StockMarketApi stockMarketApi,
            IndexConfigManage indexConfigManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexDailyKlineManage indexDailyKlineManage) {
        this.stockMarketApi = stockMarketApi;
        this.indexConfigManage = indexConfigManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexDailyKlineManage = indexDailyKlineManage;
    }

    @Scheduled(
            initialDelayString = "${index.sync.initial-delay-ms}",
            fixedDelayString = "${index.sync.fixed-delay-ms}")
    public void syncIndexMarketData() {
        if (!this.enabled) {
            log.debug("Index market sync task is disabled.");
            return;
        }
//        if (!this.isInSyncWindow()) {
//            log.debug("Index market sync task is outside sync window.");
//            return;
//        }

        this.runSyncIfIdle();
    }

    public boolean startManualSync() {
        if (!this.syncing.compareAndSet(false, true)) {
            return false;
        }
        this.manualSyncExecutor.submit(() -> {
            try {
                this.doSyncIndexMarketData();
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
            log.info("Index market sync task is already running.");
            return;
        }
        try {
            this.doSyncIndexMarketData();
        } finally {
            this.syncing.set(false);
        }
    }

    private void doSyncIndexMarketData() {
        List<IndexConfigPO> indices = this.indexConfigManage.listEnabledIndices();
        if (CollUtil.isEmpty(indices)) {
            log.info("No enabled index config found.");
            return;
        }

        log.info("Start syncing index market data, index count: {}", indices.size());
        indices.forEach(this::syncOneIndex);
        log.info("Finished syncing index market data.");
    }

    private void syncOneIndex(IndexConfigPO indexConfig) {
        if (StrUtil.isBlank(indexConfig.getSecid())) {
            log.warn("Skip index without secid, code: {}", indexConfig.getIndexCode());
            return;
        }

        try {
            StockMarketDataDTO quote = this.stockMarketApi.getQuote(indexConfig.getSecid());
            this.indexQuoteSnapshotManage.saveLatest(IndexQuoteSnapshotPO.fromApiResponse(indexConfig, quote.data()));
            this.sleepForRateLimit();
            if (this.dailyKlineEnabled) {
                StockMarketDataDTO dailyKlines =
                        this.stockMarketApi.getDailyKlines(indexConfig.getSecid(), this.dailyKlineLimit);
                this.indexDailyKlineManage.saveDailyKlines(
                        IndexDailyKlinePO.fromApiResponse(indexConfig, dailyKlines.data()));
                this.sleepForRateLimit();
            }
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync index market data, code: {}, name: {}",
                    indexConfig.getIndexCode(),
                    indexConfig.getIndexName(),
                    ex);
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
}
