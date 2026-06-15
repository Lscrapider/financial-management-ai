package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.Arrays;
import java.util.List;

final class ScreenFactory {
    private ScreenFactory() {
    }

    static List<ScreenSpec> list(ScreenSpec... values) {
        return Arrays.asList(values);
    }

    static List<BlockSpec> blocks(BlockSpec... values) {
        return Arrays.asList(values);
    }

    static BlockSpec block(String title, String type, RowSpec... rows) {
        return new BlockSpec(title, type, Arrays.asList(rows));
    }

    static RowSpec row(String label, String value, String tone) {
        return new RowSpec(label, value, tone);
    }

    static RowSpec adminRow(String label, String value, String tone) {
        return new RowSpec(label, value, tone, true);
    }

    static RowSpec actionRow(String label, String value, String tone) {
        return new RowSpec(label, value, tone, false, null, true);
    }

    static RowSpec commandRow(String label, String value, String tone, String actionKey) {
        return new RowSpec(label, value, tone, false, null, true, actionKey);
    }

    static RowSpec adminCommandRow(String label, String value, String tone, String actionKey) {
        return new RowSpec(label, value, tone, true, null, true, actionKey);
    }

    static RowSpec navRow(String label, String value, String tone, String targetScreenTitle) {
        return new RowSpec(label, value, tone, false, targetScreenTitle);
    }

    static RowSpec actionNavRow(String label, String value, String tone, String targetScreenTitle) {
        return new RowSpec(label, value, tone, false, targetScreenTitle, true);
    }

    static RowSpec adminActionNavRow(String label, String value, String tone, String targetScreenTitle) {
        return new RowSpec(label, value, tone, true, targetScreenTitle, true);
    }
}
