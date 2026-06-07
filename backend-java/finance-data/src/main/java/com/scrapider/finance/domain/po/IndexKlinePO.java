package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("index_kline")
public class IndexKlinePO {

    private Long id;
    private String indexCode;
    private String indexName;
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

    public static List<IndexKlinePO> fromApiResponse(
            IndexConfigPO indexConfig,
            JsonNode response,
            KlinePeriodTypeEnum periodType) {
        String symbol = toTencentSymbol(indexConfig.getSecid());
        JsonNode data = response.path("data").path(symbol);
        JsonNode lines = data.path(periodType.getTencentCode());
        if (!lines.isArray()) {
            lines = data.path("hfq" + periodType.getTencentCode());
        }
        if (!lines.isArray()) {
            lines = data.path("qfq" + periodType.getTencentCode());
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        List<IndexKlinePO> klines = StreamSupport.stream(lines.spliterator(), false)
                .map(line -> fromTencentLine(indexConfig, line, periodType, syncedAt))
                .filter(Objects::nonNull)
                .toList();
        return withMovingAverages(klines);
    }

    public static List<IndexKlinePO> fromApiResponse(IndexConfigPO indexConfig, JsonNode response) {
        return fromApiResponse(indexConfig, response, KlinePeriodTypeEnum.DAILY);
    }

    public static List<IndexKlinePO> fromTushareRows(
            IndexConfigPO indexConfig,
            JsonNode rows,
            KlinePeriodTypeEnum periodType) {
        LocalDateTime syncedAt = LocalDateTime.now();
        List<IndexKlinePO> klines = StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(indexConfig, row, periodType, syncedAt))
                .filter(Objects::nonNull)
                .toList();
        return withMovingAverages(klines);
    }

    private static IndexKlinePO fromTencentLine(
            IndexConfigPO indexConfig,
            JsonNode line,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        if (!line.isArray() || line.size() < 5) {
            return null;
        }

        IndexKlinePO kline = new IndexKlinePO();
        kline.setIndexCode(indexConfig.getIndexCode());
        kline.setIndexName(indexConfig.getIndexName());
        kline.setSecid(indexConfig.getSecid());
        kline.setMarketCode(indexConfig.getMarketCode());
        kline.setExchangeCode(indexConfig.getExchangeCode());
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

    private static IndexKlinePO fromTushareRow(
            IndexConfigPO indexConfig,
            JsonNode row,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        String tradeDate = StockMarketJsonParser.text(row, "trade_date", null);
        if (tradeDate == null || tradeDate.length() != 8) {
            return null;
        }
        IndexKlinePO kline = new IndexKlinePO();
        kline.setIndexCode(indexConfig.getIndexCode());
        kline.setIndexName(indexConfig.getIndexName());
        kline.setSecid(indexConfig.getSecid());
        kline.setMarketCode(indexConfig.getMarketCode());
        kline.setExchangeCode(indexConfig.getExchangeCode());
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

    public static IndexKlinePO aggregateFromDaily(
            IndexConfigPO indexConfig,
            List<IndexKlinePO> dailyKlines,
            KlinePeriodTypeEnum periodType,
            LocalDateTime syncedAt) {
        if (dailyKlines == null || dailyKlines.isEmpty()) {
            return null;
        }
        IndexKlinePO first = dailyKlines.get(0);
        IndexKlinePO last = dailyKlines.get(dailyKlines.size() - 1);
        IndexKlinePO repaired = new IndexKlinePO();
        repaired.setIndexCode(indexConfig.getIndexCode());
        repaired.setIndexName(indexConfig.getIndexName());
        repaired.setSecid(indexConfig.getSecid());
        repaired.setMarketCode(indexConfig.getMarketCode());
        repaired.setExchangeCode(indexConfig.getExchangeCode());
        repaired.setPeriodType(periodType.getCode());
        repaired.setTradeDate(last.getTradeDate());
        repaired.setOpenPrice(first.getOpenPrice());
        repaired.setClosePrice(last.getClosePrice());
        repaired.setHighPrice(maxHigh(dailyKlines));
        repaired.setLowPrice(minLow(dailyKlines));
        repaired.setVolume(sumLong(dailyKlines.stream().map(IndexKlinePO::getVolume).toList()));
        repaired.setTurnoverAmount(sumDecimal(dailyKlines.stream().map(IndexKlinePO::getTurnoverAmount).toList()));
        repaired.setTurnoverRate(sumDecimal(dailyKlines.stream().map(IndexKlinePO::getTurnoverRate).toList()));
        repaired.setRawResponse("aggregated-from-daily");
        repaired.setSyncedAt(syncedAt);
        return repaired;
    }

    private static List<IndexKlinePO> withMovingAverages(List<IndexKlinePO> klines) {
        if (klines.isEmpty()) {
            return klines;
        }
        List<IndexKlinePO> sorted = new ArrayList<>(klines);
        sorted.sort(Comparator.comparing(IndexKlinePO::getTradeDate));
        for (int index = 0; index < sorted.size(); index++) {
            IndexKlinePO current = sorted.get(index);
            current.setMa5(meanClose(sorted, index, 5));
            current.setMa10(meanClose(sorted, index, 10));
            current.setMa20(meanClose(sorted, index, 20));
        }
        return sorted;
    }

    private static BigDecimal meanClose(List<IndexKlinePO> klines, int endIndex, int window) {
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

    private static BigDecimal maxHigh(List<IndexKlinePO> klines) {
        return klines.stream()
                .map(IndexKlinePO::getHighPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private static BigDecimal minLow(List<IndexKlinePO> klines) {
        return klines.stream()
                .map(IndexKlinePO::getLowPrice)
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
