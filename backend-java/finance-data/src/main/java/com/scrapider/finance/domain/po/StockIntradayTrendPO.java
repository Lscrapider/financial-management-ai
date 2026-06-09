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
public class StockIntradayTrendPO {

    private static final DateTimeFormatter TREND_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd HHmm");

    private Long id;
    private String syncBatchNo;
    private String stockCode;
    private String stockName;
    private String secid;
    private LocalDateTime trendTime;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal averagePrice;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal previousClosePrice;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockIntradayTrendPO fromTrendLine(
            StockConfigPO stock,
            String tradeDate,
            String line,
            BigDecimal previousClosePrice,
            LocalDateTime syncedAt,
            String syncBatchNo) {
        String[] parts = line.split(" ");
        if (parts.length < 4) {
            return null;
        }

        StockIntradayTrendPO trend = new StockIntradayTrendPO();
        trend.setSyncBatchNo(syncBatchNo);
        trend.setStockCode(stock.getStockCode());
        trend.setStockName(stock.getStockName());
        trend.setSecid(stock.getSecid());
        trend.setTrendTime(LocalDateTime.parse(tradeDate + " " + parts[0], TREND_TIME_FORMATTER));
        trend.setClosePrice(StockMarketJsonParser.decimal(parts[1]));
        trend.setVolume(StockMarketJsonParser.longValue(parts[2]));
        trend.setTurnoverAmount(StockMarketJsonParser.decimal(parts[3]));
        trend.setAveragePrice(calculateAveragePrice(
                trend.getTurnoverAmount(),
                trend.getVolume(),
                trend.getClosePrice()));
        trend.setPreviousClosePrice(previousClosePrice);
        trend.setSyncedAt(syncedAt);
        return trend;
    }

    public static Point toInfluxPoint(
            StockIntradayTrendPO trend,
            String measurement,
            ZoneId zoneId) {
        Point point = Point.measurement(measurement)
                .addTag("syncBatchNo", trend.getSyncBatchNo())
                .addTag("stockCode", trend.getStockCode())
                .addTag("stockName", trend.getStockName())
                .addTag("secid", trend.getSecid())
                .time(toInstant(trend.getTrendTime(), zoneId), com.influxdb.client.domain.WritePrecision.NS);

        addField(point, "openPrice", trend.getOpenPrice());
        addField(point, "closePrice", trend.getClosePrice());
        addField(point, "highPrice", trend.getHighPrice());
        addField(point, "lowPrice", trend.getLowPrice());
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

    public static StockIntradayTrendPO fromInfluxRecord(FluxRecord record, ZoneId zoneId) {
        StockIntradayTrendPO trend = new StockIntradayTrendPO();
        trend.setSyncBatchNo(stringValue(record.getValueByKey("syncBatchNo")));
        trend.setStockCode(stringValue(record.getValueByKey("stockCode")));
        trend.setStockName(stringValue(record.getValueByKey("stockName")));
        trend.setSecid(stringValue(record.getValueByKey("secid")));
        trend.setTrendTime(toLocalDateTime(record.getTime(), zoneId));
        trend.setOpenPrice(decimalValue(record.getValueByKey("openPrice")));
        trend.setClosePrice(decimalValue(record.getValueByKey("closePrice")));
        trend.setHighPrice(decimalValue(record.getValueByKey("highPrice")));
        trend.setLowPrice(decimalValue(record.getValueByKey("lowPrice")));
        BigDecimal storedAveragePrice = decimalValue(record.getValueByKey("averagePrice"));
        trend.setVolume(longValue(record.getValueByKey("volume")));
        trend.setTurnoverAmount(decimalValue(record.getValueByKey("turnoverAmount")));
        BigDecimal calculatedAveragePrice = calculateAveragePrice(
                trend.getTurnoverAmount(),
                trend.getVolume(),
                trend.getClosePrice());
        trend.setAveragePrice(calculatedAveragePrice == null ? storedAveragePrice : calculatedAveragePrice);
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

    private static BigDecimal calculateAveragePrice(
            BigDecimal turnoverAmount,
            Long volume,
            BigDecimal closePrice) {
        if (turnoverAmount == null || volume == null || volume <= 0 || closePrice == null || closePrice.signum() <= 0) {
            return null;
        }
        BigDecimal direct = divideAveragePrice(turnoverAmount, volume, 1);
        BigDecimal tenTimes = divideAveragePrice(turnoverAmount, volume, 10);
        BigDecimal hundredTimes = divideAveragePrice(turnoverAmount, volume, 100);
        return closestToClosePrice(closePrice, direct, tenTimes, hundredTimes);
    }

    private static BigDecimal divideAveragePrice(BigDecimal turnoverAmount, Long volume, long unitMultiplier) {
        return turnoverAmount.divide(
                BigDecimal.valueOf(volume).multiply(BigDecimal.valueOf(unitMultiplier)),
                4,
                RoundingMode.HALF_UP);
    }

    private static BigDecimal closestToClosePrice(BigDecimal closePrice, BigDecimal... candidates) {
        BigDecimal best = null;
        BigDecimal bestDistance = null;
        for (BigDecimal candidate : candidates) {
            if (candidate == null || candidate.signum() <= 0) {
                continue;
            }
            BigDecimal distance = candidate.subtract(closePrice).abs();
            if (bestDistance == null || distance.compareTo(bestDistance) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
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
