package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.ArrayList;
import java.util.List;

public final class ScreenData {
    private ScreenData() {
    }

    public static List<ScreenSpec> create() {
        List<ScreenSpec> screens = new ArrayList<>();
        screens.addAll(AuthScreenData.create());
        screens.addAll(WorkbenchScreenData.create());
        screens.addAll(MarketScreenData.create());
        screens.addAll(WatchScreenData.create());
        screens.addAll(ResearchScreenData.create());
        screens.addAll(KnowledgeScreenData.create());
        screens.addAll(AdminScreenData.create());
        screens.addAll(ProfileScreenData.create());
        return screens;
    }
}
