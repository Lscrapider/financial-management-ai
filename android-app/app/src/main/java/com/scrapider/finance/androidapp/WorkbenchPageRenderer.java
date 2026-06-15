package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.workbench.WorkbenchPageSpecFactory;

import android.widget.LinearLayout;

final class WorkbenchPageRenderer {
    private final BlockRenderer blockRenderer;

    WorkbenchPageRenderer(BlockRenderer blockRenderer) {
        this.blockRenderer = blockRenderer;
    }

    boolean supports(ScreenSpec screen) {
        return screen != null && WorkbenchPageSpecFactory.SCREEN_TITLE.equals(screen.title);
    }

    void render(LinearLayout content, ScreenSpec screen) {
        content.removeAllViews();
        for (BlockSpec block : screen.blocks) {
            content.addView(blockRenderer.blockView(screen, block));
        }
    }
}
