package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.StockAlertService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockMarketSyncTask {

    private final StockMarketApi stockMarketApi;
    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final StockAlertService stockAlertService;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final ExecutorService manualSyncExecutor = Executors.newSingleThreadExecutor();

    @Value("${stock.sync.enabled:false}")
    private boolean enabled;

    @Value("${stock.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${stock.sync.start-time:09:29}")
    private String startTime;

    @Value("${stock.sync.end-time:16:00}")
    private String endTime;

    @Value("${stock.sync.timezone:Asia/Shanghai}")
    private String timezone;

    @Value("${stock.sync.trend-enabled:true}")
    private boolean trendEnabled;

    public StockMarketSyncTask(
            StockMarketApi stockMarketApi,
            StockConfigManage stockConfigManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            StockAlertService stockAlertService) {
        this.stockMarketApi = stockMarketApi;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.stockAlertService = stockAlertService;
    }

    @Scheduled(
            initialDelayString = "${stock.sync.initial-delay-ms}",
            fixedDelayString = "${stock.sync.fixed-delay-ms}")
    public void syncStockMarketData() {
        if (!this.enabled) {
            log.debug("Stock market sync task is disabled.");
            return;
        }
        if (!this.isInSyncWindow()) {
            log.debug("Stock market sync task is outside sync window.");
            return;
        }

        this.runSyncIfIdle();
    }

    /**
     * 手动触发行情快照全量同步（不含分时数据）。
     */
    public boolean startManualSync() {
        if (!this.syncing.compareAndSet(false, true)) {
            return false;
        }
        this.manualSyncExecutor.submit(() -> {
            try {
                this.doSyncStockMarketData();
            } finally {
                this.syncing.set(false);
            }
        });
        return true;
    }

    /**
     * 手动触发单只股票的分时数据同步（同步执行）。
     *
     * @param stockCode 股票代码
     * @return 是否成功同步（股票不存在返回 false）
     */
    public boolean syncStockTrend(String stockCode) {
        StockConfigPO stock = this.stockConfigManage.getEnabledByStockCode(stockCode);
        if (stock == null || StrUtil.isBlank(stock.getSecid())) {
            log.warn("Cannot sync trend: stock not found or no secid, code: {}", stockCode);
            return false;
        }
        try {
            this.doSyncTrendsForStock(stock);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync trend for stock: {}", stockCode, ex);
            return false;
        }
    }

    public boolean isSyncing() {
        return this.syncing.get();
    }

    private void runSyncIfIdle() {
        if (!this.syncing.compareAndSet(false, true)) {
            log.info("Stock market sync task is already running.");
            return;
        }
        try {
            this.doSyncStockMarketData();
        } finally {
            this.syncing.set(false);
        }
    }

    private void doSyncStockMarketData() {
        List<StockConfigPO> stocks = this.stockConfigManage.listEnabledStocks();
        if (CollUtil.isEmpty(stocks)) {
            log.info("No enabled stock config found.");
            return;
        }

        log.info("Start syncing stock quotes, stock count: {}", stocks.size());
        stocks.forEach(this::syncOneStock);
        this.stockAlertService.checkAlerts();
        log.info("Finished syncing stock quotes.");
    }

    private void syncOneStock(StockConfigPO stock) {
        if (StrUtil.isBlank(stock.getSecid())) {
            log.warn("Skip stock without secid, code: {}", stock.getStockCode());
            return;
        }

        try {
            StockMarketDataDTO quote = this.stockMarketApi.getQuote(stock.getSecid());
            this.stockQuoteSnapshotManage.saveLatest(StockQuoteSnapshotPO.fromApiResponse(stock, quote.data()));
            this.sleepForRateLimit();
            if (this.trendEnabled) {
                this.doSyncTrendsForStock(stock);
                this.sleepForRateLimit();
            }
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync stock quote, code: {}, name: {}",
                    stock.getStockCode(),
                    stock.getStockName(),
                    ex);
        }
    }

    private void doSyncTrendsForStock(StockConfigPO stock) {
        try {
            String syncBatchNo = UUID.randomUUID().toString();
            StockMarketDataDTO trends = this.stockMarketApi.getTrends(stock.getSecid());
            this.saveTrends(stock, trends.data(), syncBatchNo);
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync trend for stock, code: {}, name: {}",
                    stock.getStockCode(),
                    stock.getStockName(),
                    ex);
        }
    }

    private void saveTrends(StockConfigPO stock, JsonNode response, String syncBatchNo) {
        String tencentSymbol = this.toTencentSymbol(stock.getSecid());
        JsonNode data = response.path("data").path(tencentSymbol).path("data");
        String tradeDate = data.path("date").asText();
        BigDecimal previousClosePrice = this.previousClosePrice(response, tencentSymbol);
        ZoneId zoneId = ZoneId.of(this.timezone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime syncedAt = now;
        List<StockIntradayTrendPO> trends = StreamSupport.stream(data.path("data").spliterator(), false)
                .map(JsonNode::asText)
                .map(line -> StockIntradayTrendPO.fromTrendLine(
                        stock,
                        tradeDate,
                        line,
                        previousClosePrice,
                        syncedAt,
                        syncBatchNo))
                .filter(Objects::nonNull)
                .filter(trend -> this.isNotFutureTrend(trend, now))
                .toList();

        this.stockIntradayTrendInfluxManage.saveTrends(trends);
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

    private boolean isNotFutureTrend(StockIntradayTrendPO trend, LocalDateTime now) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null || trendTime.toLocalDate().isBefore(now.toLocalDate())) {
            return true;
        }
        return !trendTime.isAfter(now);
    }

    private BigDecimal previousClosePrice(JsonNode response, String tencentSymbol) {
        JsonNode quote = response.path("data").path(tencentSymbol).path("qt").path(tencentSymbol);
        return StockMarketJsonParser.decimal(quote.path(4).asText());
    }

    private String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }
}
