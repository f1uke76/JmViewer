package com.moco.comicsearch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;

import androidx.core.content.ContextCompat;

/**
 * 与整体实心块风格一致的自定义开关：
 * 药丸形轨道 + 无阴影的实心圆点，开启为主色蓝，关闭为分割线灰。
 */
public class PillSwitch extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean checked;
    private float fraction;
    private CompoundButton.OnCheckedChangeListener listener;

    public PillSwitch(Context context) {
        super(context);
        init();
    }

    public PillSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PillSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        thumbPaint.setColor(0xFFFFFFFF);
        setOnClickListener(v -> setChecked(!checked, true));
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        setChecked(value, false);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value) {
            invalidate();
            return;
        }
        checked = value;
        if (animate) {
            ValueAnimator anim = ValueAnimator.ofFloat(fraction, value ? 1f : 0f);
            anim.setDuration(160);
            anim.addUpdateListener(a -> {
                fraction = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        } else {
            fraction = value ? 1f : 0f;
            invalidate();
        }
        if (listener != null) {
            listener.onCheckedChanged(null, checked);
        }
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(dp(46), widthMeasureSpec),
                resolveSize(dp(26), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        int on = ContextCompat.getColor(getContext(), R.color.primary);
        int off = ContextCompat.getColor(getContext(), R.color.divider);
        trackPaint.setColor(blend(off, on, fraction));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        float pad = dp(3);
        float diameter = h - pad * 2;
        float travel = w - diameter - pad * 2;
        float cx = pad + diameter / 2f + travel * fraction;
        canvas.drawCircle(cx, h / 2f, diameter / 2f, thumbPaint);
    }

    private static int blend(int from, int to, float f) {
        int fr = (from >> 16) & 0xff;
        int fg = (from >> 8) & 0xff;
        int fb = from & 0xff;
        int tr = (to >> 16) & 0xff;
        int tg = (to >> 8) & 0xff;
        int tb = to & 0xff;
        int r = Math.round(fr + (tr - fr) * f);
        int g = Math.round(fg + (tg - fg) * f);
        int b = Math.round(fb + (tb - fb) * f);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
