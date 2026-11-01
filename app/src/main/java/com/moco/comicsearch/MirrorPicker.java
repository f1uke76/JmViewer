package com.moco.comicsearch;

import android.app.Activity;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 读取官方发布页 → 并行探测可用性 → 弹出分流选择。 */
public final class MirrorPicker {

    public interface OnPick {
        void onPick(Models.Mirror mirror);
    }

    private MirrorPicker() {
    }

    public static void show(final Activity activity, final OnPick onPick) {
        final Site site = Site.get(activity);
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        final AlertDialog loading = new MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.loading_official)
                .setCancelable(true)
                .create();
        loading.setCanceledOnTouchOutside(true);
        loading.setOnCancelListener(d -> cancelled.set(true));
        loading.show();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            List<Models.Mirror> mirrors = new ArrayList<>();
            String err = null;
            try {
                mirrors = Parsers.parseMirrors(site.getOfficialPage());
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            if (cancelled.get()) {
                return;
            }
            // 并行探测，避免逐条等待
            site.probeAll(mirrors);
            if (cancelled.get()) {
                return;
            }
            final List<Models.Mirror> data = mirrors;
            final String msg = err;
            activity.runOnUiThread(() -> afterLoad(activity, loading, data, msg, cancelled, onPick));
        });
        pool.shutdown();
    }

    private static void afterLoad(Activity activity, AlertDialog loading, List<Models.Mirror> data,
                                  String msg, AtomicBoolean cancelled, OnPick onPick) {
        if (cancelled.get()) {
            return;
        }
        try {
            loading.dismiss();
        } catch (Exception ignored) {
        }
        if (data.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.official_page)
                    .setMessage(msg == null ? activity.getString(R.string.no_mirror) : msg)
                    .setPositiveButton(R.string.cancel, null)
                    .show();
            return;
        }
        showList(activity, data, onPick);
    }

    private static void showList(Activity activity, List<Models.Mirror> data, OnPick onPick) {
        String currentHost = "";
        try {
            currentHost = Uri.parse(Site.get(activity).base()).getHost();
        } catch (Exception ignored) {
        }

        final String[] rows = new String[data.size()];
        final int[] selected = {-1};
        for (int i = 0; i < data.size(); i++) {
            Models.Mirror m = data.get(i);
            String mark = m.reachable ? "✅" : "❌";
            StringBuilder sb = new StringBuilder(mark).append("  ").append(m.host);
            if (!m.label.isEmpty()) {
                sb.append("  ·  ").append(m.label);
            }
            rows[i] = sb.toString();
            if (currentHost != null && m.host.equalsIgnoreCase(currentHost)) {
                selected[0] = i;
            }
        }

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return data.size();
            }

            @Override
            public Object getItem(int position) {
                return data.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_mirror, parent, false);
                RadioButton radio = v.findViewById(R.id.mirrorRadio);
                TextView label = v.findViewById(R.id.mirrorLabel);
                radio.setChecked(position == selected[0]);
                label.setText(rows[position]);
                return v;
            }
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.pick_mirror_title)
                .setAdapter(adapter, (d, which) -> {
                    selected[0] = which;
                    if (onPick != null) {
                        onPick.onPick(data.get(which));
                    }
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
