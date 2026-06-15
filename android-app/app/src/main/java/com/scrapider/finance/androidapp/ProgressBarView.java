package com.scrapider.finance.androidapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class ProgressBarView extends View {
    private static final int SURFACE_MUTED = Color.rgb(46, 48, 51);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int percent;
    private final int color;

    ProgressBarView(Context context, int percent, int color) {
        super(context);
        this.percent = percent;
        this.color = color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(4);
        paint.setColor(SURFACE_MUTED);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
        paint.setColor(color);
        canvas.drawRoundRect(0, 0, getWidth() * percent / 100f, getHeight(), radius, radius, paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
