package com.scrapider.finance.service;

import cn.hutool.core.util.StrUtil;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MarketTradingCalendarService {

    private static final String DEFAULT_CLOSED_DATES = "2026-01-01,2026-01-02,2026-01-03,"
            + "2026-02-15,2026-02-16,2026-02-17,2026-02-18,2026-02-19,2026-02-20,2026-02-21,2026-02-22,2026-02-23,"
            + "2026-04-04,2026-04-05,2026-04-06,"
            + "2026-05-01,2026-05-02,2026-05-03,2026-05-04,2026-05-05,"
            + "2026-06-19,2026-06-20,2026-06-21,"
            + "2026-09-25,2026-09-26,2026-09-27,"
            + "2026-10-01,2026-10-02,2026-10-03,2026-10-04,2026-10-05,2026-10-06,2026-10-07";

    private final Set<LocalDate> closedDates;

    public MarketTradingCalendarService(@Value("${market.trading.closed-dates:}") String closedDatesText) {
        String effectiveClosedDatesText = StrUtil.blankToDefault(closedDatesText, DEFAULT_CLOSED_DATES);
        this.closedDates = StrUtil.splitTrim(effectiveClosedDatesText, ',').stream()
                .filter(StrUtil::isNotBlank)
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isTradingDay(ZoneId zoneId) {
        return this.isTradingDay(LocalDate.now(zoneId));
    }

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return !DayOfWeek.SATURDAY.equals(dayOfWeek)
                && !DayOfWeek.SUNDAY.equals(dayOfWeek)
                && !this.closedDates.contains(date);
    }
}
