package com.scrapider.finance.androidapp.model;

import java.util.List;

public final class BlockSpec {
    public final String title;
    public final String type;
    public final List<RowSpec> rows;

    public BlockSpec(String title, String type, List<RowSpec> rows) {
        this.title = title;
        this.type = type;
        this.rows = rows;
    }
}
