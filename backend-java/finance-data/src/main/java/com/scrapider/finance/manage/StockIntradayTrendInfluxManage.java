package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.scrapider.finance.config.InfluxDbProperties;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StockIntradayTrendInfluxManage {

    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);

    private final InfluxDbProperties influxDbProperties;
    private final QueryApi queryApi;
    private final WriteApiBlocking writeApiBlocking;
    private final ZoneId zoneId;

    public StockIntradayTrendInfluxManage(
            InfluxDbProperties influxDbProperties,
            QueryApi queryApi,
            WriteApiBlocking writeApiBlocking) {
        this.influxDbProperties = influxDbProperties;
        this.queryApi = queryApi;
        this.writeApiBlocking = writeApiBlocking;
        this.zoneId = ZoneId.of(influxDbProperties.getTimezone());
    }

    public void saveTrends(List<StockIntradayTrendPO> trends) {
        if (CollUtil.isEmpty(trends)) {
            return;
        }
        List<Point> points = trends.stream()
                .map(trend -> StockIntradayTrendPO.toInfluxPoint(
                        trend,
                        this.influxDbProperties.getStockMinuteMeasurement(),
                        this.zoneId))
                .toList();
        this.writeApiBlocking.writePoints(points);
    }

    public String getLatestBatchNo(String stockCode) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: -7d)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["stockCode"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "syncedAtEpoch")
                  |> sort(columns: ["_value"], desc: true)
                  |> limit(n: 1)
                """.formatted(
                escape(this.influxDbProperties.getBucket()),
                escape(this.influxDbProperties.getStockMinuteMeasurement()),
                escape(stockCode));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> String.valueOf(record.getValueByKey("syncBatchNo")))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    public String getLatestTodayBatchNo(String stockCode) {
        return this.getLatestBatchNoByTrendDate(stockCode, LocalDate.now(this.zoneId));
    }

    public String getLatestBatchNoByTrendDate(String stockCode, LocalDate trendDate) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["stockCode"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "syncedAtEpoch")
                  |> sort(columns: ["_value"], desc: true)
                  |> limit(n: 1)
                """.formatted(
                escape(this.influxDbProperties.getBucket()),
                this.rangeStart(trendDate),
                this.rangeEnd(trendDate),
                escape(this.influxDbProperties.getStockMinuteMeasurement()),
                escape(stockCode));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> String.valueOf(record.getValueByKey("syncBatchNo")))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    public List<StockIntradayTrendPO> listByBatchNo(String stockCode, String syncBatchNo) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: -7d)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["stockCode"] == "%s")
                  |> filter(fn: (r) => r["syncBatchNo"] == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.influxDbProperties.getBucket()),
                escape(this.influxDbProperties.getStockMinuteMeasurement()),
                escape(stockCode),
                escape(syncBatchNo));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toTrend)
                .toList();
    }

    public List<StockIntradayTrendPO> listTodayByBatchNo(String stockCode, String syncBatchNo) {
        return this.listByBatchNoAndTrendDate(stockCode, syncBatchNo, LocalDate.now(this.zoneId));
    }

    public List<StockIntradayTrendPO> listLatestTradingTrends(String stockCode) {
        List<StockIntradayTrendPO> todayTrends = this.latestTradingTrends(this.listTodayByStockCode(stockCode));
        if (CollUtil.isNotEmpty(todayTrends)) {
            return todayTrends;
        }
        String latestBatchNo = this.getLatestBatchNo(stockCode);
        if (StrUtil.isBlank(latestBatchNo)) {
            return List.of();
        }
        return this.listByBatchNo(stockCode, latestBatchNo).stream()
                .filter(this::isTradingTrend)
                .sorted(Comparator.comparing(StockIntradayTrendPO::getTrendTime))
                .toList();
    }

    public List<StockIntradayTrendPO> listTodayByStockCode(String stockCode) {
        return this.listByStockCodeAndTrendDate(stockCode, LocalDate.now(this.zoneId));
    }

    public List<StockIntradayTrendPO> listByStockCodeAndTrendDate(String stockCode, LocalDate trendDate) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["stockCode"] == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.influxDbProperties.getBucket()),
                this.rangeStart(trendDate),
                this.rangeEnd(trendDate),
                escape(this.influxDbProperties.getStockMinuteMeasurement()),
                escape(stockCode));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toTrend)
                .toList();
    }

    public List<LocalDateTime> listTrendTimesByBatchNoAndTrendDate(
            String stockCode,
            String syncBatchNo,
            LocalDate trendDate) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["stockCode"] == "%s")
                  |> filter(fn: (r) => r["syncBatchNo"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "closePrice")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.influxDbProperties.getBucket()),
                this.rangeStart(trendDate),
                this.rangeEnd(trendDate),
                escape(this.influxDbProperties.getStockMinuteMeasurement()),
                escape(stockCode),
                escape(syncBatchNo));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> StockIntradayTrendPO.fromInfluxRecord(record, this.zoneId).getTrendTime())
                .toList();
    }

    public List<StockIntradayTrendPO> listByBatchNoAndTrendDate(
            String stockCode,
            String syncBatchNo,
            LocalDate trendDate) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["stockCode"] == "%s")
                  |> filter(fn: (r) => r["syncBatchNo"] == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.influxDbProperties.getBucket()),
                this.rangeStart(trendDate),
                this.rangeEnd(trendDate),
                escape(this.influxDbProperties.getStockMinuteMeasurement()),
                escape(stockCode),
                escape(syncBatchNo));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toTrend)
                .toList();
    }

    private StockIntradayTrendPO toTrend(FluxRecord record) {
        return StockIntradayTrendPO.fromInfluxRecord(record, this.zoneId);
    }

    private List<StockIntradayTrendPO> latestTradingTrends(List<StockIntradayTrendPO> trends) {
        Map<LocalDateTime, StockIntradayTrendPO> latestByMinute = new LinkedHashMap<>();
        trends.stream()
                .filter(this::isTradingTrend)
                .sorted(Comparator.comparing(StockIntradayTrendPO::getTrendTime))
                .forEach(trend -> latestByMinute.merge(
                        trend.getTrendTime(),
                        trend,
                        this::latestSyncedTrend));
        return latestByMinute.values().stream()
                .sorted(Comparator.comparing(StockIntradayTrendPO::getTrendTime))
                .toList();
    }

    private StockIntradayTrendPO latestSyncedTrend(
            StockIntradayTrendPO existing,
            StockIntradayTrendPO candidate) {
        LocalDateTime existingSyncedAt = existing.getSyncedAt();
        LocalDateTime candidateSyncedAt = candidate.getSyncedAt();
        if (existingSyncedAt == null) {
            return candidateSyncedAt == null ? existing : candidate;
        }
        if (candidateSyncedAt == null) {
            return existing;
        }
        return candidateSyncedAt.isAfter(existingSyncedAt) ? candidate : existing;
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String rangeStart(LocalDate date) {
        return this.fluxTime(date.atStartOfDay(this.zoneId).toInstant());
    }

    private String rangeEnd(LocalDate date) {
        return this.fluxTime(date.plusDays(1).atStartOfDay(this.zoneId).toInstant());
    }

    private String fluxTime(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
