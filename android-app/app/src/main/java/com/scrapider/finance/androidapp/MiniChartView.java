package com.scrapider.finance.androidapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class MiniChartView extends View {
    private static final int BORDER = Color.rgb(54, 54, 58);
    private static final int BLUE = Color.rgb(0, 107, 230);
    private static final int RED = Color.rgb(220, 68, 70);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MiniChartView(Context context) {
        super(context);
        setPadding(0, dp(12), 0, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        paint.setStrokeWidth(dp(1));
        paint.setColor(BORDER);
        for (int i = 1; i < 4; i++) {
            float y = height * i / 4f;
            canvas.drawLine(0, y, width, y, paint);
        }

        float[] values = {0.72f, 0.58f, 0.64f, 0.48f, 0.55f, 0.36f, 0.42f, 0.28f, 0.34f};
        paint.setColor(RED);
        paint.setStrokeWidth(dp(2));
        for (int i = 0; i < values.length - 1; i++) {
            float x1 = width * i / (float) (values.length - 1);
            float y1 = height * values[i];
            float x2 = width * (i + 1) / (float) (values.length - 1);
            float y2 = height * values[i + 1];
            canvas.drawLine(x1, y1, x2, y2, paint);
        }

        paint.setColor(BLUE);
        canvas.drawCircle(width * 0.75f, height * 0.34f, dp(4), paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
