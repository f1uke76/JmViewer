package com.moco.comicsearch;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** 单一桌面入口：通过禁用/启用别名强制启动器刷新图标缓存。 */
public final class IconSwitcher {

    private static final String ALIAS = ".IconPurple";

    private IconSwitcher() {
    }

    public static void forceRefresh(Context ctx) {
        try {
            String pkg = ctx.getPackageName();
            ComponentName cn = new ComponentName(pkg, pkg + ALIAS);
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
