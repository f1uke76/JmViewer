package com.moco.comicsearch;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;

/**
 * 主题色：默认跟随系统壁纸（Android 12+ 动态取色），也可手动选择预设色系。
 * 所有强调色都通过主题属性 colorPrimary / colorPrimaryContainer 取值，保证同一色系分层。
 */
public final class ThemeManager {

    public static final int AUTO = 0;

    private static final int[] OVERLAYS = {
            0,
            R.style.ThemeOverlay_ComicSearch_Accent_Blue,
            R.style.ThemeOverlay_ComicSearch_Accent_Green,
            R.style.ThemeOverlay_ComicSearch_Accent_Purple,
            R.style.ThemeOverlay_ComicSearch_Accent_Orange,
            R.style.ThemeOverlay_ComicSearch_Accent_Teal
    };

    private ThemeManager() {
    }

    /** 在 setContentView 之前调用。 */
    public static void apply(Activity activity) {
        int mode = new Prefs(activity).getThemeAccent();
        if (mode == AUTO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DynamicColors.applyToActivityIfAvailable(activity);
            }
            return;
        }
        if (mode > 0 && mode < OVERLAYS.length) {
            activity.getTheme().applyStyle(OVERLAYS[mode], true);
        }
    }

    public static int primary(Context context) {
        return color(context, com.google.android.material.R.attr.colorPrimary, R.color.primary);
    }

    public static int primaryContainer(Context context) {
        return color(context, com.google.android.material.R.attr.colorPrimaryContainer, R.color.primary_dim);
    }

    public static int onPrimary(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnPrimary, R.color.white);
    }

    private static int color(Context context, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(context, tv.resourceId);
            }
            return tv.data;
        }
        return ContextCompat.getColor(context, fallback);
    }
}
