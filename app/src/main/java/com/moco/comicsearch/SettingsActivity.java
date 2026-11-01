package com.moco.comicsearch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 设置页：外观、站点与分流、缓存、条目导入导出、清除记录。 */
public class SettingsActivity extends SwipeBackActivity {

    private Site site;
    private Prefs prefs;
    private Store store;

    private TextView themeValue;
    private TextView viewModeValue;
    private TextView siteValue;
    private TextView cacheManageValue;
    private PillSwitch swHomeRecommend;

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> {
                        if (uri == null) {
                            return;
                        }
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os == null) {
                                throw new java.io.IOException("no stream");
                            }
                            os.write(buildExport().getBytes(StandardCharsets.UTF_8));
                            Toast.makeText(this, R.string.export_ok, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) {
                    return;
                }
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) {
                        throw new java.io.IOException("no stream");
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                    applyImport(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    Toast.makeText(this, R.string.import_ok, Toast.LENGTH_SHORT).show();
                    updateValues();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.import_fail, Toast.LENGTH_SHORT).show();
                }
            });

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
        cacheManageValue = findViewById(R.id.cacheManageValue);
        swHomeRecommend = findViewById(R.id.swHomeRecommend);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.rowTheme).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.rowViewMode).setOnClickListener(v -> showViewModeDialog());
        findViewById(R.id.rowSite).setOnClickListener(v -> showSiteDialog());
        findViewById(R.id.rowMirror).setOnClickListener(v -> MirrorPicker.show(this, this::applyMirror));
        findViewById(R.id.rowCacheManage).setOnClickListener(v ->
                startActivity(new Intent(this, CacheActivity.class)));
        findViewById(R.id.rowClearRecords).setOnClickListener(v -> showClearRecordsDialog());
        findViewById(R.id.rowExport).setOnClickListener(v ->
                exportLauncher.launch("JmViewer-entries.json"));
        findViewById(R.id.rowImport).setOnClickListener(v ->
                importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"}));

        findViewById(R.id.rowHomeRecommend).setOnClickListener(v ->
                swHomeRecommend.setChecked(!swHomeRecommend.isChecked(), true));
        swHomeRecommend.setChecked(prefs.homeRecommend());
        swHomeRecommend.setOnCheckedChangeListener((b, checked) -> prefs.setHomeRecommend(checked));

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

        long size = dirSize(new File(getCacheDir(), "raw"));
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

    // ------------------------------------------------------------ 清除记录

    private void showClearRecordsDialog() {
        final String[] items = {
                getString(R.string.clear_read_records),
                getString(R.string.clear_search_records)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_records)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        confirmClear(R.string.clear_read_records, R.string.confirm_clear_read, true);
                    } else {
                        confirmClear(R.string.clear_search_records, R.string.confirm_clear_search, false);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClear(int title, int message, boolean readHistory) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.remove, (d, w) -> {
                    if (readHistory) {
                        store.clearReadHistory();
                        Toast.makeText(this, R.string.read_history_cleared, Toast.LENGTH_SHORT).show();
                    } else {
                        prefs.clearHistory();
                        Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------ 条目导入导出

    private String buildExport() {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "JmViewer");
            root.put("version", 1);
            root.put("favorites", new JSONArray(store.rawFavorites()));
            root.put("readHistory", new JSONArray(store.rawReadHistory()));
            root.put("searchHistory", new JSONArray(prefs.rawSearchHistory()));
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void applyImport(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.has("favorites")) {
            store.putRawFavorites(root.getJSONArray("favorites").toString());
        }
        if (root.has("readHistory")) {
            store.putRawReadHistory(root.getJSONArray("readHistory").toString());
        }
        if (root.has("searchHistory")) {
            prefs.putRawSearchHistory(root.getJSONArray("searchHistory").toString());
        }
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
}
