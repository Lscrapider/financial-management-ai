package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;

final class ShellController {
    interface Listener {
        void refreshCurrentScreen();

        void selectNav(String nav);
    }

    private final CockpitActivity activity;
    private final Listener listener;
    private LinearLayout bottomNav;
    private LinearLayout content;
    private TextView topTitle;
    private TextView topSubtitle;

    ShellController(CockpitActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    View buildTopBar() {
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(activity.dp(16), activity.dp(48), activity.dp(16), activity.dp(12));
        top.setBackgroundColor(CockpitActivity.BACKGROUND);

        LinearLayout titleBox = new LinearLayout(activity);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        topTitle = activity.label("金融研究驾驶舱", 22, CockpitActivity.FOREGROUND, Typeface.BOLD);
        topSubtitle = activity.label("深色原生移动端", 12, CockpitActivity.MUTED, Typeface.NORMAL);
        titleBox.addView(topTitle);
        titleBox.addView(topSubtitle);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView refresh = activity.pill("刷新", CockpitActivity.BLUE, CockpitActivity.FOREGROUND);
        refresh.setOnClickListener(v -> listener.refreshCurrentScreen());
        top.addView(refresh);
        return top;
    }

    View buildContentScroll() {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(16));
        scrollView.addView(content);
        return scrollView;
    }

    View buildBottomNav() {
        bottomNav = new LinearLayout(activity);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(10));
        bottomNav.setBackgroundColor(CockpitActivity.SURFACE);
        return bottomNav;
    }

    LinearLayout content() {
        return content;
    }

    void renderTop(ScreenSpec screen) {
        topTitle.setText(screen.title);
        topSubtitle.setText(screen.nav);
    }

    void renderBottomNav(String currentNav) {
        bottomNav.removeAllViews();
        for (String nav : Arrays.asList("工作台", "观察", "研究", "我的")) {
            boolean selected = nav.equals(currentNav);
            TextView item = activity.label(nav, 13,
                    selected ? CockpitActivity.FOREGROUND : CockpitActivity.MUTED,
                    selected ? Typeface.BOLD : Typeface.NORMAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
            item.setBackground(selected
                    ? activity.rounded(CockpitActivity.SURFACE_MUTED, activity.dp(8), CockpitActivity.BLUE)
                    : activity.rounded(CockpitActivity.SURFACE, activity.dp(8), CockpitActivity.SURFACE));
            item.setOnClickListener(v -> listener.selectNav(nav));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(activity.dp(3), 0, activity.dp(3), 0);
            bottomNav.addView(item, params);
        }
    }

    void setStatus(String message, int color) {
    }
}
