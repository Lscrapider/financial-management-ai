package com.scrapider.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.scrapider.finance.api.TushareApi;
import com.scrapider.finance.domain.po.MarketTradingCalendarPO;
import com.scrapider.finance.manage.MarketTradingCalendarManage;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketTradingCalendarServiceTest {

    @Test
    void shouldLoadMissingCalendarFromTushareAndUseCachedResult() {
        LocalDate closedDate = LocalDate.parse("2026-06-06");
        LocalDate tradingDate = LocalDate.parse("2026-06-08");
        FakeMarketTradingCalendarManage calendarManage = new FakeMarketTradingCalendarManage();
        FakeTushareApi tushareApi = new FakeTushareApi(tushareRows(closedDate, false));
        MarketTradingCalendarService service = new MarketTradingCalendarService(calendarManage, tushareApi);
        calendarManage.calendars.put(tradingDate, calendar(tradingDate, true));

        assertFalse(service.isTradingDay(closedDate));
        assertTrue(service.isTradingDay(tradingDate));
        assertEquals(1, tushareApi.queryCount);
        assertEquals(1, calendarManage.savedBatchCount);
        assertEquals("trade_cal", tushareApi.apiName);
        assertEquals("exchange,cal_date,is_open", tushareApi.fields);
        assertEquals(MarketTradingCalendarPO.EXCHANGE_SSE, tushareApi.params.get("exchange"));
        assertEquals("20260101", tushareApi.params.get("start_date"));
        assertEquals("20261231", tushareApi.params.get("end_date"));
    }

    private static MarketTradingCalendarPO calendar(LocalDate calendarDate, boolean open) {
        MarketTradingCalendarPO calendar = new MarketTradingCalendarPO();
        calendar.setExchange(MarketTradingCalendarPO.EXCHANGE_SSE);
        calendar.setCalendarDate(calendarDate);
        calendar.setOpen(open);
        return calendar;
    }

    private static ArrayNode tushareRows(LocalDate calendarDate, boolean open) {
        ArrayNode rows = JsonNodeFactory.instance.arrayNode();
        rows.addObject()
                .put("exchange", MarketTradingCalendarPO.EXCHANGE_SSE)
                .put("cal_date", calendarDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                .put("is_open", open ? 1 : 0);
        return rows;
    }

    private static class FakeMarketTradingCalendarManage extends MarketTradingCalendarManage {
        private final Map<LocalDate, MarketTradingCalendarPO> calendars = new HashMap<>();
        private int savedBatchCount;

        @Override
        public MarketTradingCalendarPO findByExchangeAndCalendarDate(String exchange, LocalDate calendarDate) {
            return this.calendars.get(calendarDate);
        }

        @Override
        public void saveCalendars(List<MarketTradingCalendarPO> calendars) {
            calendars.forEach(calendar -> this.calendars.put(calendar.getCalendarDate(), calendar));
            this.savedBatchCount++;
        }
    }

    private static class FakeTushareApi extends TushareApi {
        private final ArrayNode rows;
        private int queryCount;
        private String apiName;
        private String fields;
        private Map<String, Object> params;

        private FakeTushareApi(ArrayNode rows) {
            super(null, null, null, null);
            this.rows = rows;
        }

        @Override
        public ArrayNode queryRows(String apiName, Map<String, Object> params, String fields) {
            this.queryCount++;
            this.apiName = apiName;
            this.params = Map.copyOf(params);
            this.fields = fields;
            return this.rows;
        }
    }
}
