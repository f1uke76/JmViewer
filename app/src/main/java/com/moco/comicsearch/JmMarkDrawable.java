package com.moco.comicsearch;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 封面占位：和主页未开启推荐时显示的「Jm」标识同款，单色灰。
 * 封面加载完成后会被真实封面替换。
 */
public class JmMarkDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "Jm";

    public JmMarkDrawable(int color) {
        paint.setColor(color);
        paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public void setText(String value) {
        text = value;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return;
        }
        paint.setTextSize(Math.min(b.width(), b.height()) * 0.62f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = b.exactCenterY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, b.exactCenterX(), baseline, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
