package com.moco.comicsearch;

import android.app.Activity;
import android.content.Context;

/**
 * 更新检查的统一入口：检查在应用级跑，不依赖某个页面，
 * 页面退出后结果仍然会回到当前前台页面弹窗。
 */
public final class UpdateManager {

    public static final int MODE_SILENT = 0; // 只更新红点，不打扰
    public static final int MODE_AUTO = 1;   // 启动检查：尊重开关、同版本只弹一次
    public static final int MODE_MANUAL = 2; // 手动检查：总是给反馈

    public interface Done {
        void onDone(UpdateChecker.Result result);
    }

    private static boolean running;

    private UpdateManager() {
    }

    public static boolean running() {
        return running;
    }

    public static void check(final Context ctx, final int mode, final Done done) {
        if (running) {
            return;
        }
        running = true;
        final Context app = ctx.getApplicationContext();
        UpdateChecker.check(app, r -> {
            running = false;
            if (done != null) {
                done.onDone(r);
            }
            Activity a = App.current();
            if (a == null) {
                return;
            }
            if (r == null || !r.ok) {
                if (mode == MODE_MANUAL) {
                    Ui.toast(a, a.getString(r != null && r.code == 404
                            ? R.string.update_repo_private
                            : R.string.check_update_failed));
                }
                return;
            }
            if (!r.newer) {
                if (mode == MODE_MANUAL) {
                    Ui.toast(a, a.getString(R.string.up_to_date));
                }
                return;
            }
            if (mode == MODE_SILENT) {
                return;
            }
            if (mode == MODE_AUTO) {
                if (!new Prefs(app).autoCheckUpdate()) {
                    return;
                }
                String v = "v" + r.version;
                if (v.equals(UpdateChecker.promptedVersion)) {
                    return;
                }
                UpdateChecker.promptedVersion = v;
            }
            UpdateDialog.show(a, r);
        });
    }
}
