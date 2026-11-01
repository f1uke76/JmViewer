package com.moco.comicsearch;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/** 启动时按设置应用明亮 / 黑暗 / 跟随系统。 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(toNightMode(new Prefs(this).getThemeMode()));
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
