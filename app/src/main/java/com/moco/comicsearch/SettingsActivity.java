package com.moco.comicsearch;

import android.os.Bundle;

/** 独立设置页（保留给外部入口）；主页内嵌的设置面板与它共用 SettingsPanel。 */
public class SettingsActivity extends SwipeBackActivity {

    private SettingsPanel panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setup(this);
        setContentView(R.layout.activity_settings);
        EdgeToEdge.padTop(findViewById(R.id.header));
        EdgeToEdge.padBottom(findViewById(R.id.root));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        panel = new SettingsPanel(this, findViewById(android.R.id.content), null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (panel != null) {
            panel.refresh();
        }
    }
}
