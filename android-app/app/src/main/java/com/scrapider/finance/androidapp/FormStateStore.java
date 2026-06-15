package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.HashMap;
import java.util.Map;

final class FormStateStore {
    private final Map<String, String> values = new HashMap<>();

    String valueFor(ScreenSpec screen, RowSpec row) {
        String key = key(screen, row.label);
        if (!values.containsKey(key)) {
            values.put(key, initialValue(row));
        }
        return values.get(key);
    }

    void update(ScreenSpec screen, RowSpec row, String value) {
        values.put(key(screen, row.label), value == null ? "" : value);
    }

    void update(String screenTitle, String label, String value) {
        values.put(key(screenTitle, label), value == null ? "" : value);
    }

    String value(String screenTitle, String label) {
        String value = values.get(key(screenTitle, label));
        return value == null ? "" : value;
    }

    boolean hasValue(String screenTitle, String label) {
        return values.containsKey(key(screenTitle, label));
    }

    private String initialValue(RowSpec row) {
        if ("password".equals(row.tone) && row.value.contains("•")) {
            return "";
        }
        return row.value;
    }

    private String key(ScreenSpec screen, String label) {
        return key(screen == null ? "抽屉" : screen.title, label);
    }

    private String key(String screenTitle, String label) {
        return screenTitle + "|" + label;
    }
}
