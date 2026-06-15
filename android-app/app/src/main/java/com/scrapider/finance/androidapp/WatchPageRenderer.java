package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class WatchPageRenderer {
    private static final String SCREEN_TITLE = "观察风控";
    private static final String MODE_OVERVIEW = "overview";
    private static final String MODE_POOL = "pool";
    private static final String MODE_ALERTS = "alerts";

    private final CockpitActivity activity;
    private final BlockRenderer blockRenderer;
    private final FormStateStore formStateStore;
    private final RuntimeValueStore runtimeValueStore;
    private final ScreenPanelRenderer.Listener listener;
    private String mode = MODE_OVERVIEW;

    WatchPageRenderer(
            CockpitActivity activity,
            BlockRenderer blockRenderer,
            FormStateStore formStateStore,
            RuntimeValueStore runtimeValueStore,
            ScreenPanelRenderer.Listener listener) {
        this.activity = activity;
        this.blockRenderer = blockRenderer;
        this.formStateStore = formStateStore;
        this.runtimeValueStore = runtimeValueStore;
        this.listener = listener;
    }

    boolean supports(ScreenSpec screen) {
        return screen != null && SCREEN_TITLE.equals(screen.title);
    }

    void render(LinearLayout content, ScreenSpec screen, boolean adminMode) {
        content.removeAllViews();
        if (MODE_POOL.equals(mode)) {
            renderPool(content, screen, adminMode);
        } else if (MODE_ALERTS.equals(mode)) {
            renderAlerts(content, screen, adminMode);
        } else {
            renderOverview(content, screen, adminMode);
        }
        content.addView(activity.space(20));
    }

    private void renderOverview(LinearLayout content, ScreenSpec screen, boolean adminMode) {
        BlockSpec groupBlock = block(screen, "分组");
        BlockSpec itemBlock = block(screen, "持仓列表");
        BlockSpec alertBlock = block(screen, "布控提醒");

        content.addView(metricsGrid(screen, groupBlock, alertBlock, adminMode));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.addView(entryCard(
                "观察池",
                watchCountText(screen, groupBlock) + " · " + selectedGroupText(screen, groupBlock),
                primaryItemText(screen, itemBlock),
                CockpitActivity.BLUE,
                () -> switchMode(content, screen, adminMode, MODE_POOL)));
        actions.addView(entryCard(
                "布控提醒",
                alertCountText(alertBlock, adminMode) + " · " + triggerText(screen, groupBlock),
                latestAlertText(screen, alertBlock),
                CockpitActivity.AMBER,
                () -> switchMode(content, screen, adminMode, MODE_ALERTS)));
        content.addView(actions);
    }

    private void renderPool(LinearLayout content, ScreenSpec screen, boolean adminMode) {
        BlockSpec itemBlock = block(screen, "持仓列表");
        BlockSpec editBlock = block(screen, "添加标的抽屉");

        content.addView(pageHeader("观察池", content, screen, adminMode));

        addSectionTitle(content, "持仓列表");
        for (RowSpec row : visibleRows(itemBlock, adminMode)) {
            content.addView(compactDataRow(screen, itemBlock.title, row));
        }

        content.addView(editCard(screen, "添加或修改标的", editBlock,
                new String[]{"标的类型", "搜索选择", "买入价", "持仓数量", "所属分组", "备注"}));
        content.addView(actionButton("保存观察项", "saveWatchItem"));
    }

    private void renderAlerts(LinearLayout content, ScreenSpec screen, boolean adminMode) {
        BlockSpec groupBlock = block(screen, "分组");
        BlockSpec alertBlock = block(screen, "布控提醒");

        content.addView(pageHeader("布控提醒", content, screen, adminMode));
        content.addView(metricsGrid(screen, groupBlock, alertBlock, adminMode));

        addSectionTitle(content, "提醒列表");
        for (RowSpec row : visibleRows(alertBlock, adminMode)) {
            if (isAlertConfigRow(row)) {
                continue;
            }
            content.addView(compactDataRow(screen, alertBlock.title, row));
        }

        content.addView(editCard(screen, "添加布控提醒", alertBlock,
                new String[]{"提醒类型", "提醒标的", "阈值编辑", "启用状态", "邮箱通知"}));
        content.addView(actionButton("添加/保存提醒", "saveStockAlert"));
        RowSpec manualCheck = row(alertBlock, "手动检查");
        if (manualCheck != null && (!manualCheck.adminOnly || adminMode)) {
            content.addView(commandButton("手动检查提醒", manualCheck));
        }
    }

    private void switchMode(LinearLayout content, ScreenSpec screen, boolean adminMode, String nextMode) {
        mode = nextMode;
        render(content, screen, adminMode);
    }

    private View pageHeader(String title, LinearLayout content, ScreenSpec screen, boolean adminMode) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, activity.dp(12));

        TextView back = activity.chip("返回", false);
        back.setOnClickListener(v -> switchMode(content, screen, adminMode, MODE_OVERVIEW));
        row.addView(back);

        TextView heading = activity.label(title, 20, CockpitActivity.FOREGROUND, Typeface.BOLD);
        heading.setPadding(activity.dp(12), 0, 0, 0);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View metricsGrid(ScreenSpec screen, BlockSpec groupBlock, BlockSpec alertBlock, boolean adminMode) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        setBottomMargin(box, 12);

        LinearLayout first = new LinearLayout(activity);
        first.setOrientation(LinearLayout.HORIZONTAL);
        first.addView(metricCell("观察标的", watchCount(screen, groupBlock), CockpitActivity.FOREGROUND),
                weightParams(1, 0, 4));
        first.addView(metricCell("提醒数量", String.valueOf(alertItemRows(alertBlock, adminMode).size()), CockpitActivity.FOREGROUND),
                weightParams(1, 4, 0));
        box.addView(first);

        LinearLayout second = new LinearLayout(activity);
        second.setOrientation(LinearLayout.HORIZONTAL);
        second.addView(metricCell("触发提醒", triggerCount(screen, groupBlock), CockpitActivity.RED),
                weightParams(1, 0, 4));
        second.addView(metricCell("接近阈值", nearCount(screen, alertBlock, adminMode), CockpitActivity.AMBER),
                weightParams(1, 4, 0));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        secondParams.setMargins(0, activity.dp(8), 0, 0);
        box.addView(second, secondParams);
        return box;
    }

    private View metricCell(String label, String value, int color) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        cell.setBackground(activity.rounded(CockpitActivity.SURFACE, activity.dp(8), CockpitActivity.BORDER));
        cell.addView(activity.label(label, 12, CockpitActivity.MUTED, Typeface.NORMAL));
        TextView number = activity.label(value, 18, color, Typeface.BOLD);
        number.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        number.setSingleLine(true);
        number.setEllipsize(TextUtils.TruncateAt.END);
        number.setPadding(0, activity.dp(4), 0, 0);
        cell.addView(number);
        return cell;
    }

    private View entryCard(String title, String meta, String value, int accent, Runnable open) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        card.setBackground(activity.rounded(CockpitActivity.SURFACE, activity.dp(8), CockpitActivity.BORDER));
        setBottomMargin(card, 10);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(activity.label(title, 17, CockpitActivity.FOREGROUND, Typeface.BOLD));
        TextView metaView = activity.label(meta, 12, CockpitActivity.MUTED, Typeface.NORMAL);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setPadding(0, activity.dp(4), 0, 0);
        text.addView(metaView);
        TextView valueView = activity.label(value, 13, accent, Typeface.BOLD);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setPadding(0, activity.dp(6), 0, 0);
        text.addView(valueView);
        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView action = activity.pill("查看/编辑", CockpitActivity.SURFACE_MUTED, accent);
        card.addView(action);
        card.setOnClickListener(v -> open.run());
        action.setOnClickListener(v -> open.run());
        return card;
    }

    private View compactDataRow(ScreenSpec screen, String blockTitle, RowSpec row) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        item.setBackground(activity.rounded(CockpitActivity.SURFACE, activity.dp(8), CockpitActivity.BORDER));
        setBottomMargin(item, 8);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView label = activity.label(label(screen, blockTitle, row), 14, CockpitActivity.FOREGROUND, Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(label);
        TextView value = activity.label(value(screen, blockTitle, row), 12, CockpitActivity.MUTED, Typeface.NORMAL);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setPadding(0, activity.dp(4), 0, 0);
        text.addView(value);
        item.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        item.addView(activity.pill(statusLabel(blockTitle, tone(screen, blockTitle, row)), CockpitActivity.SURFACE_MUTED, activity.colorFor(tone(screen, blockTitle, row))));
        item.setOnClickListener(v -> listener.openDrawer(label(screen, blockTitle, row), row));
        return item;
    }

    private View editCard(ScreenSpec screen, String title, BlockSpec block, String[] labels) {
        LinearLayout card = activity.card();
        card.addView(activity.label(title, 16, CockpitActivity.FOREGROUND, Typeface.BOLD));
        for (String label : labels) {
            RowSpec row = row(block, label);
            if (row != null) {
                card.addView(formControl(screen, block, row));
            }
        }
        return card;
    }

    private View formControl(ScreenSpec screen, BlockSpec block, RowSpec row) {
        if ("标的类型".equals(row.label) || "提醒类型".equals(row.label)) {
            return typeSelector(screen, row);
        }
        if ("搜索选择".equals(row.label) || "提醒标的".equals(row.label)) {
            return targetSearch(screen, row);
        }
        if ("所属分组".equals(row.label)) {
            return groupSelector(screen, row);
        }
        if (!"field".equals(row.tone)) {
            return blockRenderer.formRow(screen, row);
        }
        return liveTextInput(screen, block, row);
    }

    private View liveTextInput(ScreenSpec screen, BlockSpec block, RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        box.addView(activity.label(row.label, 12, CockpitActivity.MUTED, Typeface.BOLD));
        EditText input = activity.inputControl(formOrRuntimeValue(screen, block, row), true, false);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                formStateStore.update(screen, row, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, activity.dp(5), 0, 0);
        box.addView(input, params);
        return box;
    }

    private View typeSelector(ScreenSpec screen, RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        box.addView(activity.label(row.label, 12, CockpitActivity.MUTED, Typeface.BOLD));
        TextView selected = activity.label("当前：" + formOrRuntimeValue(screen, block(screen, ""), row), 12, CockpitActivity.AMBER, Typeface.BOLD);
        selected.setPadding(0, activity.dp(6), 0, activity.dp(6));
        box.addView(selected);
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String option : new String[]{"股票", "指数", "可转债"}) {
            TextView chip = activity.chip(option, option.equals(formOrRuntimeValue(screen, block(screen, ""), row)));
            chip.setOnClickListener(v -> {
                formStateStore.update(screen, row, option);
                selected.setText("当前：" + option);
            });
            chips.addView(chip);
        }
        box.addView(chips);
        return box;
    }

    private View targetSearch(ScreenSpec screen, RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        box.addView(activity.label(row.label, 12, CockpitActivity.MUTED, Typeface.BOLD));
        EditText input = activity.inputControl(formOrRuntimeValue(screen, block(screen, ""), row), true, false);
        LinearLayout suggestions = suggestionContainer();
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                formStateStore.update(screen, row, s == null ? "" : s.toString());
                renderTargetSuggestions(screen, row, suggestions, input);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        box.addView(input, inputParams());
        renderTargetSuggestions(screen, row, suggestions, input);
        box.addView(suggestions);
        return box;
    }

    private View groupSelector(ScreenSpec screen, RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        box.addView(activity.label(row.label, 12, CockpitActivity.MUTED, Typeface.BOLD));
        EditText input = activity.inputControl(formOrRuntimeValue(screen, block(screen, ""), row), true, false);
        LinearLayout suggestions = suggestionContainer();
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                formStateStore.update(screen, row, s == null ? "" : s.toString());
                renderGroupSuggestions(screen, row, suggestions, input);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        box.addView(input, inputParams());
        renderGroupSuggestions(screen, row, suggestions, input);
        box.addView(suggestions);
        return box;
    }

    private LinearLayout suggestionContainer() {
        LinearLayout suggestions = new LinearLayout(activity);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestions.setPadding(0, activity.dp(6), 0, 0);
        return suggestions;
    }

    private LinearLayout.LayoutParams inputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, activity.dp(5), 0, 0);
        return params;
    }

    private void renderTargetSuggestions(ScreenSpec screen, RowSpec row, LinearLayout suggestions, EditText input) {
        suggestions.removeAllViews();
        String query = input.getText() == null ? "" : input.getText().toString().trim();
        List<TargetOption> options = filteredTargets(screen, selectedType(screen, row), query);
        for (TargetOption option : options) {
            TextView item = suggestionRow(option.name + " " + option.code, CockpitActivity.BLUE);
            item.setOnClickListener(v -> {
                String value = option.name + " " + option.code;
                formStateStore.update(screen, row, value);
                input.setText(value);
                input.setSelection(input.getText().length());
                suggestions.removeAllViews();
            });
            suggestions.addView(item);
        }
        if (options.isEmpty() && !query.isEmpty()) {
            suggestions.addView(suggestionRow("未匹配到标的，请输入完整名称和代码", CockpitActivity.AMBER));
        }
    }

    private void renderGroupSuggestions(ScreenSpec screen, RowSpec row, LinearLayout suggestions, EditText input) {
        suggestions.removeAllViews();
        String query = input.getText() == null ? "" : input.getText().toString().trim();
        boolean matched = false;
        for (String group : groupNames(screen)) {
            if (query.isEmpty() || group.contains(query)) {
                matched = matched || group.equals(query);
                TextView item = suggestionRow(group, CockpitActivity.BLUE);
                item.setOnClickListener(v -> {
                    formStateStore.update(screen, row, group);
                    input.setText(group);
                    input.setSelection(input.getText().length());
                    suggestions.removeAllViews();
                });
                suggestions.addView(item);
            }
        }
        if (!query.isEmpty() && !matched) {
            suggestions.addView(suggestionRow("将新建分组：" + query, CockpitActivity.AMBER));
        }
    }

    private TextView suggestionRow(String text, int color) {
        TextView row = activity.label(text, 13, color, Typeface.BOLD);
        row.setPadding(activity.dp(10), activity.dp(7), activity.dp(10), activity.dp(7));
        row.setBackground(activity.rounded(CockpitActivity.SURFACE_ELEVATED, activity.dp(8), CockpitActivity.BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, activity.dp(6));
        row.setLayoutParams(params);
        return row;
    }

    private View commandButton(String title, RowSpec row) {
        Button button = activity.actionButton(title, false);
        button.setOnClickListener(v -> listener.openDrawer(row.label, row));
        setBottomMargin(button, 10);
        return button;
    }

    private View actionButton(String text, String actionKey) {
        Button button = activity.actionButton(text, false);
        RowSpec action = new RowSpec(text, text, "blue", false, null, true, actionKey);
        button.setOnClickListener(v -> listener.openDrawer(text, action));
        setBottomMargin(button, 12);
        return button;
    }


    private void addSectionTitle(LinearLayout content, String title) {
        TextView view = activity.label(title, 16, CockpitActivity.FOREGROUND, Typeface.BOLD);
        view.setPadding(0, activity.dp(6), 0, activity.dp(8));
        content.addView(view);
    }

    private BlockSpec block(ScreenSpec screen, String title) {
        if (screen == null) {
            return new BlockSpec(title, "list", new ArrayList<>());
        }
        for (BlockSpec block : screen.blocks) {
            if (title.equals(block.title)) {
                return block;
            }
        }
        return new BlockSpec(title, "list", new ArrayList<>());
    }

    private RowSpec row(BlockSpec block, String label) {
        if (block == null) {
            return null;
        }
        for (RowSpec row : block.rows) {
            if (label.equals(row.label)) {
                return row;
            }
        }
        return null;
    }

    private RowSpec firstVisibleRow(BlockSpec block) {
        if (block == null) {
            return null;
        }
        for (RowSpec row : block.rows) {
            if (!row.adminOnly) {
                return row;
            }
        }
        return null;
    }

    private List<RowSpec> visibleRows(BlockSpec block, boolean adminMode) {
        List<RowSpec> rows = new ArrayList<>();
        if (block == null) {
            return rows;
        }
        for (RowSpec row : block.rows) {
            if (!row.adminOnly || adminMode) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<RowSpec> groupRows(BlockSpec block) {
        List<RowSpec> rows = new ArrayList<>();
        if (block == null) {
            return rows;
        }
        for (RowSpec row : block.rows) {
            if (!"触发提醒".equals(row.label)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> groupNames(ScreenSpec screen) {
        List<String> names = new ArrayList<>();
        for (RowSpec row : groupRows(block(screen, "分组"))) {
            String name = runtimeValueStore.labelFor(screen, "分组", row);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private String selectedType(ScreenSpec screen, RowSpec row) {
        String typeLabel = "提醒标的".equals(row.label)
                ? formOrRuntimeValue(screen, block(screen, ""), new RowSpec("提醒类型", "股票", "field"))
                : formOrRuntimeValue(screen, block(screen, ""), new RowSpec("标的类型", "股票", "field"));
        if (typeLabel.contains("指数")) {
            return "INDEX";
        }
        if (typeLabel.contains("债")) {
            return "BOND";
        }
        return "STOCK";
    }

    private List<TargetOption> filteredTargets(ScreenSpec screen, String targetType, String query) {
        List<TargetOption> matches = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        for (TargetOption option : targetOptions(screen)) {
            if (!option.targetType.equals(targetType)) {
                continue;
            }
            String haystack = (option.name + " " + option.code).toLowerCase();
            if (normalizedQuery.isEmpty() || haystack.contains(normalizedQuery)) {
                matches.add(option);
            }
            if (matches.size() >= 5) {
                break;
            }
        }
        return matches;
    }

    private List<TargetOption> targetOptions(ScreenSpec screen) {
        List<TargetOption> options = new ArrayList<>();
        addTargetOption(options, "STOCK", "宁德时代", "300750");
        addTargetOption(options, "STOCK", "招商银行", "600036");
        addTargetOption(options, "STOCK", "平安银行", "000001");
        addTargetOption(options, "BOND", "汇通转债", "113665");
        addTargetOption(options, "BOND", "平安转债", "113000");
        addTargetOption(options, "INDEX", "上证指数", "000001.SH");
        addTargetOption(options, "INDEX", "深证成指", "399001.SZ");
        for (RowSpec row : visibleRows(block(screen, "持仓列表"), true)) {
            addTargetOption(options, targetTypeFromTone(row.tone), runtimeValueStore.labelFor(screen, "持仓列表", row), codeFromValue(row.value));
        }
        for (RowSpec row : alertItemRows(block(screen, "布控提醒"), true)) {
            addTargetOption(options, targetTypeFromTone(row.tone), runtimeValueStore.labelFor(screen, "布控提醒", row), codeFromValue(row.value));
        }
        return options;
    }

    private void addTargetOption(List<TargetOption> options, String targetType, String name, String code) {
        if (name == null || name.trim().isEmpty() || code == null || code.trim().isEmpty()) {
            return;
        }
        for (TargetOption option : options) {
            if (option.targetType.equals(targetType) && option.code.equals(code)) {
                return;
            }
        }
        options.add(new TargetOption(targetType, name.trim(), code.trim()));
    }

    private String targetTypeFromTone(String tone) {
        return "BOND".equals(tone) || "bond".equals(tone) ? "BOND" : "STOCK";
    }

    private String codeFromValue(String value) {
        if (value == null) {
            return "";
        }
        String[] parts = value.split("\\s+");
        for (String part : parts) {
            String code = part.replaceAll("[^0-9A-Za-z.]", "");
            if (code.matches("[0-9]{6}(\\.[A-Za-z]{2})?")) {
                return code;
            }
        }
        return "";
    }

    private List<RowSpec> alertItemRows(BlockSpec block, boolean adminMode) {
        List<RowSpec> rows = new ArrayList<>();
        for (RowSpec row : visibleRows(block, adminMode)) {
            if (!isAlertConfigRow(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean isAlertConfigRow(RowSpec row) {
        return "阈值编辑".equals(row.label)
                || "提醒类型".equals(row.label)
                || "提醒标的".equals(row.label)
                || "启用状态".equals(row.label)
                || "最近提醒".equals(row.label)
                || "删除提醒".equals(row.label)
                || "邮箱通知".equals(row.label)
                || "手动检查".equals(row.label);
    }

    private String label(ScreenSpec screen, String blockTitle, RowSpec row) {
        return runtimeValueStore.labelFor(screen, blockTitle, row);
    }

    private String value(ScreenSpec screen, String blockTitle, RowSpec row) {
        return runtimeValueStore.valueFor(screen, blockTitle, row);
    }

    private String tone(ScreenSpec screen, String blockTitle, RowSpec row) {
        return runtimeValueStore.toneFor(screen, blockTitle, row);
    }

    private String valueOf(BlockSpec block, String label) {
        RowSpec row = row(block, label);
        return row == null ? "" : row.value;
    }

    private String valueOf(ScreenSpec screen, BlockSpec block, String label) {
        RowSpec row = row(block, label);
        return row == null ? "" : runtimeValueStore.valueFor(screen, block.title, row);
    }

    private String formOrRuntimeValue(ScreenSpec screen, BlockSpec block, RowSpec row) {
        String screenTitle = screen == null ? "抽屉" : screen.title;
        if (formStateStore.hasValue(screenTitle, row.label)) {
            return emptyFallback(formStateStore.value(screenTitle, row.label));
        }
        return emptyFallback(runtimeValueStore.valueFor(screen, block.title, row));
    }

    private String watchCountText(ScreenSpec screen, BlockSpec groupBlock) {
        return watchCount(screen, groupBlock) + " 个标的";
    }

    private String watchCount(ScreenSpec screen, BlockSpec groupBlock) {
        int count = 0;
        for (RowSpec row : groupRows(groupBlock)) {
            count += parseInt(runtimeValueStore.valueFor(screen, groupBlock.title, row));
        }
        return count == 0 ? "0" : String.valueOf(count);
    }

    private String selectedGroupText(ScreenSpec screen, BlockSpec groupBlock) {
        List<RowSpec> rows = groupRows(groupBlock);
        if (rows.isEmpty()) {
            return "未分组";
        }
        return runtimeValueStore.labelFor(screen, groupBlock.title, rows.get(0));
    }

    private String primaryItemText(ScreenSpec screen, BlockSpec itemBlock) {
        RowSpec row = firstVisibleRow(itemBlock);
        if (row == null) {
            return "暂无标的";
        }
        return label(screen, itemBlock.title, row) + " · " + value(screen, itemBlock.title, row);
    }

    private String alertCountText(BlockSpec alertBlock, boolean adminMode) {
        return alertItemRows(alertBlock, adminMode).size() + " 条提醒";
    }

    private String triggerText(ScreenSpec screen, BlockSpec groupBlock) {
        RowSpec row = row(groupBlock, "触发提醒");
        if (row == null) {
            return "0 触发";
        }
        return runtimeValueStore.valueFor(screen, groupBlock.title, row) + " 触发";
    }

    private String triggerCount(ScreenSpec screen, BlockSpec groupBlock) {
        RowSpec row = row(groupBlock, "触发提醒");
        return row == null ? "0" : runtimeValueStore.valueFor(screen, groupBlock.title, row);
    }

    private String nearCount(ScreenSpec screen, BlockSpec alertBlock, boolean adminMode) {
        int count = 0;
        for (RowSpec row : alertItemRows(alertBlock, adminMode)) {
            if ("amber".equals(tone(screen, alertBlock.title, row))) {
                count++;
            }
        }
        return String.valueOf(count);
    }

    private String latestAlertText(ScreenSpec screen, BlockSpec alertBlock) {
        String value = valueOf(screen, alertBlock, "最近提醒");
        return value.isEmpty() ? "暂无最近提醒" : value;
    }

    private String statusLabel(String blockTitle, String tone) {
        if ("持仓列表".equals(blockTitle)) {
            if ("up".equals(tone)) {
                return "上涨";
            }
            if ("down".equals(tone)) {
                return "下跌";
            }
            return "持平";
        }
        if ("danger".equals(tone)) {
            return "触发";
        }
        if ("amber".equals(tone)) {
            return "接近";
        }
        if ("success".equals(tone) || "down".equals(tone)) {
            return "正常";
        }
        if ("blue".equals(tone) || "primary".equals(tone)) {
            return "选中";
        }
        return "查看";
    }

    private int parseInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String emptyFallback(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value;
    }

    private static final class TargetOption {
        private final String targetType;
        private final String name;
        private final String code;

        private TargetOption(String targetType, String name, String code) {
            this.targetType = targetType;
            this.name = name;
            this.code = code;
        }
    }

    private LinearLayout.LayoutParams weightParams(int weight, int leftDp, int rightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(activity.dp(leftDp), 0, activity.dp(rightDp), 0);
        return params;
    }

    private void setBottomMargin(View view, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, activity.dp(marginDp));
        view.setLayoutParams(params);
    }
}
