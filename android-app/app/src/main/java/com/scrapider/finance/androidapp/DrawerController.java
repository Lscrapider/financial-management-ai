package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.screens.ScreenBehaviorData;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class DrawerController {
    interface ActionHandler {
        void handle(RowSpec row);
    }

    private final CockpitActivity activity;
    private final BlockRenderer blockRenderer;
    private final ActionHandler actionHandler;
    private FrameLayout overlay;
    private LinearLayout panel;

    DrawerController(CockpitActivity activity, BlockRenderer blockRenderer, ActionHandler actionHandler) {
        this.activity = activity;
        this.blockRenderer = blockRenderer;
        this.actionHandler = actionHandler;
    }

    View buildOverlay() {
        overlay = new FrameLayout(activity);
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(Color.argb(168, 0, 0, 0));
        overlay.setOnClickListener(v -> hide());

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(16));
        panel.setBackground(activity.rounded(CockpitActivity.SURFACE, activity.dp(12), CockpitActivity.BORDER));
        panel.setOnClickListener(v -> {
        });

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        panelParams.setMargins(activity.dp(10), 0, activity.dp(10), activity.dp(10));
        overlay.addView(panel, panelParams);
        overlay.setLayoutParams(activity.matchParent());
        return overlay;
    }

    void show(ScreenSpec currentScreen, String title, RowSpec row) {
        panel.removeAllViews();
        activity.setStatus("已打开详情：" + title + "。", CockpitActivity.BLUE);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = activity.label(title, 18, CockpitActivity.FOREGROUND, Typeface.BOLD);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = activity.pill("关闭", CockpitActivity.SURFACE_MUTED, CockpitActivity.MUTED);
        close.setOnClickListener(v -> hide());
        titleRow.addView(close);
        panel.addView(titleRow);

        TextView subtitle = activity.label(currentScreen.title + " · " + currentScreen.area, 12, CockpitActivity.MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, activity.dp(6), 0, activity.dp(10));
        panel.addView(subtitle);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        for (RowSpec item : ScreenBehaviorData.drawerRows(currentScreen, row)) {
            body.addView(drawerRow(item));
        }
        scrollView.addView(body);
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(activity.dp(420), activity.getResources().getDisplayMetrics().heightPixels - activity.dp(160))
        ));

        overlay.setVisibility(View.VISIBLE);
    }

    void hide() {
        if (overlay != null) {
            overlay.setVisibility(View.GONE);
        }
    }

    private View drawerRow(RowSpec row) {
        if ("nav".equals(row.tone) || "primary".equals(row.tone) || "danger".equals(row.tone)) {
            return drawerActionRow(row);
        }
        if ("field".equals(row.tone) || "readonly".equals(row.tone) || "password".equals(row.tone)) {
            return blockRenderer.formRow(row);
        }
        if ("switch".equals(row.tone)) {
            return blockRenderer.formRow(row);
        }
        if ("slider".equals(row.tone)) {
            return blockRenderer.formRow(row);
        }
        if ("progress".equals(row.tone)) {
            return blockRenderer.progressRow(new RowSpec(row.label, row.value, "amber"));
        }
        return blockRenderer.listRow(row);
    }

    private View drawerActionRow(RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        Button button = activity.actionButton(row.value, "danger".equals(row.tone));
        button.setEnabled(!"disabled".equals(row.tone));
        button.setOnClickListener(v -> {
            if ("danger".equals(row.tone)) {
                showConfirm(row);
            } else {
                actionHandler.handle(row);
            }
        });
        box.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        if ("danger".equals(row.tone)) {
            TextView warning = activity.label("高风险操作需要二次确认，当前原型只展示确认状态，不执行删除或入库。", 12, CockpitActivity.AMBER, Typeface.NORMAL);
            warning.setPadding(0, activity.dp(6), 0, 0);
            box.addView(warning);
        }
        return box;
    }

    private void showConfirm(RowSpec row) {
        panel.removeAllViews();
        activity.setStatus("需要二次确认：" + row.label + "。", CockpitActivity.RED);
        TextView heading = activity.label("二次确认", 18, CockpitActivity.FOREGROUND, Typeface.BOLD);
        panel.addView(heading);
        TextView body = activity.label(row.label + "：" + row.value, 14, CockpitActivity.MUTED, Typeface.NORMAL);
        body.setPadding(0, activity.dp(10), 0, activity.dp(12));
        panel.addView(body);
        panel.addView(blockRenderer.formRow(activity.row("确认码", "输入标的代码或任务号后才能继续", "field")));
        Button cancel = activity.actionButton("取消操作", false);
        cancel.setOnClickListener(v -> {
            activity.setStatus("已取消高风险操作。", CockpitActivity.AMBER);
            hide();
        });
        panel.addView(cancel);
        overlay.setVisibility(View.VISIBLE);
    }
}
