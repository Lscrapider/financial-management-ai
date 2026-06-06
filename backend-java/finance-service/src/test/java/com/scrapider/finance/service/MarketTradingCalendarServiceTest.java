package com.scrapider.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MarketTradingCalendarServiceTest {

    @Test
    void shouldSkipWeekendAndConfiguredClosedDate() {
        MarketTradingCalendarService service = new MarketTradingCalendarService("2026-10-01,2026-10-02");

        assertFalse(service.isTradingDay(LocalDate.parse("2026-06-06")));
        assertFalse(service.isTradingDay(LocalDate.parse("2026-10-01")));
        assertTrue(service.isTradingDay(LocalDate.parse("2026-06-08")));
    }
}
