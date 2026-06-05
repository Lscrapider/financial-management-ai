package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.scrapider.finance.config.InfluxDbProperties;
import com.scrapider.finance.domain.po.BondIntradayTrendPO;
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
public class BondIntradayTrendInfluxManage {

    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);

    private final InfluxDbProperties influxDbProperties;
    private final QueryApi queryApi;
    private final WriteApiBlocking writeApiBlocking;
    private final ZoneId zoneId;

    public BondIntradayTrendInfluxManage(
            InfluxDbProperties influxDbProperties,
            QueryApi queryApi,
            WriteApiBlocking writeApiBlocking) {
        this.influxDbProperties = influxDbProperties;
        this.queryApi = queryApi;
        this.writeApiBlocking = writeApiBlocking;
        this.zoneId = ZoneId.of(influxDbProperties.getTimezone());
    }

    public void saveTrends(List<BondIntradayTrendPO> trends) {
        if (CollUtil.isEmpty(trends)) {
            return;
        }
        List<Point> points = trends.stream()
                .map(trend -> BondIntradayTrendPO.toInfluxPoint(
                        trend,
                        this.influxDbProperties.getBondMinuteMeasurement(),
                        this.zoneId))
                .toList();
        this.writeApiBlocking.writePoints(
                this.minuteBucket(),
                this.influxDbProperties.getOrg(),
                points);
    }

    public String getLatestBatchNo(String bondCode) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: -7d)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["bondCode"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "syncedAtEpoch")
                  |> sort(columns: ["_value"], desc: true)
                  |> limit(n: 1)
                """.formatted(
                escape(this.minuteBucket()),
                escape(this.influxDbProperties.getBondMinuteMeasurement()),
                escape(bondCode));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> String.valueOf(record.getValueByKey("syncBatchNo")))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    public List<BondIntradayTrendPO> listLatestTradingTrends(String bondCode) {
        List<BondIntradayTrendPO> todayTrends = this.latestTradingTrends(this.listTodayByBondCode(bondCode));
        if (CollUtil.isNotEmpty(todayTrends)) {
            return todayTrends;
        }
        String latestBatchNo = this.getLatestBatchNo(bondCode);
        if (StrUtil.isBlank(latestBatchNo)) {
            return List.of();
        }
        return this.listByBatchNo(bondCode, latestBatchNo).stream()
                .filter(this::isTradingTrend)
                .sorted(Comparator.comparing(BondIntradayTrendPO::getTrendTime))
                .toList();
    }

    public List<BondIntradayTrendPO> listTodayByBondCode(String bondCode) {
        return this.listByBondCodeAndTrendDate(bondCode, LocalDate.now(this.zoneId));
    }

    public List<BondIntradayTrendPO> listByBondCodeAndTrendDate(String bondCode, LocalDate trendDate) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["bondCode"] == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.minuteBucket()),
                this.rangeStart(trendDate),
                this.rangeEnd(trendDate),
                escape(this.influxDbProperties.getBondMinuteMeasurement()),
                escape(bondCode));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toTrend)
                .toList();
    }

    public List<LocalDateTime> listTrendTimesByBatchNoAndTrendDate(
            String bondCode,
            String syncBatchNo,
            LocalDate trendDate) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["bondCode"] == "%s")
                  |> filter(fn: (r) => r["syncBatchNo"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "closePrice")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.minuteBucket()),
                this.rangeStart(trendDate),
                this.rangeEnd(trendDate),
                escape(this.influxDbProperties.getBondMinuteMeasurement()),
                escape(bondCode),
                escape(syncBatchNo));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> BondIntradayTrendPO.fromInfluxRecord(record, this.zoneId).getTrendTime())
                .toList();
    }

    public List<BondIntradayTrendPO> listByBatchNo(String bondCode, String syncBatchNo) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: -7d)
                  |> filter(fn: (r) => r["_measurement"] == "%s")
                  |> filter(fn: (r) => r["bondCode"] == "%s")
                  |> filter(fn: (r) => r["syncBatchNo"] == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """.formatted(
                escape(this.minuteBucket()),
                escape(this.influxDbProperties.getBondMinuteMeasurement()),
                escape(bondCode),
                escape(syncBatchNo));
        List<FluxTable> tables = this.queryApi.query(flux, this.influxDbProperties.getOrg());
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(this::toTrend)
                .toList();
    }

    private BondIntradayTrendPO toTrend(FluxRecord record) {
        return BondIntradayTrendPO.fromInfluxRecord(record, this.zoneId);
    }

    private List<BondIntradayTrendPO> latestTradingTrends(List<BondIntradayTrendPO> trends) {
        Map<LocalDateTime, BondIntradayTrendPO> latestByMinute = new LinkedHashMap<>();
        trends.stream()
                .filter(this::isTradingTrend)
                .sorted(Comparator.comparing(BondIntradayTrendPO::getTrendTime))
                .forEach(trend -> latestByMinute.merge(
                        trend.getTrendTime(),
                        trend,
                        this::latestSyncedTrend));
        return latestByMinute.values().stream()
                .sorted(Comparator.comparing(BondIntradayTrendPO::getTrendTime))
                .toList();
    }

    private BondIntradayTrendPO latestSyncedTrend(
            BondIntradayTrendPO existing,
            BondIntradayTrendPO candidate) {
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

    private boolean isTradingTrend(BondIntradayTrendPO trend) {
        LocalDateTime trendTime = trend.getTrendTime();
        if (trendTime == null) {
            return false;
        }
        LocalTime minute = trendTime.toLocalTime();
        return (!minute.isBefore(MORNING_START) && !minute.isAfter(MORNING_END))
                || (!minute.isBefore(AFTERNOON_START) && !minute.isAfter(AFTERNOON_END));
    }

    private String minuteBucket() {
        return StrUtil.blankToDefault(
                this.influxDbProperties.getBondMinuteBucket(),
                "bond_intraday");
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
