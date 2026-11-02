package com.moco.comicsearch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

/** 详情页下载按钮：主题色从左向右填充，完成后切换为打勾。 */
public class DownloadProgressButton extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable downloadIcon;
    private final Drawable checkIcon;
    private final int primary;
    private final int surface;

    private int progress;
    private boolean downloaded;
    private float iconScale = 1f;
    private ValueAnimator animator;

    public DownloadProgressButton(Context context) {
        this(context, null);
    }

    public DownloadProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        primary = ThemeManager.primary(context);
        surface = ContextCompat.getColor(context, R.color.surface2);
        bgPaint.setColor(surface);
        fillPaint.setColor(primary);
        downloadIcon = ContextCompat.getDrawable(context, R.drawable.ic_download);
        checkIcon = ContextCompat.getDrawable(context, R.drawable.ic_check);
    }

    public void setProgress(int value) {
        progress = Math.max(0, Math.min(100, value));
        if (progress > 0) {
            downloaded = false;
        }
        invalidate();
    }

    public void setDownloaded(boolean value) {
        downloaded = value;
        if (value) {
            progress = 100;
        }
        invalidate();
    }

    /** 完成时先把进度补到 100，再把图标切成打勾。 */
    public void animateToDone(final Runnable end) {
        if (animator != null) {
            animator.cancel();
        }
        final int start = progress;
        animator = ValueAnimator.ofInt(start, 100);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (int) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                downloaded = true;
                iconScale = 0.6f;
                animate().cancel();
                post(() -> animate().scaleX(1.12f).scaleY(1.12f).setDuration(110)
                        .withEndAction(() -> animate().scaleX(1f).scaleY(1f)
                                .setDuration(90).start()).start());
                invalidate();
                if (end != null) {
                    end.run();
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);
        float fillW = w * progress / 100f;
        if (fillW > 0) {
            canvas.drawRect(0, 0, fillW, h, fillPaint);
        }
        Drawable icon = downloaded ? checkIcon : downloadIcon;
        if (icon == null) {
            return;
        }
        int size = (int) (Math.min(w, h) * 0.46f * iconScale);
        int left = (w - size) / 2;
        int top = (h - size) / 2;
        icon.setBounds(left, top, left + size, top + size);

        // 未填充部分用主题色，填充部分用白色，形成进度切割效果
        if (downloaded) {
            icon.setTint(Color.WHITE);
            icon.draw(canvas);
            return;
        }
        icon.setTint(primary);
        icon.draw(canvas);
        if (fillW > 0) {
            canvas.save();
            canvas.clipRect(0, 0, fillW, h);
            icon.setTint(Color.WHITE);
            icon.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
