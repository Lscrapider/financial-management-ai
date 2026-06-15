package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.auth.AuthPageSpecFactory;
import com.scrapider.finance.androidapp.network.ApiConfig;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class LoginPageController {
    interface Listener {
        void toggleRole();

        void submitLogin();
    }

    private final CockpitActivity activity;
    private final FormStateStore formStateStore;
    private final Listener listener;
    private TextView status;

    LoginPageController(CockpitActivity activity, FormStateStore formStateStore, Listener listener) {
        this.activity = activity;
        this.formStateStore = formStateStore;
        this.listener = listener;
    }

    View build(boolean loginAsAdmin) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(CockpitActivity.BACKGROUND);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(activity.dp(18), activity.dp(54), activity.dp(18), activity.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        content.addView(activity.label("金融研究驾驶舱", 24, CockpitActivity.FOREGROUND, Typeface.BOLD));
        TextView subtitle = activity.label("登录后进入投资工作台，后端权限决定可访问页面。", 13,
                CockpitActivity.MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, activity.dp(8), 0, activity.dp(16));
        content.addView(subtitle);

        LinearLayout card = activity.card();
        card.addView(activity.label("登录与权限", 18, CockpitActivity.FOREGROUND, Typeface.BOLD));
        TextView hint = activity.label("选择登录角色后提交账号密码。管理员入口会在后端确认角色后开放。",
                13, CockpitActivity.MUTED, Typeface.NORMAL);
        hint.setPadding(0, activity.dp(8), 0, activity.dp(12));
        card.addView(hint);

        LinearLayout roles = new LinearLayout(activity);
        roles.setOrientation(LinearLayout.HORIZONTAL);
        roles.addView(roleChip("管理员", loginAsAdmin), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        roles.addView(activity.space(8), new LinearLayout.LayoutParams(activity.dp(8), 1));
        roles.addView(roleChip("普通用户", !loginAsAdmin), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(roles);

        card.addView(field(AuthPageSpecFactory.FIELD_USERNAME, "admin", false));
        card.addView(field(AuthPageSpecFactory.FIELD_PASSWORD, "123456", true));

        Button loginButton = activity.actionButton("登录并进入工作台", false);
        loginButton.setOnClickListener(v -> listener.submitLogin());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, activity.dp(14), 0, 0);
        card.addView(loginButton, buttonParams);

        status = activity.label("后端地址：" + ApiConfig.DEFAULT_BASE_URL, 12, CockpitActivity.MUTED, Typeface.NORMAL);
        status.setPadding(0, activity.dp(12), 0, 0);
        card.addView(status);
        content.addView(card);
        return root;
    }

    void setStatus(String message, int color) {
        if (status == null || message == null || message.isEmpty()) {
            return;
        }
        status.setText(message);
        status.setTextColor(color);
    }

    private TextView roleChip(String text, boolean selected) {
        TextView chip = activity.pill(text,
                selected ? CockpitActivity.BLUE : CockpitActivity.SURFACE_MUTED,
                selected ? CockpitActivity.FOREGROUND : CockpitActivity.MUTED);
        chip.setGravity(Gravity.CENTER);
        chip.setOnClickListener(v -> listener.toggleRole());
        return chip;
    }

    private View field(String label, String fallback, boolean password) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(12), 0, 0);
        box.addView(activity.label(label, 12, CockpitActivity.MUTED, Typeface.BOLD));

        String value = formStateStore.hasValue(AuthPageSpecFactory.SCREEN_TITLE, label)
                ? formStateStore.value(AuthPageSpecFactory.SCREEN_TITLE, label)
                : fallback;
        formStateStore.update(AuthPageSpecFactory.SCREEN_TITLE, label, value);
        EditText input = activity.inputControl(value, true, password);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                formStateStore.update(AuthPageSpecFactory.SCREEN_TITLE, label, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, activity.dp(6), 0, 0);
        box.addView(input, params);
        return box;
    }
}
