package com.scrapider.finance.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondIntradayTrendPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondIntradayTrendInfluxManage;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
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
public class BondMarketSyncTask {

    private static final DateTimeFormatter TRADE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);

    private final StockMarketApi stockMarketApi;
    private final BondConfigManage bondConfigManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondKlineManage bondKlineManage;
    private final BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final ExecutorService manualSyncExecutor = Executors.newSingleThreadExecutor();

    @Value("${bond.sync.enabled:false}")
    private boolean enabled;

    @Value("${bond.sync.daily-kline-enabled:true}")
    private boolean dailyKlineEnabled;

    @Value("${bond.sync.daily-kline-limit:250}")
    private Integer dailyKlineLimit;

    @Value("${bond.sync.weekly-kline-enabled:true}")
    private boolean weeklyKlineEnabled;

    @Value("${bond.sync.weekly-kline-limit:250}")
    private Integer weeklyKlineLimit;

    @Value("${bond.sync.monthly-kline-enabled:true}")
    private boolean monthlyKlineEnabled;

    @Value("${bond.sync.monthly-kline-limit:250}")
    private Integer monthlyKlineLimit;

    @Value("${bond.sync.request-interval-ms:1500}")
    private long requestIntervalMs;

    @Value("${bond.sync.start-time:09:29}")
    private String startTime;

    @Value("${bond.sync.end-time:16:00}")
    private String endTime;

    @Value("${bond.sync.timezone:Asia/Shanghai}")
    private String timezone;

    @Value("${bond.sync.trend-enabled:true}")
    private boolean trendEnabled;

    public BondMarketSyncTask(
            StockMarketApi stockMarketApi,
            BondConfigManage bondConfigManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondKlineManage bondKlineManage,
            BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage) {
        this.stockMarketApi = stockMarketApi;
        this.bondConfigManage = bondConfigManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondKlineManage = bondKlineManage;
        this.bondIntradayTrendInfluxManage = bondIntradayTrendInfluxManage;
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

    public boolean syncKlinesForBond(String bondCode, KlinePeriodTypeEnum periodType, Integer limit) {
        BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(bondCode);
        if (bond == null || StrUtil.isBlank(bond.getSecid())) {
            return false;
        }
        this.doSyncKlinesForBond(bond, periodType, limit);
        return true;
    }

    public boolean syncTrendForBond(String bondCode) {
        BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(bondCode);
        if (bond == null || StrUtil.isBlank(bond.getSecid())) {
            log.warn("Cannot sync trend: bond not found or no secid, code: {}", bondCode);
            return false;
        }
        try {
            this.doSyncTrendsForBond(bond);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync trend for bond: {}", bondCode, ex);
            return false;
        }
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

        if (this.trendEnabled) {
            for (BondConfigPO bond : valid) {
                try {
                    this.doSyncTrendsForBond(bond);
                    this.sleepForRateLimit();
                } catch (Exception ex) {
                    log.warn("Failed to sync trend for bond: {}", bond.getBondCode(), ex);
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

    private void doSyncTrendsForBond(BondConfigPO bond) {
        try {
            StockMarketDataDTO trends = this.stockMarketApi.getTrends(bond.getSecid());
            this.saveTrends(bond, trends.data());
        } catch (Exception ex) {
            log.warn(
                    "Failed to sync trend for bond, code: {}, name: {}",
                    bond.getBondCode(),
                    bond.getBondName(),
                    ex);
        }
    }

    private void batchSyncKlines(List<BondConfigPO> bonds, KlineSyncPlan plan) {
        for (BondConfigPO bond : bonds) {
            try {
                if (!this.shouldSyncKline(bond, plan.periodType())) {
                    continue;
                }
                this.doSyncKlinesForBond(bond, plan.periodType(), plan.limit());
                this.sleepForRateLimit();
            } catch (Exception ex) {
                log.warn(
                        "Failed to sync {} kline for bond: {}",
                        plan.periodType().getCode(),
                        bond.getBondCode(),
                        ex);
            }
        }
    }

    private boolean shouldSyncKline(BondConfigPO bond, KlinePeriodTypeEnum periodType) {
        return !this.bondKlineManage.hasSyncedSince(
                bond.getSecid(),
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

    private void doSyncKlinesForBond(
            BondConfigPO bond,
            KlinePeriodTypeEnum periodType,
            Integer limit) {
        StockMarketDataDTO klines = this.stockMarketApi.getKlines(
                bond.getSecid(),
                periodType,
                KlineAdjustTypeEnum.NONE,
                limit);
        this.bondKlineManage.saveKlines(BondKlinePO.fromApiResponse(
                bond,
                klines.data(),
                periodType));
    }

    private void saveTrends(BondConfigPO bond, JsonNode response) {
        String tencentSymbol = this.toTencentSymbol(bond.getSecid());
        JsonNode data = response.path("data").path(tencentSymbol).path("data");
        String tradeDate = data.path("date").asText();
        if (StrUtil.isBlank(tradeDate)) {
            return;
        }
        LocalDate trendDate = LocalDate.parse(tradeDate, TRADE_DATE_FORMATTER);
        String syncBatchNo = this.trendSyncBatchNo(bond, tradeDate);
        BigDecimal previousClosePrice = this.previousClosePrice(response, tencentSymbol);
        ZoneId zoneId = ZoneId.of(this.timezone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime syncedAt = now;
        Set<LocalDateTime> existingTrendTimes = new HashSet<>(
                this.bondIntradayTrendInfluxManage.listTrendTimesByBatchNoAndTrendDate(
                        bond.getBondCode(),
                        syncBatchNo,
                        trendDate));
        List<BondIntradayTrendPO> trends = StreamSupport.stream(data.path("data").spliterator(), false)
                .map(JsonNode::asText)
                .map(line -> BondIntradayTrendPO.fromTrendLine(
                        bond,
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

        this.bondIntradayTrendInfluxManage.saveTrends(trends);
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

    private boolean isNotFutureTrend(BondIntradayTrendPO trend, LocalDateTime now) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null || trendTime.toLocalDate().isBefore(now.toLocalDate())) {
            return true;
        }
        return !trendTime.isAfter(now);
    }

    private boolean isTradingTrend(BondIntradayTrendPO trend) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null) {
            return false;
        }
        LocalTime minute = trendTime.toLocalTime();
        return (!minute.isBefore(MORNING_START) && !minute.isAfter(MORNING_END))
                || (!minute.isBefore(AFTERNOON_START) && !minute.isAfter(AFTERNOON_END));
    }

    private boolean shouldWriteTrend(
            BondIntradayTrendPO trend,
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

    private String trendSyncBatchNo(BondConfigPO bond, String tradeDate) {
        return "%s-%s".formatted(bond.getBondCode(), tradeDate);
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
