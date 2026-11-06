package com.moco.comicsearch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


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

    /** 检查在应用级跑，退出本页也不影响；结果会回到当前页面弹窗。 */
    private void checkUpdate(boolean manual) {
        if (manual) {
            Ui.toast(this, getString(R.string.checking_update));
        }
        UpdateManager.check(this,
                manual ? UpdateManager.MODE_MANUAL : UpdateManager.MODE_SILENT,
                r -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    versionDot.setVisibility(UpdateChecker.hasUpdate()
                            ? View.VISIBLE : View.GONE);
                });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.browser_open_failed));
        }
    }
}
