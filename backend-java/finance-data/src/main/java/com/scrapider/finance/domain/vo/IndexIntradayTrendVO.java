package com.scrapider.finance.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexIntradayTrendPO;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
public class IndexIntradayTrendVO {

    private static final DateTimeFormatter TREND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd HHmm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private String indexCode;
    private String indexName;
    private String secid;
    private LocalDateTime trendTime;
    private String trendDate;
    private String trendMinute;
    private BigDecimal closePrice;
    private BigDecimal averagePrice;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal previousClosePrice;
    private LocalDateTime syncedAt;

    public static IndexIntradayTrendVO fromPO(IndexIntradayTrendPO po) {
        IndexIntradayTrendVO vo = new IndexIntradayTrendVO();
        vo.setIndexCode(po.getIndexCode());
        vo.setIndexName(po.getIndexName());
        vo.setSecid(po.getSecid());
        vo.setTrendTime(po.getTrendTime());
        vo.setTrendDate(format(po.getTrendTime(), DATE_FORMATTER));
        vo.setTrendMinute(format(po.getTrendTime(), MINUTE_FORMATTER));
        vo.setClosePrice(po.getClosePrice());
        vo.setAveragePrice(po.getAveragePrice());
        vo.setVolume(po.getVolume());
        vo.setTurnoverAmount(po.getTurnoverAmount());
        vo.setPreviousClosePrice(po.getPreviousClosePrice());
        vo.setSyncedAt(po.getSyncedAt());
        return vo;
    }

    public static List<IndexIntradayTrendVO> fromApiResponse(IndexConfigPO indexConfig, JsonNode response) {
        String symbol = toTencentSymbol(indexConfig.getSecid());
        JsonNode data = response.path("data").path(symbol).path("data");
        String tradeDate = data.path("date").asText();
        if (tradeDate.isBlank()) {
            return List.of();
        }
        BigDecimal previousClosePrice = previousClosePrice(response, symbol);
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(data.path("data").spliterator(), false)
                .map(JsonNode::asText)
                .map(line -> fromTrendLine(indexConfig, tradeDate, line, previousClosePrice, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static IndexIntradayTrendVO fromTrendLine(
            IndexConfigPO indexConfig,
            String tradeDate,
            String line,
            BigDecimal previousClosePrice,
            LocalDateTime syncedAt) {
        String[] parts = line.split(" ");
        if (parts.length < 4) {
            return null;
        }
        LocalDateTime trendTime = LocalDateTime.parse(tradeDate + " " + parts[0], TREND_TIME_FORMATTER);
        IndexIntradayTrendVO vo = new IndexIntradayTrendVO();
        vo.setIndexCode(indexConfig.getIndexCode());
        vo.setIndexName(indexConfig.getIndexName());
        vo.setSecid(indexConfig.getSecid());
        vo.setTrendTime(trendTime);
        vo.setTrendDate(trendTime.format(DATE_FORMATTER));
        vo.setTrendMinute(trendTime.format(MINUTE_FORMATTER));
        vo.setClosePrice(StockMarketJsonParser.decimal(parts[1]));
        vo.setVolume(StockMarketJsonParser.longValue(parts[2]));
        vo.setTurnoverAmount(StockMarketJsonParser.decimal(parts[3]));
        vo.setAveragePrice(calculateAveragePrice(vo.getTurnoverAmount(), vo.getVolume()));
        vo.setPreviousClosePrice(previousClosePrice);
        vo.setSyncedAt(syncedAt);
        return vo;
    }

    private static BigDecimal previousClosePrice(JsonNode response, String symbol) {
        JsonNode quote = response.path("data").path(symbol).path("qt").path(symbol);
        return StockMarketJsonParser.decimal(quote.path(4).asText());
    }

    private static BigDecimal calculateAveragePrice(BigDecimal turnoverAmount, Long volume) {
        if (turnoverAmount == null || volume == null || volume <= 0) {
            return null;
        }
        return turnoverAmount.divide(BigDecimal.valueOf(volume * 100L), 4, RoundingMode.HALF_UP);
    }

    private static String format(LocalDateTime value, DateTimeFormatter formatter) {
        return value == null ? null : value.format(formatter);
    }

    private static String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }
}
