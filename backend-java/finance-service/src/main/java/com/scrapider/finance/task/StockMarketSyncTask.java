package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.StockAlertService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final KlineAdjustTypeEnum DEFAULT_KLINE_ADJUST_TYPE = KlineAdjustTypeEnum.HFQ;
    private static final DateTimeFormatter TRADE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);

    private final StockMarketApi stockMarketApi;
    private final StockConfigManage stockConfigManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockKlineManage stockKlineManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final StockAlertService stockAlertService;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final ExecutorService manualSyncExecutor = Executors.newSingleThreadExecutor();

    @Value("${stock.sync.enabled:false}")
    private boolean enabled;

    @Value("${stock.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${stock.sync.daily-kline-enabled:true}")
    private boolean dailyKlineEnabled;

    @Value("${stock.sync.daily-kline-limit:250}")
    private Integer dailyKlineLimit;

    @Value("${stock.sync.weekly-kline-enabled:true}")
    private boolean weeklyKlineEnabled;

    @Value("${stock.sync.weekly-kline-limit:250}")
    private Integer weeklyKlineLimit;

    @Value("${stock.sync.monthly-kline-enabled:true}")
    private boolean monthlyKlineEnabled;

    @Value("${stock.sync.monthly-kline-limit:250}")
    private Integer monthlyKlineLimit;

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
            StockKlineManage stockKlineManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            StockAlertService stockAlertService) {
        this.stockMarketApi = stockMarketApi;
        this.stockConfigManage = stockConfigManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockKlineManage = stockKlineManage;
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

    public boolean syncStockDailyKline(String stockCode) {
        StockConfigPO stock = this.stockConfigManage.getEnabledByStockCode(stockCode);
        if (stock == null || StrUtil.isBlank(stock.getSecid())) {
            log.warn("Cannot sync daily kline: stock not found or no secid, code: {}", stockCode);
            return false;
        }
        try {
            this.doSyncDailyKlinesForStock(stock);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync daily kline for stock: {}", stockCode, ex);
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
        this.batchSyncQuotes(stocks);
        this.stockAlertService.checkAlerts();
        log.info("Finished syncing stock quotes.");
    }

    private void batchSyncQuotes(List<StockConfigPO> stocks) {
        List<StockConfigPO> valid = stocks.stream()
                .filter(s -> StrUtil.isNotBlank(s.getSecid()))
                .toList();
        if (valid.isEmpty()) {
            return;
        }

        Map<String, StockConfigPO> symbolToStock = new LinkedHashMap<>();
        for (StockConfigPO stock : valid) {
            symbolToStock.put(this.toTencentSymbol(stock.getSecid()), stock);
        }

        try {
            List<String> secids = new ArrayList<>(valid.size());
            for (StockConfigPO stock : valid) {
                secids.add(stock.getSecid());
            }
            StockMarketDataDTO quote = this.stockMarketApi.getQuotes(secids);
            List<StockQuoteSnapshotPO> snapshots = StockQuoteSnapshotPO
                    .fromBatchApiResponse(quote.data().asText(), symbolToStock);
            if (CollUtil.isNotEmpty(snapshots)) {
                this.stockQuoteSnapshotManage.saveQuotesBatch(snapshots);
            }
            log.info("Batch synced {} stock quotes", snapshots.size());
        } catch (Exception ex) {
            log.warn("Failed to batch sync stock quotes, count: {}", valid.size(), ex);
        }

        if (this.trendEnabled) {
            for (StockConfigPO stock : valid) {
                try {
                    this.doSyncTrendsForStock(stock);
                    this.sleepForRateLimit();
                } catch (Exception ex) {
                    log.warn("Failed to sync trend for stock: {}", stock.getStockCode(), ex);
                }
            }
        }

        if (this.dailyKlineEnabled) {
            this.batchSyncKlines(valid, new KlineSyncPlan(
                    KlinePeriodTypeEnum.DAILY,
                    this.dailyKlineLimit));
        }
        if (this.weeklyKlineEnabled) {
            this.batchSyncKlines(valid, new KlineSyncPlan(
                    KlinePeriodTypeEnum.WEEKLY,
                    this.weeklyKlineLimit));
        }
        if (this.monthlyKlineEnabled) {
            this.batchSyncKlines(valid, new KlineSyncPlan(
                    KlinePeriodTypeEnum.MONTHLY,
                    this.monthlyKlineLimit));
        }
    }

    private void doSyncTrendsForStock(StockConfigPO stock) {
        try {
            StockMarketDataDTO trends = this.stockMarketApi.getTrends(stock.getSecid());
            this.saveTrends(stock, trends.data());
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync trend for stock, code: {}, name: {}",
                    stock.getStockCode(),
                    stock.getStockName(),
                    ex);
        }
    }

    private void doSyncDailyKlinesForStock(StockConfigPO stock) {
        this.doSyncKlinesForStock(stock, KlinePeriodTypeEnum.DAILY, this.dailyKlineLimit);
    }

    private void batchSyncKlines(List<StockConfigPO> stocks, KlineSyncPlan plan) {
        for (StockConfigPO stock : stocks) {
            try {
                if (!this.shouldSyncKline(stock, plan.periodType())) {
                    continue;
                }
                this.doSyncKlinesForStock(stock, plan.periodType(), plan.limit());
                this.sleepForRateLimit();
            } catch (Exception ex) {
                log.warn(
                        "Failed to sync {} kline for stock: {}",
                        plan.periodType().getCode(),
                        stock.getStockCode(),
                        ex);
            }
        }
    }

    private boolean shouldSyncKline(StockConfigPO stock, KlinePeriodTypeEnum periodType) {
        return !this.stockKlineManage.hasSyncedSince(
                stock.getSecid(),
                periodType,
                DEFAULT_KLINE_ADJUST_TYPE,
                this.currentPeriodStart(periodType));
    }

    private LocalDateTime currentPeriodStart(KlinePeriodTypeEnum periodType) {
        LocalDate today = LocalDate.now(ZoneId.of(this.timezone));
        if (KlinePeriodTypeEnum.WEEKLY.equals(periodType)) {
            return today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay();
        }
        if (KlinePeriodTypeEnum.MONTHLY.equals(periodType)) {
            return today.withDayOfMonth(1).atStartOfDay();
        }
        return today.atStartOfDay();
    }

    private void doSyncKlinesForStock(StockConfigPO stock, KlinePeriodTypeEnum periodType, Integer limit) {
        StockMarketDataDTO klines = this.stockMarketApi.getKlines(
                stock.getSecid(),
                periodType,
                DEFAULT_KLINE_ADJUST_TYPE,
                limit);
        this.stockKlineManage.saveKlines(StockKlinePO.fromApiResponse(
                stock,
                klines.data(),
                periodType,
                DEFAULT_KLINE_ADJUST_TYPE));
    }

    private void saveTrends(StockConfigPO stock, JsonNode response) {
        String tencentSymbol = this.toTencentSymbol(stock.getSecid());
        JsonNode data = response.path("data").path(tencentSymbol).path("data");
        String tradeDate = data.path("date").asText();
        if (StrUtil.isBlank(tradeDate)) {
            return;
        }
        LocalDate trendDate = LocalDate.parse(tradeDate, TRADE_DATE_FORMATTER);
        String syncBatchNo = this.trendSyncBatchNo(stock, tradeDate);
        BigDecimal previousClosePrice = this.previousClosePrice(response, tencentSymbol);
        ZoneId zoneId = ZoneId.of(this.timezone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime syncedAt = now;
        Set<LocalDateTime> existingTrendTimes = new HashSet<>(
                this.stockIntradayTrendInfluxManage.listTrendTimesByBatchNoAndTrendDate(
                        stock.getStockCode(),
                        syncBatchNo,
                        trendDate));
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
                .filter(this::isTradingTrend)
                .filter(trend -> this.shouldWriteTrend(trend, existingTrendTimes, now))
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

    private boolean isTradingTrend(StockIntradayTrendPO trend) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null) {
            return false;
        }
        LocalTime minute = trendTime.toLocalTime();
        return (!minute.isBefore(MORNING_START) && !minute.isAfter(MORNING_END))
                || (!minute.isBefore(AFTERNOON_START) && !minute.isAfter(AFTERNOON_END));
    }

    private boolean shouldWriteTrend(
            StockIntradayTrendPO trend,
            Set<LocalDateTime> existingTrendTimes,
            LocalDateTime now) {
        LocalDateTime trendTime = trend.getTrendTime();
        return !existingTrendTimes.contains(trendTime) || this.isCurrentMinute(trendTime, now);
    }

    private boolean isCurrentMinute(LocalDateTime trendTime, LocalDateTime now) {
        return trendTime.getYear() == now.getYear()
                && trendTime.getDayOfYear() == now.getDayOfYear()
                && trendTime.getHour() == now.getHour()
                && trendTime.getMinute() == now.getMinute();
    }

    private BigDecimal previousClosePrice(JsonNode response, String tencentSymbol) {
        JsonNode quote = response.path("data").path(tencentSymbol).path("qt").path(tencentSymbol);
        return StockMarketJsonParser.decimal(quote.path(4).asText());
    }

    private String trendSyncBatchNo(StockConfigPO stock, String tradeDate) {
        return "%s-%s".formatted(stock.getStockCode(), tradeDate);
    }

    private String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }

    private record KlineSyncPlan(KlinePeriodTypeEnum periodType, Integer limit) {
    }
}
