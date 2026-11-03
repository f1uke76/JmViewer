package com.moco.comicsearch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

/** 详情页下载按钮：主题色从左向右填充，完成后线条从下载图标过渡为打勾。 */
public class DownloadProgressButton extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path downloadPath = new Path();
    private final Path checkPath = new Path();
    private final PathMeasure checkMeasure = new PathMeasure();
    private final int primary;

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
        primary = ThemeManager.primary(context);
        bgPaint.setColor(ContextCompat.getColor(context, R.color.surface2));
        fillPaint.setColor(primary);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.BUTT);
        linePaint.setStrokeJoin(Paint.Join.MITER);
        linePaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        buildPaths();
    }

    private void buildPaths() {
        downloadPath.reset();
        downloadPath.moveTo(12, 4);
        downloadPath.lineTo(12, 15);
        downloadPath.moveTo(7.5f, 10.5f);
        downloadPath.lineTo(12, 15);
        downloadPath.lineTo(16.5f, 10.5f);
        downloadPath.moveTo(5, 16.5f);
        downloadPath.lineTo(5, 19.5f);
        downloadPath.lineTo(19, 19.5f);
        downloadPath.lineTo(19, 16.5f);

        checkPath.reset();
        checkPath.moveTo(5, 12.5f);
        checkPath.lineTo(9.5f, 17);
        checkPath.lineTo(19, 7);
        checkMeasure.setPath(checkPath, false);
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

    /** 完成时先把进度补到 100，再让下载线条过渡成打勾。 */
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
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        float scale = size / 24f;
        linePaint.setStrokeWidth((morph > 0f ? 2.4f : 2f) * scale);

        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);

        if (morph < 1f) {
            linePaint.setAlpha((int) (255 * (1f - morph)));
            linePaint.setColor(progress == 0 ? primary : Color.WHITE);
            canvas.drawPath(downloadPath, linePaint);
        }
        if (morph > 0f) {
            linePaint.setAlpha(255);
            linePaint.setColor(Color.WHITE);
            float len = checkMeasure.getLength();
            float part = Math.max(0f, Math.min(1f, morph));
            if (part >= 1f) {
                canvas.drawPath(checkPath, linePaint);
            } else {
                Path dst = new Path();
                checkMeasure.getSegment(0, len * part, dst, true);
                canvas.drawPath(dst, linePaint);
            }
        }
        canvas.restore();
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
