package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class BlockRenderer {
    interface RowSelection {
        void open(String title, RowSpec row);
    }

    private final CockpitActivity activity;
    private final RowSelection rowSelection;
    private final StepBlockRenderer stepBlockRenderer;
    private final FormStateStore formStateStore;
    private final RuntimeValueStore runtimeValueStore;
    private boolean adminMode = true;

    BlockRenderer(CockpitActivity activity, RowSelection rowSelection, FormStateStore formStateStore, RuntimeValueStore runtimeValueStore) {
        this.activity = activity;
        this.rowSelection = rowSelection;
        this.stepBlockRenderer = new StepBlockRenderer(activity, rowSelection, runtimeValueStore);
        this.formStateStore = formStateStore;
        this.runtimeValueStore = runtimeValueStore;
    }

    void setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }

    View blockView(ScreenSpec screen, BlockSpec block) {
        LinearLayout card = activity.card();
        TextView heading = activity.label(block.title, 16, CockpitActivity.FOREGROUND, Typeface.BOLD);
        card.addView(heading);

        if ("chips".equals(block.type)) {
            card.addView(chipLine(screen, block.title, block.rows));
        } else if ("metrics".equals(block.type)) {
            card.addView(metricGrid(screen, block.title, block.rows));
        } else if ("steps".equals(block.type)) {
            card.addView(stepBlockRenderer.stepList(screen, block.title, visibleRows(block.rows)));
        } else if ("progress".equals(block.type)) {
            for (RowSpec row : visibleRows(block.rows)) {
                card.addView(progressRow(screen, block.title, row));
            }
        } else if ("chart".equals(block.type)) {
            card.addView(chipLine(screen, block.title, block.rows));
            MiniChartView chart = new MiniChartView(activity);
            chart.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.dp(128)
            ));
            card.addView(chart);
        } else if ("form".equals(block.type)) {
            for (RowSpec row : visibleRows(block.rows)) {
                card.addView(formRow(screen, row));
            }
        } else if ("paragraph".equals(block.type)) {
            for (RowSpec row : visibleRows(block.rows)) {
                card.addView(paragraphRow(screen, block.title, row));
            }
        } else if ("quotes".equals(block.type)) {
            for (RowSpec row : visibleRows(block.rows)) {
                card.addView(quoteRow(screen, block.title, row));
            }
        } else {
            for (RowSpec row : visibleRows(block.rows)) {
                card.addView(listRow(screen, block.title, row));
            }
        }
        return card;
    }

    View formRow(RowSpec row) {
        return formRow(null, row);
    }

    View formRow(ScreenSpec screen, RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        box.addView(activity.label(row.label, 12, CockpitActivity.MUTED, Typeface.BOLD));

        View value;
        if ("primary".equals(row.tone) || "danger".equals(row.tone)) {
            value = activity.actionButton(row.value, "danger".equals(row.tone));
            value.setOnClickListener(v -> rowSelection.open(row.label, row));
        } else if ("switch".equals(row.tone)) {
            value = activity.switchControl(row.value);
            value.setOnClickListener(v -> rowSelection.open(row.label, row));
        } else if ("slider".equals(row.tone)) {
            value = activity.sliderControl(row.value);
            value.setOnClickListener(v -> rowSelection.open(row.label, row));
        } else {
            boolean editable = !"readonly".equals(row.tone) && !"success".equals(row.tone) && !"amber".equals(row.tone);
            EditText input = activity.inputControl(fieldValue(screen, row), editable, "password".equals(row.tone));
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
            if ("success".equals(row.tone)) {
                input.setTextColor(CockpitActivity.GREEN);
            } else if ("amber".equals(row.tone)) {
                input.setTextColor(CockpitActivity.AMBER);
            }
            value = input;
            value.setOnClickListener(v -> rowSelection.open(row.label, row));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, activity.dp(5), 0, 0);
        box.addView(value, params);
        return box;
    }

    View progressRow(RowSpec row) {
        return progressRow(null, row);
    }

    View progressRow(ScreenSpec screen, RowSpec row) {
        return progressRow(screen, "", row);
    }

    View progressRow(ScreenSpec screen, String blockTitle, RowSpec row) {
        String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
        String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
        String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(12), 0, 0);
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = activity.label(rowLabel, 13, CockpitActivity.FOREGROUND, Typeface.BOLD);
        line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = activity.label(rowValue, 13, activity.colorFor(rowTone), Typeface.BOLD);
        value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        line.addView(value);
        box.addView(line);
        box.addView(new ProgressBarView(activity, activity.parsePercent(rowValue), activity.colorFor(rowTone)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(8)
        ));
        return box;
    }

    View listRow(RowSpec row) {
        return listRow(null, row);
    }

    View listRow(ScreenSpec screen, RowSpec row) {
        return listRow(screen, "", row);
    }

    View listRow(ScreenSpec screen, String blockTitle, RowSpec row) {
        String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
        String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
        String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.TOP);
        item.setPadding(0, activity.dp(12), 0, 0);

        TextView tag = activity.pill(rowLabel, CockpitActivity.SURFACE_MUTED, activity.colorFor(rowTone));
        item.addView(tag);
        if ("switch".equals(row.tone)) {
            item.addView(activity.switchControl(rowValue), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        } else if (isProminentAction(row)) {
            Button button = activity.actionButton(row.label, false);
            button.setOnClickListener(v -> rowSelection.open(row.label, row));
            item.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        } else {
            TextView value = activity.label(rowValue, 14, CockpitActivity.MUTED, Typeface.NORMAL);
            value.setPadding(activity.dp(10), 0, 0, 0);
            item.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (row.targetScreenTitle != null) {
                TextView go = activity.pill("进入", CockpitActivity.SURFACE_MUTED, CockpitActivity.BLUE);
                item.addView(go);
            }
        }
        item.setOnClickListener(v -> rowSelection.open(row.label, row));
        return item;
    }

    private boolean isProminentAction(RowSpec row) {
        return row.prominentAction;
    }

    private View chipLine(ScreenSpec screen, String blockTitle, List<RowSpec> rows) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, activity.dp(12), 0, activity.dp(2));
        for (RowSpec row : visibleRows(rows)) {
            String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
            String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
            String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
            TextView item = activity.chip(rowLabel + (rowValue.isEmpty() ? "" : " · " + rowValue), activity.isBlue(rowTone));
            item.setTextColor(activity.colorFor(rowTone));
            item.setEnabled(!"disabled".equals(rowTone));
            if ("disabled".equals(rowTone)) {
                item.setAlpha(0.48f);
            } else {
                item.setOnClickListener(v -> rowSelection.open(screen.title + " · 筛选与模式", row));
            }
            line.addView(item);
        }
        scroll.addView(line);
        return scroll;
    }

    private View metricGrid(ScreenSpec screen, String blockTitle, List<RowSpec> rows) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        List<RowSpec> visibleRows = visibleRows(rows);
        for (int i = 0; i < visibleRows.size(); i += 2) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.addView(metricCell(screen, blockTitle, visibleRows.get(i)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (i + 1 < visibleRows.size()) {
                line.addView(activity.space(8), new LinearLayout.LayoutParams(activity.dp(8), 1));
                line.addView(metricCell(screen, blockTitle, visibleRows.get(i + 1)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lineParams.setMargins(0, 0, 0, activity.dp(8));
            box.addView(line, lineParams);
        }
        return box;
    }

    private List<RowSpec> visibleRows(List<RowSpec> rows) {
        List<RowSpec> visible = new ArrayList<>();
        for (RowSpec row : rows) {
            if (!row.adminOnly || adminMode) {
                visible.add(row);
            }
        }
        return visible;
    }

    private View metricCell(ScreenSpec screen, String blockTitle, RowSpec row) {
        String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
        String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
        String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(activity.dp(11), activity.dp(9), activity.dp(11), activity.dp(9));
        cell.setBackground(activity.rounded(CockpitActivity.SURFACE_ELEVATED, activity.dp(8), CockpitActivity.BORDER));
        cell.addView(activity.label(rowLabel, 12, CockpitActivity.MUTED, Typeface.NORMAL));
        TextView value = activity.label(rowValue, 18, activity.colorFor(rowTone), Typeface.BOLD);
        value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        value.setPadding(0, activity.dp(5), 0, 0);
        cell.addView(value);
        return cell;
    }

    private View paragraphRow(ScreenSpec screen, String blockTitle, RowSpec row) {
        String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
        String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
        String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(12), 0, 0);
        TextView label = activity.label(rowLabel, 12, activity.colorFor(rowTone), Typeface.BOLD);
        box.addView(label);
        if ("field".equals(rowTone)) {
            EditText input = activity.inputControl(fieldValue(screen, row), true, false);
            input.setMinLines(2);
            input.setGravity(Gravity.TOP);
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
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, activity.dp(6), 0, 0);
            box.addView(input, params);
        } else if (isProminentAction(row)) {
            Button button = activity.actionButton(rowValue, "danger".equals(rowTone));
            button.setOnClickListener(v -> rowSelection.open(row.label, row));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, activity.dp(6), 0, 0);
            box.addView(button, params);
        } else {
            TextView value = activity.label(rowValue, 14, CockpitActivity.MUTED, Typeface.NORMAL);
            value.setPadding(0, activity.dp(4), 0, 0);
            box.addView(value);
        }
        return box;
    }

    private View quoteRow(ScreenSpec screen, String blockTitle, RowSpec row) {
        if ("field".equals(row.tone)) {
            return searchRow(screen, row);
        }
        String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
        String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
        String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        item.setBackground(activity.rounded(CockpitActivity.SURFACE_ELEVATED, activity.dp(8), CockpitActivity.BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, activity.dp(10), 0, 0);
        item.setLayoutParams(params);

        TextView name = activity.label(rowLabel, 14, CockpitActivity.FOREGROUND, Typeface.BOLD);
        item.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = activity.label(rowValue, 13, activity.colorFor(rowTone), Typeface.BOLD);
        value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        value.setGravity(Gravity.END);
        item.addView(value);
        item.setOnClickListener(v -> rowSelection.open(row.label, row));
        return item;
    }

    private View searchRow(ScreenSpec screen, RowSpec row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        TextView label = activity.label(row.label, 12, CockpitActivity.MUTED, Typeface.BOLD);
        box.addView(label);
        EditText input = activity.inputControl(fieldValue(screen, row), true, false);
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
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, activity.dp(6), 0, 0);
        box.addView(input, params);
        box.setOnClickListener(v -> rowSelection.open(row.label, row));
        return box;
    }

    private String fieldValue(ScreenSpec screen, RowSpec row) {
        String screenTitle = screen == null ? "抽屉" : screen.title;
        if (formStateStore.hasValue(screenTitle, row.label)) {
            return formStateStore.value(screenTitle, row.label);
        }
        if ("password".equals(row.tone) && row.value.contains("•")) {
            return "";
        }
        return runtimeValueStore.valueFor(screen, row);
    }
}
