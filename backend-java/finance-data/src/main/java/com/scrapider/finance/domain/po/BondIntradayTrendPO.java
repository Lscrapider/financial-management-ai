package com.scrapider.finance.domain.po;

import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.Data;

@Data
public class BondIntradayTrendPO {

    private static final DateTimeFormatter TREND_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd HHmm");

    private String syncBatchNo;
    private String bondCode;
    private String bondName;
    private String secid;
    private LocalDateTime trendTime;
    private BigDecimal closePrice;
    private BigDecimal averagePrice;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal previousClosePrice;
    private LocalDateTime syncedAt;

    public static BondIntradayTrendPO fromTrendLine(
            BondConfigPO bond,
            String tradeDate,
            String line,
            BigDecimal previousClosePrice,
            LocalDateTime syncedAt,
            String syncBatchNo) {
        String[] parts = line.split(" ");
        if (parts.length < 4) {
            return null;
        }

        BondIntradayTrendPO trend = new BondIntradayTrendPO();
        trend.setSyncBatchNo(syncBatchNo);
        trend.setBondCode(bond.getBondCode());
        trend.setBondName(bond.getBondName());
        trend.setSecid(bond.getSecid());
        trend.setTrendTime(LocalDateTime.parse(tradeDate + " " + parts[0], TREND_TIME_FORMATTER));
        trend.setClosePrice(StockMarketJsonParser.decimal(parts[1]));
        trend.setVolume(StockMarketJsonParser.longValue(parts[2]));
        trend.setTurnoverAmount(StockMarketJsonParser.decimal(parts[3]));
        trend.setAveragePrice(calculateAveragePrice(trend.getTurnoverAmount(), trend.getVolume()));
        trend.setPreviousClosePrice(previousClosePrice);
        trend.setSyncedAt(syncedAt);
        return trend;
    }

    public static Point toInfluxPoint(BondIntradayTrendPO trend, String measurement, ZoneId zoneId) {
        Point point = Point.measurement(measurement)
                .addTag("syncBatchNo", trend.getSyncBatchNo())
                .addTag("bondCode", trend.getBondCode())
                .addTag("bondName", trend.getBondName())
                .addTag("secid", trend.getSecid())
                .time(toInstant(trend.getTrendTime(), zoneId), com.influxdb.client.domain.WritePrecision.NS);

        addField(point, "closePrice", trend.getClosePrice());
        addField(point, "averagePrice", trend.getAveragePrice());
        addField(point, "turnoverAmount", trend.getTurnoverAmount());
        addField(point, "previousClosePrice", trend.getPreviousClosePrice());
        if (trend.getVolume() != null) {
            point.addField("volume", trend.getVolume());
        }
        if (trend.getSyncedAt() != null) {
            point.addField("syncedAtEpoch", toInstant(trend.getSyncedAt(), zoneId).toEpochMilli());
        }
        return point;
    }

    public static BondIntradayTrendPO fromInfluxRecord(FluxRecord record, ZoneId zoneId) {
        BondIntradayTrendPO trend = new BondIntradayTrendPO();
        trend.setSyncBatchNo(stringValue(record.getValueByKey("syncBatchNo")));
        trend.setBondCode(stringValue(record.getValueByKey("bondCode")));
        trend.setBondName(stringValue(record.getValueByKey("bondName")));
        trend.setSecid(stringValue(record.getValueByKey("secid")));
        trend.setTrendTime(toLocalDateTime(record.getTime(), zoneId));
        trend.setClosePrice(decimalValue(record.getValueByKey("closePrice")));
        trend.setAveragePrice(decimalValue(record.getValueByKey("averagePrice")));
        trend.setVolume(longValue(record.getValueByKey("volume")));
        trend.setTurnoverAmount(decimalValue(record.getValueByKey("turnoverAmount")));
        if (trend.getAveragePrice() == null) {
            trend.setAveragePrice(calculateAveragePrice(trend.getTurnoverAmount(), trend.getVolume()));
        }
        trend.setPreviousClosePrice(decimalValue(record.getValueByKey("previousClosePrice")));
        trend.setSyncedAt(toSyncedAt(record.getValueByKey("syncedAtEpoch"), zoneId));
        return trend;
    }

    private static void addField(Point point, String fieldName, BigDecimal value) {
        if (value != null) {
            point.addField(fieldName, value);
        }
    }

    private static BigDecimal decimalValue(Object value) {
        return value == null ? null : StockMarketJsonParser.decimal(String.valueOf(value));
    }

    private static BigDecimal calculateAveragePrice(BigDecimal turnoverAmount, Long volume) {
        if (turnoverAmount == null || volume == null || volume <= 0) {
            return null;
        }
        return turnoverAmount.divide(BigDecimal.valueOf(volume * 100L), 4, RoundingMode.HALF_UP);
    }

    private static Long longValue(Object value) {
        return value == null ? null : StockMarketJsonParser.longValue(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Instant toInstant(LocalDateTime dateTime, ZoneId zoneId) {
        return dateTime.atZone(zoneId).toInstant();
    }

    private static LocalDateTime toLocalDateTime(Instant instant, ZoneId zoneId) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zoneId);
    }

    private static LocalDateTime toSyncedAt(Object value, ZoneId zoneId) {
        Long epochMillis = longValue(value);
        return epochMillis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId);
    }
}
