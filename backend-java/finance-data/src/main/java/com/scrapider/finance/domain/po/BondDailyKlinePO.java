package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("bond_daily_kline")
public class BondDailyKlinePO {

    private Long id;
    private String bondCode;
    private String bondName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
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
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<BondDailyKlinePO> fromApiResponse(BondConfigPO bond, JsonNode response) {
        String symbol = toTencentSymbol(bond.getSecid());
        JsonNode data = response.path("data").path(symbol);
        JsonNode lines = data.path("qfqday");
        if (!lines.isArray()) {
            lines = data.path("day");
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(lines.spliterator(), false)
                .map(line -> fromTencentLine(bond, line, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BondDailyKlinePO fromTencentLine(BondConfigPO bond, JsonNode line, LocalDateTime syncedAt) {
        if (!line.isArray() || line.size() < 5) {
            return null;
        }

        BondDailyKlinePO kline = new BondDailyKlinePO();
        kline.setBondCode(bond.getBondCode());
        kline.setBondName(bond.getBondName());
        kline.setSecid(bond.getSecid());
        kline.setMarketCode(bond.getMarketCode());
        kline.setExchangeCode(bond.getExchangeCode());
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
