package com.moco.comicsearch;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * 桌面图标：衬线「Jm」，按主题色预生成 8 套，运行时只启用与当前强调色最接近的一套。
 * 明亮白底 / 暗黑黑底由 bg_jm_icon 随系统切换。
 */
public final class IconSwitcher {

    private static final String[] ALIASES = {
            ".JmBlue", ".JmGreen", ".JmOlive", ".JmOrange",
            ".JmRed", ".JmPurple", ".JmTeal", ".JmPink"
    };

    private static final int[] COLORS = {
            0xFF1668E3, 0xFF1E8E5A, 0xFF5A6B1E, 0xFFC4621E,
            0xFFC0392B, 0xFF6A4BC4, 0xFF0E7C86, 0xFFC2185B
    };

    private IconSwitcher() {
    }

    public static void apply(Context ctx) {
        try {
            int idx = nearest(accentOf(ctx));
            if (currentEnabled(ctx) == idx) {
                return;
            }
            setAlias(ctx, idx);
        } catch (Exception ignored) {
        }
    }

    private static int accentOf(Context ctx) {
        Prefs prefs = new Prefs(ctx);
        switch (prefs.getThemeAccent()) {
            case 1:
                return 0xFF1668E3;
            case 2:
                return 0xFF1E8E5A;
            case 3:
                return 0xFF6A4BC4;
            case 4:
                return 0xFFC4621E;
            case 5:
                return 0xFF0E7C86;
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        return ctx.getResources().getColor(
                                android.R.color.system_accent1_500, ctx.getTheme());
                    } catch (Exception ignored) {
                    }
                }
                return 0xFF1668E3;
        }
    }

    private static int nearest(int color) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < COLORS.length; i++) {
            double d = dist(color, COLORS[i]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static double dist(int a, int b) {
        int ar = (a >> 16) & 0xff;
        int ag = (a >> 8) & 0xff;
        int ab = a & 0xff;
        int br = (b >> 16) & 0xff;
        int bg = (b >> 8) & 0xff;
        int bb = b & 0xff;
        return Math.sqrt((ar - br) * (ar - br) + (ag - bg) * (ag - bg) + (ab - bb) * (ab - bb));
    }

    private static int currentEnabled(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();
        for (int i = 0; i < ALIASES.length; i++) {
            if (pm.getComponentEnabledSetting(new ComponentName(pkg, pkg + ALIASES[i]))
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return i;
            }
        }
        return -1;
    }

    private static void setAlias(Context ctx, int idx) {
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();
        for (int i = 0; i < ALIASES.length; i++) {
            pm.setComponentEnabledSetting(
                    new ComponentName(pkg, pkg + ALIASES[i]),
                    i == idx ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
