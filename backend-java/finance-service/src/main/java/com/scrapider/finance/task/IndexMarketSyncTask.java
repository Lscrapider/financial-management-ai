package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexIntradayTrendPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexIntradayTrendInfluxManage;
import com.scrapider.finance.manage.IndexKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.service.HistoricalKlineProvider;
import com.scrapider.finance.service.MarketTradingCalendarService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
public class IndexMarketSyncTask {

    private static final DateTimeFormatter TRADE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);

    private final StockMarketApi stockMarketApi;
    private final IndexConfigManage indexConfigManage;
    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexKlineManage indexKlineManage;
    private final IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage;
    private final HistoricalKlineProvider historicalKlineProvider;
    private final MarketTradingCalendarService marketTradingCalendarService;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final ExecutorService manualSyncExecutor = Executors.newSingleThreadExecutor();

    @Value("${index.sync.enabled:false}")
    private boolean enabled;

    @Value("${index.sync.daily-kline-enabled:true}")
    private boolean dailyKlineEnabled;

    @Value("${index.sync.daily-kline-limit:250}")
    private Integer dailyKlineLimit;

    @Value("${index.sync.weekly-kline-enabled:true}")
    private boolean weeklyKlineEnabled;

    @Value("${index.sync.weekly-kline-limit:250}")
    private Integer weeklyKlineLimit;

    @Value("${index.sync.monthly-kline-enabled:true}")
    private boolean monthlyKlineEnabled;

    @Value("${index.sync.monthly-kline-limit:250}")
    private Integer monthlyKlineLimit;

    @Value("${index.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${index.sync.start-time:09:29}")
    private String startTime;

    @Value("${index.sync.end-time:16:00}")
    private String endTime;

    @Value("${index.sync.timezone:Asia/Shanghai}")
    private String timezone;

    @Value("${index.sync.trend-enabled:true}")
    private boolean trendEnabled;

    public IndexMarketSyncTask(
            StockMarketApi stockMarketApi,
            IndexConfigManage indexConfigManage,
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexKlineManage indexKlineManage,
            IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage,
            HistoricalKlineProvider historicalKlineProvider,
            MarketTradingCalendarService marketTradingCalendarService) {
        this.stockMarketApi = stockMarketApi;
        this.indexConfigManage = indexConfigManage;
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexKlineManage = indexKlineManage;
        this.indexIntradayTrendInfluxManage = indexIntradayTrendInfluxManage;
        this.historicalKlineProvider = historicalKlineProvider;
        this.marketTradingCalendarService = marketTradingCalendarService;
    }

    @Scheduled(
            initialDelayString = "${index.sync.initial-delay-ms}",
            fixedDelayString = "${index.sync.fixed-delay-ms}")
    public void syncIndexMarketData() {
        if (!this.enabled) {
            log.debug("Index market sync task is disabled.");
            return;
        }
        if (!this.marketTradingCalendarService.isTradingDay(ZoneId.of(this.timezone))) {
            log.debug("Index market sync task is outside trading day.");
            return;
        }
        if (!this.isInSyncWindow()) {
            log.debug("Index market sync task is outside sync window.");
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

    public boolean syncKlinesForIndex(String indexCode, KlinePeriodTypeEnum periodType, Integer limit) {
        IndexConfigPO index = this.indexConfigManage.getEnabledByIndexCode(indexCode);
        if (index == null || StrUtil.isBlank(index.getSecid())) {
            return false;
        }
        this.doSyncKlinesForIndex(index, periodType, limit);
//        if (KlinePeriodTypeEnum.DAILY.equals(periodType)) {
//            this.repairLatestPeriodKlinesFromDaily(List.of(index));
//        }
        return true;
    }

    public boolean syncTrendForIndex(String indexCode) {
        IndexConfigPO index = this.indexConfigManage.getEnabledByIndexCode(indexCode);
        if (index == null || StrUtil.isBlank(index.getSecid())) {
            log.warn("Cannot sync trend: index not found or no secid, code: {}", indexCode);
            return false;
        }
        try {
            this.doSyncTrendsForIndex(index);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync trend for index: {}", indexCode, ex);
            return false;
        }
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
        this.batchSyncQuotes(indices);
        log.info("Finished syncing index market data.");
    }

    private void batchSyncQuotes(List<IndexConfigPO> indices) {
        List<IndexConfigPO> valid = indices.stream()
                .filter(s -> StrUtil.isNotBlank(s.getSecid()))
                .toList();
        if (valid.isEmpty()) {
            return;
        }

        Map<String, IndexConfigPO> symbolToIndex = new LinkedHashMap<>();
        for (IndexConfigPO index : valid) {
            symbolToIndex.put(this.toTencentSymbol(index.getSecid()), index);
        }

        try {
            List<String> secids = new ArrayList<>(valid.size());
            for (IndexConfigPO index : valid) {
                secids.add(index.getSecid());
            }
            StockMarketDataDTO quote = this.stockMarketApi.getQuotes(secids);
            List<IndexQuoteSnapshotPO> snapshots = IndexQuoteSnapshotPO
                    .fromBatchApiResponse(quote.data().asText(), symbolToIndex);
            if (CollUtil.isNotEmpty(snapshots)) {
                this.indexQuoteSnapshotManage.saveQuotesBatch(snapshots);
            }
            log.info("Batch synced {} index quotes", snapshots.size());
        } catch (Exception ex) {
            log.warn("Failed to batch sync index quotes, count: {}", valid.size(), ex);
        }

        if (this.trendEnabled) {
            for (IndexConfigPO index : valid) {
                try {
                    this.doSyncTrendsForIndex(index);
                    this.sleepForRateLimit();
                } catch (Exception ex) {
                    log.warn("Failed to sync trend for index: {}", index.getIndexCode(), ex);
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
//        this.repairLatestPeriodKlinesFromDaily(valid);
    }

    private void doSyncTrendsForIndex(IndexConfigPO index) {
        try {
            StockMarketDataDTO trends = this.stockMarketApi.getTrends(index.getSecid());
            this.saveTrends(index, trends.data());
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync trend for index, code: {}, name: {}",
                    index.getIndexCode(),
                    index.getIndexName(),
                    ex);
        }
    }

    private void batchSyncKlines(List<IndexConfigPO> indices, KlineSyncPlan plan) {
        for (IndexConfigPO index : indices) {
            try {
                if (!this.shouldSyncKline(index, plan.periodType())) {
                    continue;
                }
                this.doSyncKlinesForIndex(index, plan.periodType(), plan.limit());
                this.sleepForRateLimit();
            } catch (Exception ex) {
                log.warn(
                        "Failed to sync {} kline for index: {}",
                        plan.periodType().getCode(),
                        index.getIndexCode(),
                        ex);
            }
        }
    }

    private boolean shouldSyncKline(IndexConfigPO index, KlinePeriodTypeEnum periodType) {
        return !this.indexKlineManage.hasSyncedSince(
                index.getSecid(),
                periodType,
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

    private void doSyncKlinesForIndex(
            IndexConfigPO index,
            KlinePeriodTypeEnum periodType,
            Integer limit) {
        this.indexKlineManage.saveKlines(this.historicalKlineProvider.getIndexKlines(index, periodType, limit));
    }

    private void repairLatestPeriodKlinesFromDaily(List<IndexConfigPO> indices) {
        if (!this.dailyKlineEnabled || CollUtil.isEmpty(indices)) {
            return;
        }
        for (IndexConfigPO index : indices) {
            if (this.weeklyKlineEnabled) {
                this.repairLatestPeriodKlineFromDaily(index, KlinePeriodTypeEnum.WEEKLY);
            }
            if (this.monthlyKlineEnabled) {
                this.repairLatestPeriodKlineFromDaily(index, KlinePeriodTypeEnum.MONTHLY);
            }
        }
    }

    private void repairLatestPeriodKlineFromDaily(IndexConfigPO index, KlinePeriodTypeEnum periodType) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of(this.timezone));
            LocalDate periodStart = this.currentPeriodStartDate(periodType, today);
            List<IndexKlinePO> dailyKlines = this.indexKlineManage.listKlines(
                            index.getIndexCode(),
                            index.getSecid(),
                            KlinePeriodTypeEnum.DAILY,
                            periodStart,
                            today,
                            null)
                    .stream()
                    .sorted(Comparator.comparing(IndexKlinePO::getTradeDate))
                    .toList();
            if (CollUtil.isEmpty(dailyKlines)) {
                return;
            }
            IndexKlinePO repaired = IndexKlinePO.aggregateFromDaily(
                    index,
                    dailyKlines,
                    periodType,
                    LocalDateTime.now(ZoneId.of(this.timezone)));
            this.fillIndexDerivedFields(repaired, this.previousIndexPeriodClose(index, periodType, repaired.getTradeDate()));
            this.fillIndexMovingAverages(repaired, periodType);
            this.indexKlineManage.saveKlines(List.of(repaired));
        } catch (Exception ex) {
            log.warn(
                    "Failed to repair {} kline for index: {}",
                    periodType.getCode(),
                    index.getIndexCode(),
                    ex);
        }
    }

    private void fillIndexDerivedFields(IndexKlinePO kline, BigDecimal previousClose) {
        if (kline.getClosePrice() == null || previousClose == null || previousClose.signum() == 0) {
            return;
        }
        BigDecimal changeAmount = kline.getClosePrice().subtract(previousClose);
        kline.setChangeAmount(changeAmount);
        kline.setChangePercent(changeAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClose, 4, RoundingMode.HALF_UP));
        if (kline.getHighPrice() != null && kline.getLowPrice() != null) {
            kline.setAmplitude(kline.getHighPrice()
                    .subtract(kline.getLowPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousClose, 4, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal previousIndexPeriodClose(
            IndexConfigPO index,
            KlinePeriodTypeEnum periodType,
            LocalDate tradeDate) {
        return this.indexKlineManage.listKlines(
                        index.getIndexCode(),
                        index.getSecid(),
                        periodType,
                        null,
                        tradeDate.minusDays(1),
                        1)
                .stream()
                .findFirst()
                .map(IndexKlinePO::getClosePrice)
                .orElse(null);
    }

    private void fillIndexMovingAverages(IndexKlinePO repaired, KlinePeriodTypeEnum periodType) {
        List<IndexKlinePO> rows = new ArrayList<>(this.indexKlineManage.listKlines(
                repaired.getIndexCode(),
                repaired.getSecid(),
                periodType,
                null,
                null,
                20));
        rows.removeIf(item -> repaired.getTradeDate().equals(item.getTradeDate()));
        rows.add(repaired);
        rows.sort(Comparator.comparing(IndexKlinePO::getTradeDate));
        int index = rows.indexOf(repaired);
        repaired.setMa5(meanIndexClose(rows, index, 5));
        repaired.setMa10(meanIndexClose(rows, index, 10));
        repaired.setMa20(meanIndexClose(rows, index, 20));
    }

    private LocalDate currentPeriodStartDate(KlinePeriodTypeEnum periodType, LocalDate today) {
        if (KlinePeriodTypeEnum.WEEKLY.equals(periodType)) {
            return today.minusDays(today.getDayOfWeek().getValue() - 1L);
        }
        if (KlinePeriodTypeEnum.MONTHLY.equals(periodType)) {
            return today.withDayOfMonth(1);
        }
        return today;
    }

    private static BigDecimal meanIndexClose(List<IndexKlinePO> klines, int endIndex, int window) {
        if (endIndex + 1 < window) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int itemIndex = endIndex - window + 1; itemIndex <= endIndex; itemIndex++) {
            BigDecimal closePrice = klines.get(itemIndex).getClosePrice();
            if (closePrice == null) {
                return null;
            }
            total = total.add(closePrice);
        }
        return total.divide(BigDecimal.valueOf(window), 4, RoundingMode.HALF_UP);
    }

    private void saveTrends(IndexConfigPO index, JsonNode response) {
        String tencentSymbol = this.toTencentSymbol(index.getSecid());
        JsonNode data = response.path("data").path(tencentSymbol).path("data");
        String tradeDate = data.path("date").asText();
        if (StrUtil.isBlank(tradeDate)) {
            return;
        }
        LocalDate trendDate = LocalDate.parse(tradeDate, TRADE_DATE_FORMATTER);
        String syncBatchNo = this.trendSyncBatchNo(index, tradeDate);
        BigDecimal previousClosePrice = this.previousClosePrice(response, tencentSymbol);
        ZoneId zoneId = ZoneId.of(this.timezone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime syncedAt = now;
        Set<LocalDateTime> existingTrendTimes = new HashSet<>(
                this.indexIntradayTrendInfluxManage.listTrendTimesByBatchNoAndTrendDate(
                        index.getIndexCode(),
                        syncBatchNo,
                        trendDate));
        List<IndexIntradayTrendPO> trends = StreamSupport.stream(data.path("data").spliterator(), false)
                .map(JsonNode::asText)
                .map(line -> IndexIntradayTrendPO.fromTrendLine(
                        index,
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

        this.indexIntradayTrendInfluxManage.saveTrends(trends);
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

    private boolean isNotFutureTrend(IndexIntradayTrendPO trend, LocalDateTime now) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null || trendTime.toLocalDate().isBefore(now.toLocalDate())) {
            return true;
        }
        return !trendTime.isAfter(now);
    }

    private boolean isTradingTrend(IndexIntradayTrendPO trend) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null) {
            return false;
        }
        LocalTime minute = trendTime.toLocalTime();
        return (!minute.isBefore(MORNING_START) && !minute.isAfter(MORNING_END))
                || (!minute.isBefore(AFTERNOON_START) && !minute.isAfter(AFTERNOON_END));
    }

    private boolean shouldWriteTrend(
            IndexIntradayTrendPO trend,
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

    private String trendSyncBatchNo(IndexConfigPO index, String tradeDate) {
        return "%s-%s".formatted(index.getIndexCode(), tradeDate);
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
