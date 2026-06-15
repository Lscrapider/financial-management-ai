package com.scrapider.finance.androidapp.model;

import java.util.List;

public final class ScreenSpec {
    public final String id;
    public final String title;
    public final String nav;
    public final String area;
    public final String intent;
    public final boolean admin;
    public final boolean tabVisible;
    public final List<BlockSpec> blocks;

    public ScreenSpec(String id, String title, String nav, String area, String intent, boolean admin, List<BlockSpec> blocks) {
        this(id, title, nav, area, intent, admin, true, blocks);
    }

    public ScreenSpec(String id, String title, String nav, String area, String intent, boolean admin, boolean tabVisible, List<BlockSpec> blocks) {
        this.id = id;
        this.title = title;
        this.nav = nav;
        this.area = area;
        this.intent = intent;
        this.admin = admin;
        this.tabVisible = tabVisible;
        this.blocks = blocks;
    }
}
