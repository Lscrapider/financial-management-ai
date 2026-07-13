package com.scrapider.finance.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class MarketSyncWindowTest {

    private static final LocalTime SYNC_START = LocalTime.of(9, 28);
    private static final LocalTime SYNC_END = LocalTime.of(15, 2);
    private static final LocalTime MIDDAY_BREAK_START = LocalTime.of(11, 30);
    private static final LocalTime MIDDAY_BREAK_END = LocalTime.of(13, 0);

    @Test
    void excludesMiddayBreakAndKeepsClosingGracePeriod() {
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(9, 28),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isTrue();
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(11, 29),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isTrue();
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(11, 30),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isFalse();
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(12, 0),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isFalse();
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(13, 0),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isTrue();
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(15, 2),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isTrue();
        assertThat(MarketSyncWindow.isInWindow(
                        LocalTime.of(15, 3),
                        SYNC_START,
                        SYNC_END,
                        MIDDAY_BREAK_START,
                        MIDDAY_BREAK_END))
                .isFalse();
    }
}
