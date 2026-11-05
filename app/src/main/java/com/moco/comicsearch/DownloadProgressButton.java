package com.moco.comicsearch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

/** 详情页下载按钮：主题色从左向右填充，完成后图标从下载切换为打勾。 */
public class DownloadProgressButton extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Drawable iconIdle;   // 未填充区域：主题色
    private final Drawable iconOnFill; // 已填充区域：白色
    private final Drawable iconCheck;  // 完成态：白色打勾

    private int progress;
    private boolean downloaded;
    private float morph;
    private float iconScale = 1f;
    private ValueAnimator animator;
    private ValueAnimator morphAnimator;
    private ValueAnimator progressAnimator;

    public DownloadProgressButton(Context context) {
        this(context, null);
    }

    public DownloadProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        int primary = ThemeManager.primary(context);
        bgPaint.setColor(ContextCompat.getColor(context, R.color.surface2));
        fillPaint.setColor(primary);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.divider));

        iconIdle = tinted(context, R.drawable.ic_download, primary);
        iconOnFill = tinted(context, R.drawable.ic_download, Color.WHITE);
        iconCheck = tinted(context, R.drawable.ic_check, Color.WHITE);
    }

    private static Drawable tinted(Context ctx, int res, int color) {
        Drawable d = ContextCompat.getDrawable(ctx, res);
        if (d == null) {
            return null;
        }
        d = d.mutate();
        d.setTint(color);
        return d;
    }

    public void setProgress(int value) {
        final int target = Math.max(0, Math.min(100, value));
        if (downloaded) {
            return;
        }
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        if (progress == target) {
            return;
        }
        progressAnimator = ValueAnimator.ofInt(progress, target);
        progressAnimator.setDuration(250);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.addUpdateListener(a -> {
            progress = (int) a.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
        morph = 0f;
    }

    public void setDownloaded(boolean value) {
        downloaded = value;
        if (value) {
            progress = 100;
            morph = 1f;
        }
        invalidate();
    }

    /** 完成时先把进度补到 100，再把下载图标过渡成打勾。 */
    public void animateToDone(final Runnable end) {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
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
                startMorph(end);
            }
        });
        animator.start();
    }

    private void startMorph(final Runnable end) {
        if (morphAnimator != null) {
            morphAnimator.cancel();
        }
        morphAnimator = ValueAnimator.ofFloat(morph, 1f);
        morphAnimator.setDuration(220);
        morphAnimator.setInterpolator(new DecelerateInterpolator());
        morphAnimator.addUpdateListener(a -> {
            morph = (float) a.getAnimatedValue();
            invalidate();
        });
        morphAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                downloaded = true;
                iconScale = 1f;
                animate().cancel();
                animate().scaleX(1.08f).scaleY(1.08f).setDuration(100)
                        .withEndAction(() -> animate().scaleX(1f).scaleY(1f)
                                .setDuration(90).start()).start();
                invalidate();
                if (end != null) {
                    end.run();
                }
            }
        });
        morphAnimator.start();
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
        if (!downloaded) {
            float half = borderPaint.getStrokeWidth() / 2f;
            canvas.drawRect(half, half, w - half, h - half, borderPaint);
        }

        int size = (int) (Math.min(w, h) * 0.5f * iconScale);
        int left = (w - size) / 2;
        int top = (h - size) / 2;
        Rect bounds = new Rect(left, top, left + size, top + size);

        // 下载图标：被填充覆盖的部分白色，其余主题色
        if (morph < 1f && iconIdle != null && iconOnFill != null) {
            int alpha = (int) (255 * (1f - morph));
            iconOnFill.setAlpha(alpha);
            iconIdle.setAlpha(alpha);
            iconOnFill.setBounds(bounds);
            iconIdle.setBounds(bounds);
            canvas.save();
            canvas.clipRect(0, 0, fillW, h);
            iconOnFill.draw(canvas);
            canvas.restore();
            canvas.save();
            canvas.clipRect(fillW, 0, w, h);
            iconIdle.draw(canvas);
            canvas.restore();
        }

        // 完成勾：从左往右揭开
        if (morph > 0f && iconCheck != null) {
            iconCheck.setAlpha(255);
            iconCheck.setBounds(bounds);
            canvas.save();
            canvas.clipRect(0, 0, left + size * Math.min(1f, morph), h);
            iconCheck.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        if (morphAnimator != null) {
            morphAnimator.cancel();
        }
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
