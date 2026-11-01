package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 封面右下角的圆环下载进度。 */
public class RingProgressView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int progress;

    public RingProgressView(Context context) {
        super(context);
        init();
    }

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RingProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float stroke = getResources().getDisplayMetrics().density * 3f;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(0x66000000);
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeWidth(stroke);
        bar.setStrokeCap(Paint.Cap.ROUND);
        bar.setColor(ThemeManager.primary(getContext()));
    }

    public void setProgress(int p) {
        progress = Math.max(0, Math.min(100, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float stroke = bar.getStrokeWidth();
        float radius = Math.min(w, h) / 2f - stroke / 2f;
        float cx = w / 2f;
        float cy = h / 2f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawCircle(cx, cy, radius, track);
        if (progress > 0) {
            canvas.drawArc(oval, -90f, 360f * progress / 100f, false, bar);
        }
    }
}
