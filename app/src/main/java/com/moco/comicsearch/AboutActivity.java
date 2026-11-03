package com.moco.comicsearch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 关于软件：版本、版本说明入口、检查更新、启动检查更新开关。 */
public class AboutActivity extends SwipeBackActivity {

    private Prefs prefs;
    private TextView versionValue;
    private View versionDot;
    private PillSwitch swAutoCheck;
    private boolean checking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setup(this);
        setContentView(R.layout.activity_about);
        EdgeToEdge.padTop(findViewById(R.id.header));
        EdgeToEdge.padBottom(findViewById(R.id.root));
        prefs = new Prefs(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        versionValue = findViewById(R.id.versionValue);
        versionDot = findViewById(R.id.versionDot);
        swAutoCheck = findViewById(R.id.swAutoCheck);

        versionValue.setText(BuildConfig.VERSION_NAME);
        swAutoCheck.setChecked(prefs.autoCheckUpdate());
        swAutoCheck.setOnCheckedChangeListener((b, checked) -> prefs.setAutoCheckUpdate(checked));
        findViewById(R.id.rowAutoCheck).setOnClickListener(v ->
                swAutoCheck.setChecked(!swAutoCheck.isChecked(), true));
        findViewById(R.id.rowCheckUpdate).setOnClickListener(v -> checkUpdate(true));
        findViewById(R.id.rowReleaseNotes).setOnClickListener(v ->
                startActivity(new Intent(this, ReleaseNotesActivity.class)));
        findViewById(R.id.rowGithub).setOnClickListener(v ->
                openUrl("https://github.com/f1uke76/JmViewer"));

        checkUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (swAutoCheck != null) {
            swAutoCheck.setChecked(prefs.autoCheckUpdate());
        }
    }

    private int checkSeq;

    private void checkUpdate(boolean manual) {
        final int seq = ++checkSeq;
        if (manual) {
            Ui.toast(this, getString(R.string.checking_update));
        }
        final boolean isManual = manual;
        UpdateChecker.check(this, r -> {
            if (seq != checkSeq) {
                return;
            }
            versionDot.setVisibility(UpdateChecker.hasUpdate() ? View.VISIBLE : View.GONE);
            if (r == null || !r.ok) {
                if (isManual) {
                    Ui.toast(this, getString(r != null && r.code == 404
                            ? R.string.update_repo_private
                            : R.string.check_update_failed));
                }
                return;
            }
            if (r.newer) {
                if (isManual) {
                    showUpdateDialog(r);
                }
            } else if (isManual) {
                Ui.toast(this, getString(R.string.up_to_date));
            }
        });
        if (manual) {
            // 兜底：30 秒仍未返回就提示失败，避免看起来没反应
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (seq == checkSeq) {
                    checkSeq++;
                    Ui.toast(this, getString(R.string.check_update_failed));
                }
            }, 30000);
        }
    }

    private void showUpdateDialog(final UpdateChecker.Result r) {
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.isEmpty()) {
            notes = getString(R.string.update_available_msg, BuildConfig.VERSION_NAME, r.version);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.update_available, "v" + r.version))
                .setMessage(notes)
                .setPositiveButton(R.string.go_update,
                        (d, w) -> downloadAndInstall(r.apkUrl))
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private void downloadAndInstall(final String url) {
        if (url == null || url.isEmpty()) {
            Ui.toast(this, getString(R.string.update_no_apk));
            return;
        }
        Ui.toast(this, getString(R.string.update_downloading));
        new Thread(() -> {
            File out = new File(getCacheDir(), "update.apk");
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "JmViewer/" + BuildConfig.VERSION_NAME);
                try (InputStream in = conn.getInputStream();
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                }
                runOnUiThread(() -> installApk(out));
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(AboutActivity.this,
                        getString(R.string.update_download_failed)));
            }
        }).start();
    }

    private void installApk(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.update_install_failed));
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.browser_open_failed));
        }
    }
}
