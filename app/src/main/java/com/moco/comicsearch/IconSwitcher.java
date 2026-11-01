package com.moco.comicsearch;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** 通过 activity-alias 切换桌面图标。 */
public final class IconSwitcher {

    public static final String[] ALIASES = {
            ".IconPurple", ".IconDark", ".IconPink", ".IconBlue"
    };

    private IconSwitcher() {
    }

    /** 先启用目标再禁用其它，避免图标短暂消失。 */
    public static void set(Context ctx, int index, boolean silent) {
        if (index < 0 || index >= ALIASES.length) {
            return;
        }
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();
        pm.setComponentEnabledSetting(
                new ComponentName(pkg, pkg + ALIASES[index]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        for (int i = 0; i < ALIASES.length; i++) {
            if (i == index) {
                continue;
            }
            pm.setComponentEnabledSetting(
                    new ComponentName(pkg, pkg + ALIASES[i]),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        new Prefs(ctx).setIconStyle(index);
    }

    /** 兜底：保证至少有一个入口处于启用状态。 */
    public static void ensure(Context ctx) {
        try {
            int style = new Prefs(ctx).getIconStyle();
            if (style < 0 || style >= ALIASES.length) {
                style = 0;
            }
            String pkg = ctx.getPackageName();
            ComponentName cn = new ComponentName(pkg, pkg + ALIASES[style]);
            int state = ctx.getPackageManager().getComponentEnabledSetting(cn);
            if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                set(ctx, style, true);
            }
        } catch (Exception ignored) {
        }
    }

    /** 强制刷新桌面图标缓存：先禁用再启用当前别名。 */
    public static void forceRefresh(Context ctx) {
        try {
            int style = new Prefs(ctx).getIconStyle();
            if (style < 0 || style >= ALIASES.length) {
                style = 0;
            }
            String pkg = ctx.getPackageName();
            ComponentName cn = new ComponentName(pkg, pkg + ALIASES[style]);
            PackageManager pm = ctx.getPackageManager();
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
        }
    }
}
