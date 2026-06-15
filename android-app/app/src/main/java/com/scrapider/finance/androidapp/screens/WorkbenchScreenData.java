package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.workbench.WorkbenchPageSpecFactory;

import java.util.List;

final class WorkbenchScreenData {
    private WorkbenchScreenData() {
    }

    static List<ScreenSpec> create() {
        return WorkbenchPageSpecFactory.create();
    }
}
