package com.scrapider.finance.androidapp.workbench;

import java.util.Locale;

final class WorkbenchMovement {
    final String type;
    final String code;
    final String name;
    final double latestPrice;
    final double changePercent;

    WorkbenchMovement(String type, String code, String name, double latestPrice, double changePercent) {
        this.type = type == null ? "" : type;
        this.code = code == null ? "" : code;
        this.name = name == null || name.isEmpty() ? "未命名标的" : name;
        this.latestPrice = latestPrice;
        this.changePercent = changePercent;
    }

    String label() {
        return code.isEmpty() ? name : name + " " + code;
    }

    String value() {
        return String.format(Locale.CHINA, "%.2f  %+.2f%%", latestPrice, changePercent);
    }

    String tone() {
        if (changePercent > 0) {
            return "up";
        }
        if (changePercent < 0) {
            return "down";
        }
        return "muted";
    }
}
