package com.moco.comicsearch;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;

/** 设置面板：可内嵌在主页的「设置」分区，也可被独立设置页复用。 */
public class SettingsPanel {

    public interface Listener {
        void onSettingsChanged();
    }

    private final androidx.appcompat.app.AppCompatActivity activity;
    private final Site site;
    private final Prefs prefs;
    private final Store store;
    private final Listener listener;

    private final TextView themeValue;
    private final TextView accentValue;
    private final TextView viewModeValue;
    private final TextView siteValue;
    private final TextView cacheManageValue;
    private final TextView aboutVersionValue;
    private final View aboutDot;
    private final PillSwitch swHomeRecommend;
    private boolean updating;

    public SettingsPanel(androidx.appcompat.app.AppCompatActivity activity, View root, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.site = Site.get(activity);
        this.prefs = new Prefs(activity);
        this.store = new Store(activity);

        themeValue = root.findViewById(R.id.themeValue);
        accentValue = root.findViewById(R.id.accentValue);
        viewModeValue = root.findViewById(R.id.viewModeValue);
        siteValue = root.findViewById(R.id.siteValue);
        cacheManageValue = root.findViewById(R.id.cacheManageValue);
        aboutVersionValue = root.findViewById(R.id.aboutVersionValue);
        aboutDot = root.findViewById(R.id.aboutDot);
        swHomeRecommend = root.findViewById(R.id.swHomeRecommend);

        root.findViewById(R.id.rowTheme).setOnClickListener(v -> showThemeDialog());
        root.findViewById(R.id.rowAccent).setOnClickListener(v -> showAccentDialog());
        root.findViewById(R.id.rowViewMode).setOnClickListener(v -> showViewModeDialog());
        root.findViewById(R.id.rowSite).setOnClickListener(v -> showSiteDialog());
        root.findViewById(R.id.btnMirror).setOnClickListener(v -> MirrorPicker.show(activity, this::applyMirror));
        root.findViewById(R.id.rowCacheManage).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, CacheActivity.class)));
        root.findViewById(R.id.rowClearRecords).setOnClickListener(v -> showClearRecordsDialog());
        root.findViewById(R.id.rowImportExport).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, ImportExportActivity.class)));
        root.findViewById(R.id.rowAbout).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, AboutActivity.class)));

        root.findViewById(R.id.rowHomeRecommend).setOnClickListener(v ->
                swHomeRecommend.setChecked(!swHomeRecommend.isChecked(), true));
        swHomeRecommend.setOnCheckedChangeListener((b, checked) -> {
            if (updating) {
                return;
            }
            prefs.setHomeRecommend(checked);
            if (listener != null) {
                listener.onSettingsChanged();
            }
        });

        refresh();
    }

    public void refresh() {
        String[] themes = {
                activity.getString(R.string.theme_follow),
                activity.getString(R.string.theme_light),
                activity.getString(R.string.theme_dark)
        };
        themeValue.setText(themes[Math.max(0, Math.min(prefs.getThemeMode(), themes.length - 1))]);

        String[] accents = {
                activity.getString(R.string.accent_auto),
                activity.getString(R.string.accent_blue),
                activity.getString(R.string.accent_green),
                activity.getString(R.string.accent_purple),
                activity.getString(R.string.accent_orange),
                activity.getString(R.string.accent_teal)
        };
        accentValue.setText(accents[Math.max(0, Math.min(prefs.getThemeAccent(), accents.length - 1))]);

        viewModeValue.setText(prefs.getViewMode() == 1 ? R.string.view_grid : R.string.view_list);
        siteValue.setText(site.base().replace("https://", ""));

        long size = dirSize(new File(activity.getCacheDir(), "raw"));
        if (cacheManageValue != null) {
            cacheManageValue.setText(String.format(Locale.ROOT, "%.1f MB", size / 1024f / 1024f));
        }
        if (aboutVersionValue != null) {
            aboutVersionValue.setText(BuildConfig.VERSION_NAME);
        }
        if (aboutDot != null) {
            aboutDot.setVisibility(UpdateChecker.hasUpdate() ? View.VISIBLE : View.GONE);
        }
        if (swHomeRecommend != null) {
            updating = true;
            swHomeRecommend.setChecked(prefs.homeRecommend());
            updating = false;
        }
    }

    private void showThemeDialog() {
        final String[] modes = {
                activity.getString(R.string.theme_follow),
                activity.getString(R.string.theme_light),
                activity.getString(R.string.theme_dark)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.theme_mode)
                .setSingleChoiceItems(modes, prefs.getThemeMode(), (d, which) -> {
                    prefs.setThemeMode(which);
                    AppCompatDelegate.setDefaultNightMode(App.toNightMode(which));
                    refresh();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAccentDialog() {
        final String[] accents = {
                activity.getString(R.string.accent_auto),
                activity.getString(R.string.accent_blue),
                activity.getString(R.string.accent_green),
                activity.getString(R.string.accent_purple),
                activity.getString(R.string.accent_orange),
                activity.getString(R.string.accent_teal)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.theme_accent)
                .setSingleChoiceItems(accents, prefs.getThemeAccent(), (d, which) -> {
                    prefs.setThemeAccent(which);
                    d.dismiss();
                    activity.recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showViewModeDialog() {
        final String[] modes = {
                activity.getString(R.string.view_list),
                activity.getString(R.string.view_grid)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.view_mode)
                .setSingleChoiceItems(modes, prefs.getViewMode(), (d, which) -> {
                    prefs.setViewMode(which);
                    refresh();
                    if (listener != null) {
                        listener.onSettingsChanged();
                    }
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSiteDialog() {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_settings, null, false);
        final EditText input = content.findViewById(R.id.inputBase);
        input.setText(site.base());
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
        content.findViewById(R.id.cbAdBlock).setVisibility(View.GONE);
        content.findViewById(R.id.btnClearHistory).setVisibility(View.GONE);
        content.findViewById(R.id.rowWebMode).setVisibility(View.GONE);
        content.findViewById(R.id.btnMirrorDrop).setVisibility(View.GONE);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.site_url)
                .setView(content)
                .setPositiveButton(R.string.save, (d, w) -> {
                    prefs.setBaseUrl(input.getText() == null ? "" : input.getText().toString());
                    refresh();
                    Toast.makeText(activity, site.base(), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.reset_default, (d, w) -> {
                    prefs.setBaseUrl(Prefs.DEFAULT_BASE_URL);
                    refresh();
                    Toast.makeText(activity, site.base(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyMirror(Models.Mirror m) {
        prefs.setBaseUrl("https://" + m.host);
        refresh();
        Toast.makeText(activity, activity.getString(R.string.mirror_switched, m.host),
                Toast.LENGTH_SHORT).show();
        if (listener != null) {
            listener.onSettingsChanged();
        }
    }

    private void showClearRecordsDialog() {
        final String[] items = {
                activity.getString(R.string.clear_read_records),
                activity.getString(R.string.clear_search_records)
        };
        new MaterialAlertDialogBuilder(activity)
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
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.remove, (d, w) -> {
                    if (readHistory) {
                        store.clearReadHistory();
                        Toast.makeText(activity, R.string.read_history_cleared, Toast.LENGTH_SHORT).show();
                    } else {
                        prefs.clearHistory();
                        Toast.makeText(activity, R.string.history_cleared, Toast.LENGTH_SHORT).show();
                    }
                    if (listener != null) {
                        listener.onSettingsChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
