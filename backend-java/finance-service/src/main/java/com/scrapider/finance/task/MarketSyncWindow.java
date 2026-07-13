package com.scrapider.finance.task;

import java.time.LocalTime;

/**
 * 市场同步窗口判定，保留收盘后的配置宽限时间，同时排除午间闭市。
 */
final class MarketSyncWindow {

    private MarketSyncWindow() {
    }

    static boolean isInWindow(
            LocalTime now,
            LocalTime start,
            LocalTime end,
            LocalTime middayBreakStart,
            LocalTime middayBreakEnd) {
        return !now.isBefore(start)
                && !now.isAfter(end)
                && (now.isBefore(middayBreakStart) || !now.isBefore(middayBreakEnd));
    }
}
