package com.moco.comicsearch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 统一的更新提示弹窗：更新 / 稍后 / 前往 GitHub 更新。 */
public final class UpdateDialog {

    private UpdateDialog() {
    }

    public static void show(final Activity activity, final UpdateChecker.Result r) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.isEmpty()) {
            notes = activity.getString(R.string.update_available_msg,
                    BuildConfig.VERSION_NAME, r.version);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.update_available, "v" + r.version))
                .setMessage(notes)
                .setPositiveButton(R.string.go_update, (d, w) -> startDownload(activity, r))
                .setNegativeButton(R.string.later, null)
                .setNeutralButton(R.string.update_on_github,
                        (d, w) -> openGitHub(activity, r))
                .show();
    }

    private static void startDownload(Activity activity, UpdateChecker.Result r) {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        UpdateService.start(activity, r.apkApiUrl, r.apkUrl, r.version);
        Ui.toast(activity, activity.getString(R.string.update_downloading));
    }

    private static void openGitHub(Activity activity, UpdateChecker.Result r) {
        String url = r.url == null || r.url.isEmpty() ? UpdateChecker.LATEST_PAGE : r.url;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(activity, activity.getString(R.string.browser_open_failed));
        }
    }
}
