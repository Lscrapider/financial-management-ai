package com.scrapider.finance.androidapp.runtime;

import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.HashMap;
import java.util.Map;

public final class RuntimeValueStore {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> tones = new HashMap<>();

    public void put(String screenTitle, String rowLabel, String value) {
        values.put(key(screenTitle, rowLabel), value == null ? "" : value);
    }

    public void put(String screenTitle, String blockTitle, String rowLabel, String value) {
        values.put(key(screenTitle, blockTitle, rowLabel), value == null ? "" : value);
    }

    public void putLabel(String screenTitle, String rowLabel, String label) {
        labels.put(key(screenTitle, rowLabel), label == null ? "" : label);
    }

    public void putLabel(String screenTitle, String blockTitle, String rowLabel, String label) {
        labels.put(key(screenTitle, blockTitle, rowLabel), label == null ? "" : label);
    }

    public void putTone(String screenTitle, String rowLabel, String tone) {
        tones.put(key(screenTitle, rowLabel), tone == null ? "" : tone);
    }

    public void putTone(String screenTitle, String blockTitle, String rowLabel, String tone) {
        tones.put(key(screenTitle, blockTitle, rowLabel), tone == null ? "" : tone);
    }

    public String valueFor(ScreenSpec screen, RowSpec row) {
        if (screen == null || row == null) {
            return row == null ? "" : row.value;
        }
        String value = values.get(key(screen.title, row.label));
        return value == null ? row.value : value;
    }

    public String valueFor(ScreenSpec screen, String blockTitle, RowSpec row) {
        if (screen == null || row == null) {
            return row == null ? "" : row.value;
        }
        String value = values.get(key(screen.title, blockTitle, row.label));
        return value == null ? valueFor(screen, row) : value;
    }

    public String labelFor(ScreenSpec screen, RowSpec row) {
        if (screen == null || row == null) {
            return row == null ? "" : row.label;
        }
        String label = labels.get(key(screen.title, row.label));
        return label == null ? row.label : label;
    }

    public String labelFor(ScreenSpec screen, String blockTitle, RowSpec row) {
        if (screen == null || row == null) {
            return row == null ? "" : row.label;
        }
        String label = labels.get(key(screen.title, blockTitle, row.label));
        return label == null ? labelFor(screen, row) : label;
    }

    public String toneFor(ScreenSpec screen, RowSpec row) {
        if (screen == null || row == null) {
            return row == null ? "" : row.tone;
        }
        String tone = tones.get(key(screen.title, row.label));
        return tone == null ? row.tone : tone;
    }

    public String toneFor(ScreenSpec screen, String blockTitle, RowSpec row) {
        if (screen == null || row == null) {
            return row == null ? "" : row.tone;
        }
        String tone = tones.get(key(screen.title, blockTitle, row.label));
        return tone == null ? toneFor(screen, row) : tone;
    }

    private String key(String screenTitle, String rowLabel) {
        return screenTitle + "|" + rowLabel;
    }

    private String key(String screenTitle, String blockTitle, String rowLabel) {
        return screenTitle + "|" + blockTitle + "|" + rowLabel;
    }
}
