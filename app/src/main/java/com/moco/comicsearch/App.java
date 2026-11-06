package com.moco.comicsearch;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

/** 启动时按设置应用明亮 / 黑暗 / 跟随系统，并记录当前前台页面。 */
public class App extends Application {

    private static Activity resumed;

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs prefs = new Prefs(this);
        AppCompatDelegate.setDefaultNightMode(toNightMode(prefs.getThemeMode()));
        IconSwitcher.apply(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                resumed = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (resumed == activity) {
                    resumed = null;
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (resumed == activity) {
                    resumed = null;
                }
            }
        });
    }

    /** 当前处于前台的页面，供后台任务弹窗使用。 */
    public static Activity current() {
        Activity a = resumed;
        if (a == null || a.isFinishing() || a.isDestroyed()) {
            return null;
        }
        return a;
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
