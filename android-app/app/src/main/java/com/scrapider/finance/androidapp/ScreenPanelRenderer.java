package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ScreenPanelRenderer {
    interface Listener {
        void openDrawer(String title, RowSpec row);

        void backToMine();
    }

    private final CockpitActivity activity;
    private final BlockRenderer blockRenderer;
    private final WorkbenchPageRenderer workbenchPageRenderer;
    private final WatchPageRenderer watchPageRenderer;
    private final Listener listener;

    ScreenPanelRenderer(
            CockpitActivity activity,
            BlockRenderer blockRenderer,
            FormStateStore formStateStore,
            RuntimeValueStore runtimeValueStore,
            Listener listener) {
        this.activity = activity;
        this.blockRenderer = blockRenderer;
        this.workbenchPageRenderer = new WorkbenchPageRenderer(blockRenderer);
        this.watchPageRenderer = new WatchPageRenderer(activity, blockRenderer, formStateStore, runtimeValueStore, listener);
        this.listener = listener;
    }

    void render(LinearLayout content, ScreenSpec screen, boolean adminMode) {
        if (workbenchPageRenderer.supports(screen)) {
            workbenchPageRenderer.render(content, screen);
            return;
        }
        if (watchPageRenderer.supports(screen)) {
            watchPageRenderer.render(content, screen, adminMode);
            return;
        }
        content.removeAllViews();
        if (screen.admin && !adminMode) {
            content.addView(permissionDeniedPanel(screen));
            content.addView(activity.space(20));
            return;
        }
        for (BlockSpec block : screen.blocks) {
            content.addView(blockRenderer.blockView(screen, block));
        }
        content.addView(activity.space(20));
    }

    private View permissionDeniedPanel(ScreenSpec screen) {
        LinearLayout card = activity.card();
        TextView heading = activity.label("403 无权限", 18, CockpitActivity.RED, Typeface.BOLD);
        card.addView(heading);
        TextView message = activity.label(screen.title + " 仅管理员可见。普通用户视图隐藏入口；若接口返回 403，页面保留说明和返回入口。",
                14, CockpitActivity.MUTED, Typeface.NORMAL);
        message.setPadding(0, activity.dp(10), 0, activity.dp(12));
        card.addView(message);
        Button back = activity.actionButton("返回我的", false);
        back.setOnClickListener(v -> listener.backToMine());
        card.addView(back);
        return card;
    }

}
