package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.RowSpec;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

abstract class CockpitActivity extends Activity {
    protected static final int BACKGROUND = Color.rgb(20, 22, 26);
    protected static final int SURFACE = Color.rgb(28, 30, 35);
    protected static final int SURFACE_ELEVATED = Color.rgb(36, 39, 45);
    protected static final int SURFACE_MUTED = Color.rgb(46, 48, 51);
    protected static final int BORDER = Color.rgb(54, 54, 58);
    protected static final int FOREGROUND = Color.rgb(242, 242, 242);
    protected static final int MUTED = Color.rgb(174, 180, 189);
    protected static final int BLUE = Color.rgb(0, 107, 230);
    protected static final int RED = Color.rgb(220, 68, 70);
    protected static final int GREEN = Color.rgb(87, 209, 136);
    protected static final int AMBER = Color.rgb(239, 189, 72);

    protected EditText inputControl(String value, boolean editable, boolean password) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(14);
        input.setTextColor(FOREGROUND);
        input.setHintTextColor(MUTED);
        input.setSingleLine(!value.contains("。"));
        input.setEnabled(editable);
        input.setPadding(dp(10), dp(7), dp(10), dp(7));
        input.setBackground(rounded(editable ? SURFACE_ELEVATED : SURFACE_MUTED, dp(8), editable ? BORDER : SURFACE_MUTED));
        input.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        if (!editable) {
            input.setAlpha(0.82f);
        }
        return input;
    }

    protected Button actionButton(String text, boolean danger) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(FOREGROUND);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        button.setBackground(rounded(danger ? RED : BLUE, dp(8), danger ? RED : BLUE));
        return button;
    }

    protected View switchControl(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(7));
        row.setBackground(rounded(SURFACE_ELEVATED, dp(8), BORDER));
        TextView text = label(value, 14, FOREGROUND, Typeface.NORMAL);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch control = new Switch(this);
        control.setChecked(value.contains("已") || value.contains("开启"));
        control.setOnCheckedChangeListener((buttonView, isChecked) ->
                setStatus(isChecked ? "开关已开启。" : "开关已关闭。", isChecked ? GREEN : AMBER));
        row.addView(control);
        return row;
    }

    protected View sliderControl(String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(rounded(SURFACE_ELEVATED, dp(8), BORDER));
        TextView text = label(value, 13, AMBER, Typeface.BOLD);
        box.addView(text);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(parsePercent(value));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
                if (fromTouch) {
                    text.setText(progress + "%");
                    setStatus("参数已调整为 " + progress + "%。", AMBER);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(seekBar);
        return box;
    }

    protected LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(SURFACE, dp(8), BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    protected TextView chip(String text, boolean selected) {
        TextView view = label(text, 13, selected ? FOREGROUND : MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackground(rounded(selected ? BLUE : SURFACE_MUTED, dp(8), selected ? BLUE : BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(4), 0, dp(4), 0);
        view.setLayoutParams(params);
        return view;
    }

    protected TextView pill(String text, int background, int textColor) {
        TextView view = label(text, 12, textColor, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setBackground(rounded(background, dp(6), background));
        return view;
    }

    protected TextView label(String text, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(dp(2), 1.0f);
        view.setIncludeFontPadding(true);
        return view;
    }

    protected GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    protected View space(int heightDp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return space;
    }

    protected RowSpec row(String label, String value, String tone) {
        return new RowSpec(label, value, tone);
    }

    protected int colorFor(String tone) {
        if ("blue".equals(tone) || "primary".equals(tone)) {
            return BLUE;
        }
        if ("danger".equals(tone) || "up".equals(tone)) {
            return RED;
        }
        if ("success".equals(tone) || "down".equals(tone)) {
            return GREEN;
        }
        if ("amber".equals(tone)) {
            return AMBER;
        }
        if ("field".equals(tone) || "switch".equals(tone)) {
            return FOREGROUND;
        }
        return MUTED;
    }

    protected boolean isBlue(String tone) {
        return "blue".equals(tone) || "primary".equals(tone);
    }

    protected int parsePercent(String value) {
        try {
            String digits = value.replace("%", "").trim();
            return Math.max(0, Math.min(100, Integer.parseInt(digits)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected ViewGroup.LayoutParams matchParent() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    protected void setStatus(String message, int color) {
    }
}
