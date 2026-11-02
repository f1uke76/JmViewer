package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * 统一切换态按钮：
 * 否 = 暗白底 + 主题色图标；是 = 主题色底 + 亮白图标。
 * 支持圆形（多选栏）与方形（详情页按钮）两种外形。
 */
public class ToggleIconButton extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Drawable icon;
    private boolean checked;
    private boolean circle = true;

    public ToggleIconButton(Context context) {
        this(context, null);
    }

    public ToggleIconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));
        borderPaint.setColor(ContextCompat.getColor(context, R.color.divider));
    }

    public void setCircle(boolean circle) {
        this.circle = circle;
        invalidate();
    }

    public void setIcon(int res) {
        icon = ContextCompat.getDrawable(getContext(), res);
        invalidate();
    }

    public void setCheckedState(boolean value) {
        checked = value;
        invalidate();
    }

    public boolean isCheckedState() {
        return checked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        rect.set(0, 0, w, h);
        int primary = ThemeManager.primary(getContext());
        if (checked) {
            bgPaint.setColor(primary);
        } else {
            bgPaint.setColor(ContextCompat.getColor(getContext(), R.color.surface2));
        }
        if (circle) {
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, bgPaint);
            if (!checked) {
                canvas.drawCircle(w / 2f, h / 2f,
                        Math.min(w, h) / 2f - borderPaint.getStrokeWidth() / 2f, borderPaint);
            }
        } else {
            canvas.drawRect(rect, bgPaint);
            if (!checked) {
                canvas.drawRect(1, 1, w - 1, h - 1, borderPaint);
            }
        }
        if (icon == null) {
            return;
        }
        int size = (int) (Math.min(w, h) * 0.46f);
        int left = (w - size) / 2;
        int top = (h - size) / 2;
        icon.setBounds(left, top, left + size, top + size);
        icon.setTint(checked ? Color.WHITE : primary);
        icon.draw(canvas);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
