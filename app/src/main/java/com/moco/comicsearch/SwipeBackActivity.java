package com.moco.comicsearch;

import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 全局返回手势：在页面任意位置从左向右滑动即触发返回。
 * 子类可用 {@link #shouldHandleSwipeBack(MotionEvent)} 排除特定区域（如阅读进度条）。
 */
public class SwipeBackActivity extends AppCompatActivity {

    private float downX;
    private float downY;
    private boolean tracking;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!shouldHandleSwipeBack(ev)) {
                    tracking = false;
                    break;
                }
                downX = ev.getX();
                downY = ev.getY();
                tracking = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (dx > dp(90) && Math.abs(dx) > Math.abs(dy) * 1.6f) {
                        tracking = false;
                        onSwipeBack();
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 返回 true 表示该次触摸允许触发返回手势。 */
    protected boolean shouldHandleSwipeBack(MotionEvent ev) {
        return true;
    }

    protected void onSwipeBack() {
        onBackPressed();
    }

    protected int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
