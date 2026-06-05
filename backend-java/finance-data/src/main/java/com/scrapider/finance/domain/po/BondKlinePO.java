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
