package com.moco.comicsearch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;

/** 设置页：外观、站点与分流、图标、缓存与记录。 */
public class SettingsActivity extends AppCompatActivity {

    private Site site;
    private Prefs prefs;
    private Store store;

    private TextView themeValue;
    private TextView viewModeValue;
    private TextView siteValue;
    private TextView iconValue;
    private TextView cacheValue;
    private TextView cacheManageValue;
    private com.google.android.material.materialswitch.MaterialSwitch swHomeRecommend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        site = Site.get(this);
        prefs = new Prefs(this);
        store = new Store(this);

        themeValue = findViewById(R.id.themeValue);
        viewModeValue = findViewById(R.id.viewModeValue);
        siteValue = findViewById(R.id.siteValue);
        iconValue = findViewById(R.id.iconValue);
        cacheValue = findViewById(R.id.cacheValue);
        cacheManageValue = findViewById(R.id.cacheManageValue);
        swHomeRecommend = findViewById(R.id.swHomeRecommend);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.rowTheme).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.rowViewMode).setOnClickListener(v -> showViewModeDialog());
        findViewById(R.id.rowSite).setOnClickListener(v -> showSiteDialog());
        findViewById(R.id.rowMirror).setOnClickListener(v -> MirrorPicker.show(this, this::applyMirror));
        findViewById(R.id.rowIcon).setOnClickListener(v -> showIconDialog());
        findViewById(R.id.rowClearCache).setOnClickListener(v -> clearCache());
        findViewById(R.id.rowCacheManage).setOnClickListener(v ->
                startActivity(new Intent(this, CacheActivity.class)));
        findViewById(R.id.rowHomeRecommend).setOnClickListener(v ->
                swHomeRecommend.setChecked(!swHomeRecommend.isChecked()));
        swHomeRecommend.setChecked(prefs.homeRecommend());
        swHomeRecommend.setOnCheckedChangeListener((b, checked) -> prefs.setHomeRecommend(checked));
        findViewById(R.id.rowClearSearch).setOnClickListener(v -> {
            prefs.clearHistory();
            Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.rowClearRead).setOnClickListener(v -> {
            store.clearReadHistory();
            Toast.makeText(this, R.string.read_history_cleared, Toast.LENGTH_SHORT).show();
        });

        updateValues();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateValues();
    }

    private void updateValues() {
        String[] themes = {
                getString(R.string.theme_follow),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        themeValue.setText(themes[Math.max(0, Math.min(prefs.getThemeMode(), themes.length - 1))]);

        viewModeValue.setText(prefs.getViewMode() == 1 ? R.string.view_grid : R.string.view_list);

        siteValue.setText(site.base().replace("https://", ""));

        String[] icons = {
                getString(R.string.icon_purple),
                getString(R.string.icon_dark),
                getString(R.string.icon_pink),
                getString(R.string.icon_blue)
        };
        int style = prefs.getIconStyle();
        iconValue.setText(icons[Math.max(0, Math.min(style, icons.length - 1))]);

        long size = dirSize(new File(getCacheDir(), "raw"));
        cacheValue.setText(String.format(Locale.ROOT, "%.1f MB", size / 1024f / 1024f));
        if (cacheManageValue != null) {
            cacheManageValue.setText(String.format(Locale.ROOT, "%.1f MB", size / 1024f / 1024f));
        }
        if (swHomeRecommend != null) {
            swHomeRecommend.setChecked(prefs.homeRecommend());
        }
    }

    private void showThemeDialog() {
        final String[] modes = {
                getString(R.string.theme_follow),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_mode)
                .setSingleChoiceItems(modes, prefs.getThemeMode(), (d, which) -> {
                    prefs.setThemeMode(which);
                    AppCompatDelegate.setDefaultNightMode(App.toNightMode(which));
                    updateValues();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showViewModeDialog() {
        final String[] modes = {
                getString(R.string.view_list),
                getString(R.string.view_grid)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.view_mode)
                .setSingleChoiceItems(modes, prefs.getViewMode(), (d, which) -> {
                    prefs.setViewMode(which);
                    updateValues();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSiteDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_settings, null, false);
        final EditText input = content.findViewById(R.id.inputBase);
        input.setText(site.base());
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
        content.findViewById(R.id.cbAdBlock).setVisibility(View.GONE);
        content.findViewById(R.id.btnClearHistory).setVisibility(View.GONE);
        content.findViewById(R.id.swWebMode).setVisibility(View.GONE);

        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.site_url)
                .setView(content)
                .setPositiveButton(R.string.save, (d, w) -> {
                    prefs.setBaseUrl(input.getText() == null ? "" : input.getText().toString());
                    updateValues();
                    Toast.makeText(this, site.base(), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.reset_default, (d, w) -> {
                    prefs.setBaseUrl(Prefs.DEFAULT_BASE_URL);
                    updateValues();
                    Toast.makeText(this, site.base(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        content.findViewById(R.id.btnMirrorDrop).setOnClickListener(v ->
                MirrorPicker.show(this, m -> {
                    dialog.dismiss();
                    applyMirror(m);
                }));

        dialog.show();
    }

    private void applyMirror(Models.Mirror m) {
        prefs.setBaseUrl("https://" + m.host);
        updateValues();
        Toast.makeText(this, getString(R.string.mirror_switched, m.host), Toast.LENGTH_SHORT).show();
    }

    private void showIconDialog() {
        final String[] styles = {
                getString(R.string.icon_purple),
                getString(R.string.icon_dark),
                getString(R.string.icon_pink),
                getString(R.string.icon_blue)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.icon_style)
                .setSingleChoiceItems(styles, prefs.getIconStyle(), (d, which) -> {
                    IconSwitcher.set(this, which, false);
                    updateValues();
                    Toast.makeText(this, R.string.icon_changed, Toast.LENGTH_LONG).show();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearCache() {
        deleteRecursive(new File(getCacheDir(), "raw"));
        ImageLoader.get(this).clearMemory();
        Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
        updateValues();
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long total = 0;
        for (File f : files) {
            total += f.isDirectory() ? dirSize(f) : f.length();
        }
        return total;
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] files = f.listFiles();
        if (files != null) {
            for (File c : files) {
                if (c.isDirectory()) {
                    deleteRecursive(c);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    c.delete();
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
