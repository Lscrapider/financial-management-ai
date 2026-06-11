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
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.MarketSyncJobPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondIntradayTrendInfluxManage;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.MarketSyncJobManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.provider.ConvertibleBondDataProvider;
import com.scrapider.finance.provider.HistoricalKlineProvider;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final String TARGET_TYPE = "bond";

    private final StockMarketApi stockMarketApi;
    private final BondConfigManage bondConfigManage;
    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondKlineManage bondKlineManage;
    private final BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage;
    private final HistoricalKlineProvider historicalKlineProvider;
    private final ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final MarketTradingCalendarService marketTradingCalendarService;
    private final MarketSyncJobManage marketSyncJobManage;
    private final AtomicBoolean syncing = new AtomicBoolean(false);

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

    @Value("${bond.sync.convertible-data-enabled:true}")
    private boolean convertibleDataEnabled;

    @Value("${bond.sync.convertible-daily-limit:250}")
    private Integer convertibleDailyLimit;

    public BondMarketSyncTask(
            StockMarketApi stockMarketApi,
            BondConfigManage bondConfigManage,
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondKlineManage bondKlineManage,
            BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage,
            HistoricalKlineProvider historicalKlineProvider,
            ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            MarketTradingCalendarService marketTradingCalendarService,
            MarketSyncJobManage marketSyncJobManage) {
        this.stockMarketApi = stockMarketApi;
        this.bondConfigManage = bondConfigManage;
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondKlineManage = bondKlineManage;
        this.bondIntradayTrendInfluxManage = bondIntradayTrendInfluxManage;
        this.historicalKlineProvider = historicalKlineProvider;
        this.convertibleBondDataProvider = convertibleBondDataProvider;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.marketTradingCalendarService = marketTradingCalendarService;
        this.marketSyncJobManage = marketSyncJobManage;
    }

    @Scheduled(
            initialDelayString = "${bond.sync.initial-delay-ms}",
            fixedDelayString = "${bond.sync.fixed-delay-ms}")
    public void syncBondMarketData() {
        if (!this.enabled) {
            log.debug("Bond market sync task is disabled.");
            return;
        }
        if (!this.marketTradingCalendarService.isTradingDay(ZoneId.of(this.timezone))) {
            log.debug("Bond market sync task is outside trading day.");
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
        CompletableFuture.runAsync(() -> {
            try {
                this.runSyncWithJob(MarketSyncJobPO.TRIGGER_MANUAL);
            } catch (Exception ex) {
                log.warn("Manual bond market sync failed.", ex);
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
        if (KlinePeriodTypeEnum.DAILY.equals(periodType)) {
//            this.repairLatestPeriodKlinesFromDaily(List.of(bond));
        }
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

    public boolean syncMarketDataForBond(String bondCode) {
        BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(bondCode);
        if (bond == null || StrUtil.isBlank(bond.getSecid())) {
            log.warn("Cannot sync bond market data: bond not found or no secid, code: {}", bondCode);
            return false;
        }
        try {
            this.syncQuoteForBond(bond);
            this.doSyncTrendsForBond(bond);
            this.doSyncKlinesForBond(bond, KlinePeriodTypeEnum.DAILY, this.dailyKlineLimit);
            this.doSyncKlinesForBond(bond, KlinePeriodTypeEnum.WEEKLY, this.weeklyKlineLimit);
            this.doSyncKlinesForBond(bond, KlinePeriodTypeEnum.MONTHLY, this.monthlyKlineLimit);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync market data for bond: {}", bondCode, ex);
            return false;
        }
    }

    public boolean syncConvertibleDailyDataForBond(String bondCode) {
        BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(bondCode);
        ConvertibleBondDataProvider provider = this.convertibleBondDataProvider.getIfAvailable();
        if (!this.convertibleDataEnabled || bond == null || provider == null) {
            log.warn("Cannot sync convertible bond daily data, bondCode: {}", bondCode);
            return false;
        }
        try {
            this.convertibleBondDailyValuationManage.saveValuations(
                    provider.getDailyValuations(bond, this.convertibleDailyLimit));
            return true;
        } catch (Exception ex) {
            log.warn("Failed to sync convertible bond daily data for bond: {}", bondCode, ex);
            return false;
        }
    }

    private void runSyncIfIdle() {
        if (!this.syncing.compareAndSet(false, true)) {
            log.info("Bond market sync task is already running.");
            return;
        }
        try {
            this.runSyncWithJob(MarketSyncJobPO.TRIGGER_SCHEDULED);
        } finally {
            this.syncing.set(false);
        }
    }

    private void runSyncWithJob(String triggerType) {
        MarketSyncJobPO job = this.marketSyncJobManage.startFullJob(TARGET_TYPE, triggerType);
        try {
            this.doSyncBondMarketData();
            this.marketSyncJobManage.markSuccess(job.getId());
        } catch (Exception ex) {
            this.marketSyncJobManage.markFailed(job.getId(), ex.getMessage());
            throw new IllegalStateException(ex);
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
            this.fillRealtimeConversionMetrics(snapshots);
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
//        this.repairLatestPeriodKlinesFromDaily(valid);
    }

    private void fillRealtimeConversionMetrics(List<BondQuoteSnapshotPO> snapshots) {
        if (CollUtil.isEmpty(snapshots)) {
            return;
        }
        Set<String> bondCodes = snapshots.stream()
                .map(BondQuoteSnapshotPO::getBondCode)
                .filter(StrUtil::isNotBlank)
                .collect(java.util.stream.Collectors.toSet());
        if (bondCodes.isEmpty()) {
            return;
        }

        Map<String, ConvertibleBondBasicPO> basicMap = this.convertibleBondBasicManage
                .list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConvertibleBondBasicPO>()
                        .in(ConvertibleBondBasicPO::getBondCode, bondCodes))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConvertibleBondBasicPO::getBondCode,
                        Function.identity(),
                        (left, right) -> this.latestBasic(left, right)));
        Set<String> stockCodes = basicMap.values().stream()
                .map(basic -> this.normalizeStockCode(basic.getUnderlyingStockCode()))
                .filter(StrUtil::isNotBlank)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, StockQuoteSnapshotPO> stockQuoteMap = this.stockQuoteSnapshotManage
                .listByStockCodes(stockCodes)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        StockQuoteSnapshotPO::getStockCode,
                        Function.identity(),
                        (left, right) -> left));

        snapshots.forEach(snapshot -> this.fillRealtimeConversionMetrics(snapshot, basicMap, stockQuoteMap));
    }

    private void fillRealtimeConversionMetrics(
            BondQuoteSnapshotPO snapshot,
            Map<String, ConvertibleBondBasicPO> basicMap,
            Map<String, StockQuoteSnapshotPO> stockQuoteMap) {
        ConvertibleBondBasicPO basic = basicMap.get(snapshot.getBondCode());
        if (basic == null || basic.getConversionPrice() == null || basic.getConversionPrice().signum() <= 0) {
            return;
        }
        String stockCode = this.normalizeStockCode(basic.getUnderlyingStockCode());
        StockQuoteSnapshotPO stockQuote = stockQuoteMap.get(stockCode);
        if (stockQuote == null || stockQuote.getLatestPrice() == null || snapshot.getLatestPrice() == null) {
            return;
        }
        BigDecimal conversionValue = stockQuote.getLatestPrice()
                .multiply(BigDecimal.valueOf(100))
                .divide(basic.getConversionPrice(), 6, RoundingMode.HALF_UP);
        if (conversionValue.signum() <= 0) {
            return;
        }
        BigDecimal conversionPremiumRate = snapshot.getLatestPrice()
                .divide(conversionValue, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(6, RoundingMode.HALF_UP);
        snapshot.setConversionValue(conversionValue);
        snapshot.setConversionPremiumRate(conversionPremiumRate);
    }

    private void syncQuoteForBond(BondConfigPO bond) {
        StockMarketDataDTO quote = this.stockMarketApi.getQuote(bond.getSecid());
        BondQuoteSnapshotPO snapshot = BondQuoteSnapshotPO.fromApiResponse(bond, quote.data());
        if (StrUtil.isBlank(snapshot.getBondCode()) || snapshot.getLatestPrice() == null) {
            throw new IllegalArgumentException("腾讯快照未返回有效可转债数据: " + bond.getBondCode());
        }
        this.fillRealtimeConversionMetrics(List.of(snapshot));
        this.bondQuoteSnapshotManage.saveLatest(snapshot);
    }

    private ConvertibleBondBasicPO latestBasic(ConvertibleBondBasicPO left, ConvertibleBondBasicPO right) {
        if (left.getSyncedAt() == null) {
            return right;
        }
        if (right.getSyncedAt() == null) {
            return left;
        }
        return left.getSyncedAt().isAfter(right.getSyncedAt()) ? left : right;
    }

    private String normalizeStockCode(String stockCode) {
        if (StrUtil.isBlank(stockCode)) {
            return null;
        }
        String trimmed = stockCode.trim();
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex > 0) {
            return trimmed.substring(0, dotIndex);
        }
        if ((trimmed.startsWith("SH") || trimmed.startsWith("SZ")) && trimmed.length() > 2) {
            return trimmed.substring(2);
        }
        return trimmed;
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
        this.bondKlineManage.saveKlines(this.historicalKlineProvider.getBondKlines(bond, periodType, limit));
    }

    private void repairLatestPeriodKlinesFromDaily(List<BondConfigPO> bonds) {
        if (!this.dailyKlineEnabled || CollUtil.isEmpty(bonds)) {
            return;
        }
        for (BondConfigPO bond : bonds) {
            if (this.weeklyKlineEnabled) {
                this.repairLatestPeriodKlineFromDaily(bond, KlinePeriodTypeEnum.WEEKLY);
            }
            if (this.monthlyKlineEnabled) {
                this.repairLatestPeriodKlineFromDaily(bond, KlinePeriodTypeEnum.MONTHLY);
            }
        }
    }

    private void repairLatestPeriodKlineFromDaily(BondConfigPO bond, KlinePeriodTypeEnum periodType) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of(this.timezone));
            LocalDate periodStart = this.currentPeriodStartDate(periodType, today);
            List<BondKlinePO> dailyKlines = this.bondKlineManage.listKlines(
                            bond.getBondCode(),
                            bond.getSecid(),
                            KlinePeriodTypeEnum.DAILY,
                            periodStart,
                            today,
                            null)
                    .stream()
                    .sorted(Comparator.comparing(BondKlinePO::getTradeDate))
                    .toList();
            if (CollUtil.isEmpty(dailyKlines)) {
                return;
            }
            BondKlinePO repaired = BondKlinePO.aggregateFromDaily(
                    bond,
                    dailyKlines,
                    periodType,
                    LocalDateTime.now(ZoneId.of(this.timezone)));
            this.fillBondDerivedFields(repaired, this.previousBondPeriodClose(bond, periodType, repaired.getTradeDate()));
            this.fillBondMovingAverages(repaired, periodType);
            this.bondKlineManage.saveKlines(List.of(repaired));
        } catch (Exception ex) {
            log.warn(
                    "Failed to repair {} kline for bond: {}",
                    periodType.getCode(),
                    bond.getBondCode(),
                    ex);
        }
    }

    private void fillBondDerivedFields(BondKlinePO kline, BigDecimal previousClose) {
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

    private BigDecimal previousBondPeriodClose(
            BondConfigPO bond,
            KlinePeriodTypeEnum periodType,
            LocalDate tradeDate) {
        return this.bondKlineManage.listKlines(
                        bond.getBondCode(),
                        bond.getSecid(),
                        periodType,
                        null,
                        tradeDate.minusDays(1),
                        1)
                .stream()
                .findFirst()
                .map(BondKlinePO::getClosePrice)
                .orElse(null);
    }

    private void fillBondMovingAverages(BondKlinePO repaired, KlinePeriodTypeEnum periodType) {
        List<BondKlinePO> rows = new ArrayList<>(this.bondKlineManage.listKlines(
                repaired.getBondCode(),
                repaired.getSecid(),
                periodType,
                null,
                null,
                20));
        rows.removeIf(item -> repaired.getTradeDate().equals(item.getTradeDate()));
        rows.add(repaired);
        rows.sort(Comparator.comparing(BondKlinePO::getTradeDate));
        int index = rows.indexOf(repaired);
        repaired.setMa5(meanBondClose(rows, index, 5));
        repaired.setMa10(meanBondClose(rows, index, 10));
        repaired.setMa20(meanBondClose(rows, index, 20));
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

    private static BigDecimal meanBondClose(List<BondKlinePO> klines, int endIndex, int window) {
        if (endIndex + 1 < window) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int index = endIndex - window + 1; index <= endIndex; index++) {
            BigDecimal closePrice = klines.get(index).getClosePrice();
            if (closePrice == null) {
                return null;
            }
            total = total.add(closePrice);
        }
        return total.divide(BigDecimal.valueOf(window), 4, RoundingMode.HALF_UP);
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
