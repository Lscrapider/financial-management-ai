package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("bond_kline")
public class BondKlinePO {

    private Long id;
    private String bondCode;
    private String bondName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private String periodType;
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

    public static List<BondKlinePO> fromApiResponse(
            BondConfigPO bond,
            JsonNode response,
            KlinePeriodTypeEnum periodType) {
        String symbol = toTencentSymbol(bond.getSecid());
        JsonNode data = response.path("data").path(symbol);
        JsonNode lines = data.path(periodType.getTencentCode());
        if (!lines.isArray()) {
            lines = data.path("hfq" + periodType.getTencentCode());
        }
        if (!lines.isArray()) {
            lines = data.path("qfq" + periodType.getTencentCode());
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        List<BondKlinePO> klines = StreamSupport.stream(lines.spliterator(), false)
                .map(line -> fromTencentLine(bond, line, periodType, syncedAt))
                .filter(Objects::nonNull)
                .toList();
        return withMovingAverages(klines);
    }

    public static List<BondKlinePO> fromApiResponse(BondConfigPO bond, JsonNode response) {
        return fromApiResponse(bond, response, KlinePeriodTypeEnum.DAILY);
    }

    public static List<BondKlinePO> fromTushareRows(
            BondConfigPO bond,
            JsonNode rows,
            KlinePeriodTypeEnum periodType) {
        LocalDateTime syncedAt = LocalDateTime.now();
        List<BondKlinePO> dailyKlines = StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(bond, row, KlinePeriodTypeEnum.DAILY, syncedAt))
                .filter(Objects::nonNull)
                .toList();
        if (KlinePeriodTypeEnum.DAILY.equals(periodType)) {
            return withMovingAverages(dailyKlines);
        }
        return withMovingAverages(aggregatePeriodKlines(bond, dailyKlines, periodType, syncedAt));
    }

    private static BondKlinePO fromTencentLine(
            BondConfigPO bond,
            JsonNode line,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        if (!line.isArray() || line.size() < 5) {
            return null;
        }

        BondKlinePO kline = new BondKlinePO();
        kline.setBondCode(bond.getBondCode());
        kline.setBondName(bond.getBondName());
        kline.setSecid(bond.getSecid());
        kline.setMarketCode(bond.getMarketCode());
        kline.setExchangeCode(bond.getExchangeCode());
        kline.setPeriodType(periodType.getCode());
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

    private static List<BondKlinePO> aggregatePeriodKlines(
            BondConfigPO bond,
            List<BondKlinePO> dailyKlines,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        List<BondKlinePO> sorted = new ArrayList<>(dailyKlines);
        sorted.sort(Comparator.comparing(BondKlinePO::getTradeDate));
        Map<String, List<BondKlinePO>> groups = new LinkedHashMap<>();
        for (BondKlinePO kline : sorted) {
            groups.computeIfAbsent(periodKey(kline.getTradeDate(), periodType), key -> new ArrayList<>()).add(kline);
        }

        List<BondKlinePO> aggregated = new ArrayList<>();
        for (List<BondKlinePO> group : groups.values()) {
            BondKlinePO row = aggregateOnePeriod(bond, group, periodType, syncedAt, "aggregated-from-tushare-cb-daily");
            if (row != null) {
                aggregated.add(row);
            }
        }
        fillPeriodDerivedFields(aggregated);
        return aggregated;
    }

    public static BondKlinePO aggregateFromDaily(
            BondConfigPO bond,
            List<BondKlinePO> dailyKlines,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        return aggregateOnePeriod(bond, dailyKlines, periodType, syncedAt, "aggregated-from-daily");
    }

    private static BondKlinePO aggregateOnePeriod(
            BondConfigPO bond,
            List<BondKlinePO> group,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt,
            String rawResponse) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        BondKlinePO first = group.get(0);
        BondKlinePO last = group.get(group.size() - 1);
        BondKlinePO row = new BondKlinePO();
        row.setBondCode(bond.getBondCode());
        row.setBondName(bond.getBondName());
        row.setSecid(bond.getSecid());
        row.setMarketCode(bond.getMarketCode());
        row.setExchangeCode(bond.getExchangeCode());
        row.setPeriodType(periodType.getCode());
        row.setTradeDate(last.getTradeDate());
        row.setOpenPrice(first.getOpenPrice());
        row.setClosePrice(last.getClosePrice());
        row.setHighPrice(max(group));
        row.setLowPrice(min(group));
        row.setVolume(sumLong(group.stream().map(BondKlinePO::getVolume).toList()));
        row.setTurnoverAmount(sumDecimal(group.stream().map(BondKlinePO::getTurnoverAmount).toList()));
        row.setTurnoverRate(sumDecimal(group.stream().map(BondKlinePO::getTurnoverRate).toList()));
        row.setRawResponse(rawResponse);
        row.setSyncedAt(syncedAt);
        return row;
    }

    private static void fillPeriodDerivedFields(List<BondKlinePO> klines) {
        klines.sort(Comparator.comparing(BondKlinePO::getTradeDate));
        for (int index = 1; index < klines.size(); index++) {
            BondKlinePO current = klines.get(index);
            BigDecimal previousClose = klines.get(index - 1).getClosePrice();
            if (current.getClosePrice() == null || previousClose == null || previousClose.signum() == 0) {
                continue;
            }
            BigDecimal changeAmount = current.getClosePrice().subtract(previousClose);
            current.setChangeAmount(changeAmount);
            current.setChangePercent(changeAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previousClose, 4, RoundingMode.HALF_UP));
            if (current.getHighPrice() != null && current.getLowPrice() != null) {
                current.setAmplitude(current.getHighPrice()
                        .subtract(current.getLowPrice())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousClose, 4, RoundingMode.HALF_UP));
            }
        }
    }

    private static String periodKey(LocalDate tradeDate, KlinePeriodTypeEnum periodType) {
        if (KlinePeriodTypeEnum.MONTHLY.equals(periodType)) {
            return "%d-%02d".formatted(tradeDate.getYear(), tradeDate.getMonthValue());
        }
        WeekFields weekFields = WeekFields.of(Locale.CHINA);
        return "%d-%02d".formatted(
                tradeDate.get(weekFields.weekBasedYear()),
                tradeDate.get(weekFields.weekOfWeekBasedYear()));
    }

    private static BigDecimal max(List<BondKlinePO> klines) {
        return klines.stream()
                .map(BondKlinePO::getHighPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private static BigDecimal min(List<BondKlinePO> klines) {
        return klines.stream()
                .map(BondKlinePO::getLowPrice)
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

    private static BondKlinePO fromTushareRow(
            BondConfigPO bond,
            JsonNode row,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        String tradeDate = StockMarketJsonParser.text(row, "trade_date", null);
        if (tradeDate == null || tradeDate.length() != 8) {
            return null;
        }
        BondKlinePO kline = new BondKlinePO();
        kline.setBondCode(bond.getBondCode());
        kline.setBondName(bond.getBondName());
        kline.setSecid(bond.getSecid());
        kline.setMarketCode(bond.getMarketCode());
        kline.setExchangeCode(bond.getExchangeCode());
        kline.setPeriodType(periodType.getCode());
        kline.setTradeDate(LocalDate.parse(
                "%s-%s-%s".formatted(tradeDate.substring(0, 4), tradeDate.substring(4, 6), tradeDate.substring(6, 8))));
        kline.setOpenPrice(StockMarketJsonParser.decimal(row, "open"));
        kline.setClosePrice(StockMarketJsonParser.decimal(row, "close"));
        kline.setHighPrice(StockMarketJsonParser.decimal(row, "high"));
        kline.setLowPrice(StockMarketJsonParser.decimal(row, "low"));
        kline.setVolume(StockMarketJsonParser.longValue(row, "vol"));
        kline.setTurnoverAmount(StockMarketJsonParser.decimal(row, "amount"));
        kline.setChangePercent(StockMarketJsonParser.decimal(row, "pct_chg"));
        kline.setChangeAmount(StockMarketJsonParser.decimal(row, "change"));
        kline.setRawResponse(row.toString());
        kline.setSyncedAt(syncedAt);
        return kline;
    }

    private static List<BondKlinePO> withMovingAverages(List<BondKlinePO> klines) {
        if (klines.isEmpty()) {
            return klines;
        }
        List<BondKlinePO> sorted = new ArrayList<>(klines);
        sorted.sort(Comparator.comparing(BondKlinePO::getTradeDate));
        for (int index = 0; index < sorted.size(); index++) {
            BondKlinePO current = sorted.get(index);
            current.setMa5(meanClose(sorted, index, 5));
            current.setMa10(meanClose(sorted, index, 10));
            current.setMa20(meanClose(sorted, index, 20));
        }
        return sorted;
    }

    private static BigDecimal meanClose(List<BondKlinePO> klines, int endIndex, int window) {
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
