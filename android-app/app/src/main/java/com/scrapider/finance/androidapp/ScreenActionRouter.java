package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.RowSpec;

final class ScreenActionRouter {
    private ScreenActionRouter() {
    }

    static String targetScreenFor(RowSpec row) {
        return row.targetScreenTitle;
    }

    static boolean isAction(RowSpec row, String actionKey) {
        return row.actionKey != null && row.actionKey.equals(actionKey);
    }
}
