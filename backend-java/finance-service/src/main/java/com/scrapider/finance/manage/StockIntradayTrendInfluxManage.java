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
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockIntradayTrendInfluxManage {

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

    private StockIntradayTrendPO toTrend(FluxRecord record) {
        return StockIntradayTrendPO.fromInfluxRecord(record, this.zoneId);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
