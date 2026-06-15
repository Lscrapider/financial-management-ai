package com.scrapider.finance.androidapp.runtime;

import java.util.Locale;

public final class RuntimeFormatters {
    private RuntimeFormatters() {
    }

    public static String price(double value) {
        if (value == 0) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.2f", value);
    }

    public static String percent(double value) {
        if (value == 0) {
            return "--";
        }
        return String.format(Locale.CHINA, "%+.2f%%", value);
    }

    public static String volume(long value) {
        if (value <= 0) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.2f 万手", value / 10000.0d);
    }

    public static String amount(double value) {
        if (value <= 0) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.2f 亿", value / 100000000.0d);
    }
}
