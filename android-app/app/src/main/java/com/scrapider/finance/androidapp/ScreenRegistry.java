package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.screens.ScreenData;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScreenRegistry {
    private final List<ScreenSpec> screens = new ArrayList<>();
    private final Map<String, List<ScreenSpec>> groupedScreens = new LinkedHashMap<>();

    ScreenRegistry() {
        for (ScreenSpec screen : ScreenData.create()) {
            add(screen);
        }
    }

    List<ScreenSpec> all() {
        return screens;
    }

    List<ScreenSpec> visibleForNav(String nav, boolean adminMode) {
        return visibleForNav(nav, adminMode, null);
    }

    List<ScreenSpec> visibleForNav(String nav, boolean adminMode, ScreenSpec currentScreen) {
        List<ScreenSpec> visible = new ArrayList<>();
        List<ScreenSpec> group = groupedScreens.get(nav);
        if (group == null) {
            return visible;
        }
        ScreenSpec mine = findByTitle("我的与智能研究助手");
        if ("我的".equals(nav) && mine != null) {
            visible.add(mine);
        }
        for (ScreenSpec screen : group) {
            if (screen == mine) {
                continue;
            }
            if (screen.tabVisible && (!screen.admin || adminMode)) {
                visible.add(screen);
            }
        }
        if (currentScreen != null
                && currentScreen.nav.equals(nav)
                && !currentScreen.tabVisible
                && (!currentScreen.admin || adminMode)
                && !visible.contains(currentScreen)) {
            visible.add(currentScreen);
        }
        return visible;
    }

    ScreenSpec defaultForNav(String nav, boolean adminMode, ScreenSpec fallback) {
        if ("我的".equals(nav)) {
            ScreenSpec mine = assistantScreen();
            if (mine != null) {
                return mine;
            }
        }
        List<ScreenSpec> visible = visibleForNav(nav, adminMode);
        if (!visible.isEmpty()) {
            return visible.get(0);
        }
        List<ScreenSpec> group = groupedScreens.get(nav);
        if (group != null && !group.isEmpty()) {
            return group.get(0);
        }
        if (fallback != null) {
            return fallback;
        }
        return screens.isEmpty() ? null : screens.get(0);
    }

    ScreenSpec assistantScreen() {
        return findByTitle("我的与智能研究助手");
    }

    ScreenSpec findByTitle(String title) {
        for (ScreenSpec screen : screens) {
            if (screen.title.equals(title)) {
                return screen;
            }
        }
        return null;
    }

    private void add(ScreenSpec screen) {
        screens.add(screen);
        if (!groupedScreens.containsKey(screen.nav)) {
            groupedScreens.put(screen.nav, new ArrayList<>());
        }
        groupedScreens.get(screen.nav).add(screen);
    }
}
