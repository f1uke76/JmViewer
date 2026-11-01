package com.moco.comicsearch;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 三横 ↔ 叉 的可动画图标。
 * progress = 0 时是三条横线（菜单），progress = 1 时是叉（关闭）。
 * 上下两条线一边向中间靠拢一边旋转，中间一条淡出。
 */
public class HamburgerDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float progress = 0f;
    private int baseAlpha = 255;

    public HamburgerDrawable(int color, float strokeDp, float density) {
        this.density = density;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeDp * density);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidateSelf();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        // 与 24dp 的菜单图标对齐：线长 18dp，间距 5dp
        float len = Math.min(b.width(), b.height()) * 0.75f;
        float half = len / 2f;
        float gap = len * 5f / 18f;
        float p = progress;

        paint.setAlpha(baseAlpha);

        // 上横：向中间收拢并旋转 +45°
        canvas.save();
        canvas.translate(cx, cy - gap * (1f - p));
        canvas.rotate(45f * p);
        canvas.drawLine(-half, 0f, half, 0f, paint);
        canvas.restore();

        // 中横：淡出
        paint.setAlpha((int) (baseAlpha * (1f - p)));
        canvas.drawLine(cx - half, cy, cx + half, cy, paint);
        paint.setAlpha(baseAlpha);

        // 下横：向中间收拢并旋转 -45°
        canvas.save();
        canvas.translate(cx, cy + gap * (1f - p));
        canvas.rotate(-45f * p);
        canvas.drawLine(-half, 0f, half, 0f, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        baseAlpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
