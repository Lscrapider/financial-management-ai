package com.scrapider.finance.androidapp;

import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

final class StepBlockRenderer {
    private final CockpitActivity activity;
    private final BlockRenderer.RowSelection rowSelection;
    private final RuntimeValueStore runtimeValueStore;

    StepBlockRenderer(CockpitActivity activity, BlockRenderer.RowSelection rowSelection, RuntimeValueStore runtimeValueStore) {
        this.activity = activity;
        this.rowSelection = rowSelection;
        this.runtimeValueStore = runtimeValueStore;
    }

    View stepList(ScreenSpec screen, String blockTitle, List<RowSpec> rows) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, activity.dp(10), 0, 0);
        for (int i = 0; i < rows.size(); i++) {
            box.addView(stepRow(screen, blockTitle, i + 1, rows.get(i)));
        }
        return box;
    }

    private View stepRow(ScreenSpec screen, String blockTitle, int number, RowSpec row) {
        String rowLabel = runtimeValueStore.labelFor(screen, blockTitle, row);
        String rowValue = runtimeValueStore.valueFor(screen, blockTitle, row);
        String rowTone = runtimeValueStore.toneFor(screen, blockTitle, row);
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.TOP);
        item.setPadding(0, activity.dp(8), 0, 0);

        TextView index = activity.pill(String.valueOf(number), CockpitActivity.SURFACE_MUTED, activity.colorFor(rowTone));
        item.addView(index);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(activity.dp(10), 0, 0, 0);

        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = activity.label(rowLabel, 14, CockpitActivity.FOREGROUND, Typeface.BOLD);
        line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = activity.label(rowValue, 13, activity.colorFor(rowTone), Typeface.BOLD);
        value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        line.addView(value);
        body.addView(line);

        if (rowValue.contains("%")) {
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.dp(6)
            );
            progressParams.setMargins(0, activity.dp(6), 0, 0);
            body.addView(new ProgressBarView(activity, activity.parsePercent(rowValue), activity.colorFor(rowTone)), progressParams);
        }

        item.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        item.setOnClickListener(v -> rowSelection.open(row.label, row));
        return item;
    }
}
