package com.scrapider.finance.domain.po;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("market_trading_calendar")
public class MarketTradingCalendarPO {

    public static final String EXCHANGE_SSE = "SSE";

    private static final int OPEN_FLAG = 1;
    private static final int CLOSED_FLAG = 0;

    private Long id;
    private String exchange;
    private LocalDate calendarDate;

    @TableField("is_open")
    private Boolean open;

    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<MarketTradingCalendarPO> fromTushareRows(String exchange, JsonNode rows) {
        if (StrUtil.isBlank(exchange) || rows == null || !rows.isArray()) {
            return List.of();
        }
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(exchange, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static MarketTradingCalendarPO fromTushareRow(
            String exchange,
            JsonNode row,
            LocalDateTime syncedAt) {
        LocalDate calendarDate = parseCalendarDate(StockMarketJsonParser.text(row, "cal_date", null));
        Integer open = StockMarketJsonParser.intValue(row, "is_open");
        if (calendarDate == null || open == null || (open != OPEN_FLAG && open != CLOSED_FLAG)) {
            return null;
        }
        MarketTradingCalendarPO calendar = new MarketTradingCalendarPO();
        calendar.setExchange(StrUtil.blankToDefault(StockMarketJsonParser.text(row, "exchange", null), exchange));
        calendar.setCalendarDate(calendarDate);
        calendar.setOpen(OPEN_FLAG == open);
        calendar.setSyncedAt(syncedAt);
        calendar.setUpdatedAt(syncedAt);
        return calendar;
    }

    private static LocalDate parseCalendarDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
