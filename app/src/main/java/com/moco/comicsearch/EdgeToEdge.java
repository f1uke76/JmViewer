package com.moco.comicsearch;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** 沉浸状态栏：内容绘制到系统栏后面，再按 insets 给标题栏/内容补间距。 */
public final class EdgeToEdge {

    private EdgeToEdge() {
    }

    /** 跟随当前主题决定状态栏图标明暗。 */
    public static void setup(Activity activity) {
        setup(activity, isLight(activity));
    }

    /** 强制浅色图标（阅读器等始终黑底的页面）。 */
    public static void setupDark(Activity activity) {
        setup(activity, false);
    }

    private static void setup(Activity activity, boolean lightIcons) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(window, window.getDecorView());
        c.setAppearanceLightStatusBars(lightIcons);
        c.setAppearanceLightNavigationBars(lightIcons);
    }

    public static boolean isLight(Context ctx) {
        int mode = new Prefs(ctx).getThemeMode();
        if (mode == 1) {
            return true;
        }
        if (mode == 2) {
            return false;
        }
        int night = ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_YES;
    }

    /** 给顶部栏加状态栏高度的 padding，标题栏背景因此延伸到状态栏后面。 */
    public static void padTop(View v) {
        if (v == null) {
            return;
        }
        final int l = v.getPaddingLeft();
        final int t = v.getPaddingTop();
        final int r = v.getPaddingRight();
        final int b = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(l, t + sb.top, r, b);
            return insets;
        });
        ViewCompat.requestApplyInsets(v);
    }

    /** 给底部容器加导航栏高度的 padding，避免内容/悬浮按钮被手势条挡住。 */
    public static void padBottom(View v) {
        if (v == null) {
            return;
        }
        final int l = v.getPaddingLeft();
        final int t = v.getPaddingTop();
        final int r = v.getPaddingRight();
        final int b = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(l, t, r, b + sb.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(v);
    }

    /** 上下都要补间距（侧边栏、登录页这类整块背景）。 */
    public static void padVertical(View v) {
        if (v == null) {
            return;
        }
        final int l = v.getPaddingLeft();
        final int t = v.getPaddingTop();
        final int r = v.getPaddingRight();
        final int b = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(l, t + sb.top, r, b + sb.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(v);
    }
}
