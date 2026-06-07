package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("stock_kline")
public class StockKlinePO {

    private Long id;
    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private String periodType;
    private String adjustType;
    private LocalDate tradeDate;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal amplitude;
    private BigDecimal turnoverRate;
    private BigDecimal ma5;
    private BigDecimal ma10;
    private BigDecimal ma20;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<StockKlinePO> fromApiResponse(
            StockConfigPO stockConfig,
            JsonNode response,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType) {
        String symbol = toTencentSymbol(stockConfig.getSecid());
        JsonNode data = response.path("data").path(symbol);
        JsonNode lines = data.path(lineField(periodType, adjustType));
        if (!lines.isArray()) {
            lines = data.path(periodType.getTencentCode());
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        List<StockKlinePO> klines = StreamSupport.stream(lines.spliterator(), false)
                .map(line -> fromTencentLine(stockConfig, line, periodType, adjustType, syncedAt))
                .filter(Objects::nonNull)
                .toList();
        return withMovingAverages(klines);
    }

    public static List<StockKlinePO> fromApiResponse(StockConfigPO stockConfig, JsonNode response) {
        return fromApiResponse(
                stockConfig,
                response,
                KlinePeriodTypeEnum.DAILY,
                KlineAdjustTypeEnum.HFQ);
    }

    public static List<StockKlinePO> fromTushareRows(
            StockConfigPO stockConfig,
            JsonNode rows,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType) {
        return fromTushareRows(stockConfig, rows, periodType, adjustType, Map.of());
    }

    public static List<StockKlinePO> fromTushareRows(
            StockConfigPO stockConfig,
            JsonNode rows,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Map<LocalDate, BigDecimal> adjustFactors) {
        LocalDateTime syncedAt = LocalDateTime.now();
        List<StockKlinePO> klines = StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(stockConfig, row, periodType, adjustType, adjustFactors, syncedAt))
                .filter(Objects::nonNull)
                .toList();
        return withMovingAverages(klines);
    }

    private static StockKlinePO fromTencentLine(
            StockConfigPO stockConfig,
            JsonNode line,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            LocalDateTime syncedAt) {
        if (!line.isArray() || line.size() < 5) {
            return null;
        }

        StockKlinePO kline = new StockKlinePO();
        kline.setStockCode(stockConfig.getStockCode());
        kline.setStockName(stockConfig.getStockName());
        kline.setSecid(stockConfig.getSecid());
        kline.setMarketCode(stockConfig.getMarketCode());
        kline.setExchangeCode(stockConfig.getExchangeCode());
        kline.setPeriodType(periodType.getCode());
        kline.setAdjustType(adjustType.getCode());
        kline.setTradeDate(LocalDate.parse(line.path(0).asText()));
        kline.setOpenPrice(decimal(line, 1));
        kline.setClosePrice(decimal(line, 2));
        kline.setHighPrice(decimal(line, 3));
        kline.setLowPrice(decimal(line, 4));
        kline.setVolume(longValue(line, 5));
        kline.setTurnoverAmount(decimal(line, 6));
        kline.setAmplitude(decimal(line, 7));
        kline.setChangePercent(decimal(line, 8));
        kline.setChangeAmount(decimal(line, 9));
        kline.setTurnoverRate(decimal(line, 10));
        kline.setRawResponse(line.toString());
        kline.setSyncedAt(syncedAt);
        return kline;
    }

    private static StockKlinePO fromTushareRow(
            StockConfigPO stockConfig,
            JsonNode row,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Map<LocalDate, BigDecimal> adjustFactors,
            LocalDateTime syncedAt) {
        String tradeDate = StockMarketJsonParser.text(row, "trade_date", null);
        if (tradeDate == null || tradeDate.length() != 8) {
            return null;
        }
        StockKlinePO kline = new StockKlinePO();
        kline.setStockCode(stockConfig.getStockCode());
        kline.setStockName(stockConfig.getStockName());
        kline.setSecid(stockConfig.getSecid());
        kline.setMarketCode(stockConfig.getMarketCode());
        kline.setExchangeCode(stockConfig.getExchangeCode());
        kline.setPeriodType(periodType.getCode());
        kline.setAdjustType(adjustType.getCode());
        LocalDate parsedTradeDate = LocalDate.parse(
                "%s-%s-%s".formatted(tradeDate.substring(0, 4), tradeDate.substring(4, 6), tradeDate.substring(6, 8)));
        kline.setTradeDate(parsedTradeDate);
        BigDecimal factor = adjustFactor(parsedTradeDate, adjustType, adjustFactors);
        kline.setOpenPrice(adjust(StockMarketJsonParser.decimal(row, "open"), factor));
        kline.setClosePrice(adjust(StockMarketJsonParser.decimal(row, "close"), factor));
        kline.setHighPrice(adjust(StockMarketJsonParser.decimal(row, "high"), factor));
        kline.setLowPrice(adjust(StockMarketJsonParser.decimal(row, "low"), factor));
        kline.setVolume(StockMarketJsonParser.longValue(row, "vol"));
        kline.setTurnoverAmount(StockMarketJsonParser.decimal(row, "amount"));
        kline.setChangePercent(StockMarketJsonParser.decimal(row, "pct_chg"));
        kline.setChangeAmount(adjust(StockMarketJsonParser.decimal(row, "change"), factor));
        kline.setRawResponse(row.toString());
        kline.setSyncedAt(syncedAt);
        return kline;
    }

    private static BigDecimal adjustFactor(
            LocalDate tradeDate,
            KlineAdjustTypeEnum adjustType,
            Map<LocalDate, BigDecimal> adjustFactors) {
        if (KlineAdjustTypeEnum.NONE.equals(adjustType) || adjustFactors.isEmpty()) {
            return BigDecimal.ONE;
        }
        BigDecimal current = adjustFactors.get(tradeDate);
        if (current == null) {
            return BigDecimal.ONE;
        }
        if (KlineAdjustTypeEnum.HFQ.equals(adjustType)) {
            return current;
        }
        BigDecimal latest = adjustFactors.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(BigDecimal.ONE);
        if (latest.signum() == 0) {
            return BigDecimal.ONE;
        }
        return current.divide(latest, 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal adjust(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    public static StockKlinePO aggregateFromDaily(
            StockConfigPO stockConfig,
            List<StockKlinePO> dailyKlines,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            LocalDateTime syncedAt) {
        if (dailyKlines == null || dailyKlines.isEmpty()) {
            return null;
        }
        StockKlinePO first = dailyKlines.get(0);
        StockKlinePO last = dailyKlines.get(dailyKlines.size() - 1);
        StockKlinePO repaired = new StockKlinePO();
        repaired.setStockCode(stockConfig.getStockCode());
        repaired.setStockName(stockConfig.getStockName());
        repaired.setSecid(stockConfig.getSecid());
        repaired.setMarketCode(stockConfig.getMarketCode());
        repaired.setExchangeCode(stockConfig.getExchangeCode());
        repaired.setPeriodType(periodType.getCode());
        repaired.setAdjustType(adjustType.getCode());
        repaired.setTradeDate(last.getTradeDate());
        repaired.setOpenPrice(first.getOpenPrice());
        repaired.setClosePrice(last.getClosePrice());
        repaired.setHighPrice(maxHigh(dailyKlines));
        repaired.setLowPrice(minLow(dailyKlines));
        repaired.setVolume(sumLong(dailyKlines.stream().map(StockKlinePO::getVolume).toList()));
        repaired.setTurnoverAmount(sumDecimal(dailyKlines.stream().map(StockKlinePO::getTurnoverAmount).toList()));
        repaired.setTurnoverRate(sumDecimal(dailyKlines.stream().map(StockKlinePO::getTurnoverRate).toList()));
        repaired.setRawResponse("aggregated-from-daily");
        repaired.setSyncedAt(syncedAt);
        return repaired;
    }

    private static List<StockKlinePO> withMovingAverages(List<StockKlinePO> klines) {
        if (klines.isEmpty()) {
            return klines;
        }
        List<StockKlinePO> sorted = new ArrayList<>(klines);
        sorted.sort(Comparator.comparing(StockKlinePO::getTradeDate));
        for (int index = 0; index < sorted.size(); index++) {
            StockKlinePO current = sorted.get(index);
            current.setMa5(meanClose(sorted, index, 5));
            current.setMa10(meanClose(sorted, index, 10));
            current.setMa20(meanClose(sorted, index, 20));
        }
        return sorted;
    }

    private static BigDecimal meanClose(List<StockKlinePO> klines, int endIndex, int window) {
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

    private static BigDecimal maxHigh(List<StockKlinePO> klines) {
        return klines.stream()
                .map(StockKlinePO::getHighPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private static BigDecimal minLow(List<StockKlinePO> klines) {
        return klines.stream()
                .map(StockKlinePO::getLowPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private static Long sumLong(List<Long> values) {
        long total = 0L;
        boolean hasValue = false;
        for (Long value : values) {
            if (value != null) {
                total += value;
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    private static BigDecimal sumDecimal(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    private static String lineField(KlinePeriodTypeEnum periodType, KlineAdjustTypeEnum adjustType) {
        if (KlineAdjustTypeEnum.NONE.equals(adjustType)) {
            return periodType.getTencentCode();
        }
        return adjustType.getCode() + periodType.getTencentCode();
    }

    private static BigDecimal decimal(JsonNode line, int index) {
        return line.size() > index ? StockMarketJsonParser.decimal(line.path(index).asText()) : null;
    }

    private static Long longValue(JsonNode line, int index) {
        return line.size() > index ? StockMarketJsonParser.longValue(line.path(index).asText()) : null;
    }

    private static String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }
}
