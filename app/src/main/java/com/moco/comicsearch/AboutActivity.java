package com.moco.comicsearch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 关于软件：版本、版本说明、检查更新、启动检查更新开关。 */
public class AboutActivity extends SwipeBackActivity {

    private Prefs prefs;
    private TextView versionValue;
    private TextView notesView;
    private View versionDot;
    private PillSwitch swAutoCheck;
    private boolean checking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setContentView(R.layout.activity_about);
        prefs = new Prefs(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        versionValue = findViewById(R.id.versionValue);
        versionDot = findViewById(R.id.versionDot);
        notesView = findViewById(R.id.notesView);
        swAutoCheck = findViewById(R.id.swAutoCheck);

        versionValue.setText(BuildConfig.VERSION_NAME);
        swAutoCheck.setChecked(prefs.autoCheckUpdate());
        swAutoCheck.setOnCheckedChangeListener((b, checked) -> prefs.setAutoCheckUpdate(checked));
        findViewById(R.id.rowAutoCheck).setOnClickListener(v ->
                swAutoCheck.setChecked(!swAutoCheck.isChecked(), true));
        findViewById(R.id.rowCheckUpdate).setOnClickListener(v -> checkUpdate(true));

        notesView.setText(readNotes());
        checkUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (swAutoCheck != null) {
            swAutoCheck.setChecked(prefs.autoCheckUpdate());
        }
    }

    private void checkUpdate(boolean manual) {
        if (checking) {
            return;
        }
        checking = true;
        if (manual) {
            Ui.toast(this, getString(R.string.checking_update));
        }
        UpdateChecker.check(this, r -> {
            checking = false;
            versionDot.setVisibility(UpdateChecker.hasUpdate() ? View.VISIBLE : View.GONE);
            if (r == null || !r.ok) {
                if (manual) {
                    Ui.toast(this, getString(R.string.check_update_failed));
                }
                return;
            }
            if (r.newer) {
                if (manual) {
                    showUpdateDialog(r);
                }
            } else if (manual) {
                Ui.toast(this, getString(R.string.up_to_date));
            }
        });
    }

    private void showUpdateDialog(UpdateChecker.Result r) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.update_available, "v" + r.version))
                .setMessage(getString(R.string.update_available_msg,
                        BuildConfig.VERSION_NAME, r.version))
                .setPositiveButton(R.string.go_update, (d, w) -> openUrl(r.url))
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.browser_open_failed));
        }
    }

    private String readNotes() {
        try (InputStream is = getAssets().open("README.md")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return stripMarkdown(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return getString(R.string.about_text);
        }
    }

    private static String stripMarkdown(String md) {
        if (md == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String raw : md.split("\n", -1)) {
            String line = raw.trim();
            if (line.equals("---") || line.equals("***")) {
                continue;
            }
            line = line.replaceAll("^#{1,6}\\s*", "");
            line = line.replaceAll("^>\\s*", "");
            line = line.replaceAll("^[-*+]\\s+", "· ");
            line = line.replace("**", "").replace("__", "").replace("`", "");
            line = line.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
            if (line.startsWith("|") || line.endsWith("|")) {
                line = line.replaceAll("^\\|", "").replaceAll("\\|$", "")
                        .replace("|", " · ").trim();
            }
            out.append(line).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
}
