package com.scrapider.finance.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scrapider.finance.api.TushareApi;
import com.scrapider.finance.domain.po.MarketTradingCalendarPO;
import com.scrapider.finance.manage.MarketTradingCalendarManage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarketTradingCalendarService {

    private static final String TUSHARE_TRADE_CAL_API = "trade_cal";
    private static final String TUSHARE_TRADE_CAL_FIELDS = "exchange,cal_date,is_open";

    private final MarketTradingCalendarManage marketTradingCalendarManage;
    private final TushareApi tushareApi;
    private final ConcurrentMap<Integer, Object> yearLocks = new ConcurrentHashMap<>();

    public MarketTradingCalendarService(
            MarketTradingCalendarManage marketTradingCalendarManage,
            TushareApi tushareApi) {
        this.marketTradingCalendarManage = marketTradingCalendarManage;
        this.tushareApi = tushareApi;
    }

    public boolean isTradingDay(ZoneId zoneId) {
        if (zoneId == null) {
            log.warn("交易日历时区为空，按非交易日处理。");
            return false;
        }
        return this.isTradingDay(LocalDate.now(zoneId));
    }

    public boolean isTradingDay(LocalDate date) {
        if (date == null) {
            log.warn("交易日历日期为空，按非交易日处理。");
            return false;
        }
        try {
            MarketTradingCalendarPO calendar = this.loadCalendar(date);
            if (calendar == null) {
                log.warn("交易日历未包含日期 {}，按非交易日处理。", date);
                return false;
            }
            return Boolean.TRUE.equals(calendar.getOpen());
        } catch (Exception ex) {
            log.warn("查询交易日历失败，按非交易日处理。日期: {}", date, ex);
            return false;
        }
    }

    private MarketTradingCalendarPO loadCalendar(LocalDate date) {
        MarketTradingCalendarPO calendar = this.marketTradingCalendarManage.findByExchangeAndCalendarDate(
                MarketTradingCalendarPO.EXCHANGE_SSE, date);
        if (calendar != null) {
            return calendar;
        }
        this.refreshYear(date);
        return this.marketTradingCalendarManage.findByExchangeAndCalendarDate(
                MarketTradingCalendarPO.EXCHANGE_SSE, date);
    }

    private void refreshYear(LocalDate missingDate) {
        Object yearLock = this.yearLocks.computeIfAbsent(missingDate.getYear(), ignored -> new Object());
        synchronized (yearLock) {
            if (this.marketTradingCalendarManage.findByExchangeAndCalendarDate(
                            MarketTradingCalendarPO.EXCHANGE_SSE, missingDate)
                    != null) {
                return;
            }
            LocalDate yearStart = missingDate.withDayOfYear(1);
            LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
            ArrayNode rows = this.tushareApi.queryRows(
                    TUSHARE_TRADE_CAL_API,
                    Map.<String, Object>of(
                            "exchange", MarketTradingCalendarPO.EXCHANGE_SSE,
                            "start_date", yearStart.format(DateTimeFormatter.BASIC_ISO_DATE),
                            "end_date", yearEnd.format(DateTimeFormatter.BASIC_ISO_DATE)),
                    TUSHARE_TRADE_CAL_FIELDS);
            List<MarketTradingCalendarPO> calendars = MarketTradingCalendarPO.fromTushareRows(
                    MarketTradingCalendarPO.EXCHANGE_SSE, rows);
            if (calendars.stream().noneMatch(calendar -> missingDate.equals(calendar.getCalendarDate()))) {
                throw new IllegalStateException("tushare trade_cal response does not contain " + missingDate);
            }
            this.marketTradingCalendarManage.saveCalendars(calendars);
        }
    }
}
