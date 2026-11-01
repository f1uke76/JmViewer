package com.moco.comicsearch;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * 支持「整页右滑呼出侧边栏」的抽屉布局：
 * 在任意位置横向右滑即可打开，左滑由 DrawerLayout 原生负责关闭。
 */
public class SwipeDrawerLayout extends DrawerLayout {

    /** 整页右滑回调：返回 true 表示手势已被消费（例如返回上一级）。 */
    public interface OnSwipeRightListener {
        boolean onSwipeRight();
    }

    private float downX;
    private float downY;
    private boolean handled;
    private OnSwipeRightListener listener;

    public void setOnSwipeRightListener(OnSwipeRightListener l) {
        listener = l;
    }

    public SwipeDrawerLayout(Context context) {
        super(context);
    }

    public SwipeDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeDrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                handled = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!handled) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (dx > dp(30) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        if (listener != null && listener.onSwipeRight()) {
                            handled = true;
                            return true;
                        }
                        if (!isDrawerOpen(GravityCompat.START)) {
                            handled = true;
                            openDrawer(GravityCompat.START);
                            return true;
                        }
                    }
                }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
