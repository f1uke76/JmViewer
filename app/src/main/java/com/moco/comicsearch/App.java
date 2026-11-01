package com.moco.comicsearch;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/** 启动时按设置应用明亮 / 黑暗 / 跟随系统。 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs prefs = new Prefs(this);
        AppCompatDelegate.setDefaultNightMode(toNightMode(prefs.getThemeMode()));
        // 每次版本更新后强制刷新一次桌面图标，避免启动器沿用旧图标缓存
        if (prefs.getIconRefreshVersion() != BuildConfig.VERSION_CODE) {
            IconSwitcher.forceRefresh(this);
            prefs.setIconRefreshVersion(BuildConfig.VERSION_CODE);
        }
    }

    public static int toNightMode(int mode) {
        if (mode == 1) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (mode == 2) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}
