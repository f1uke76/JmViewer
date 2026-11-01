package com.moco.comicsearch;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 导入与导出：收藏、阅读历史、搜索记录统一打包成 JSON。 */
public class ImportExportActivity extends SwipeBackActivity {

    private Store store;
    private Prefs prefs;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setContentView(R.layout.activity_import_export);
        store = new Store(this);
        prefs = new Prefs(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), uri -> {
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

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
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
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.import_fail, Toast.LENGTH_SHORT).show();
                    }
                });

        findViewById(R.id.rowExport).setOnClickListener(v ->
                exportLauncher.launch("JmViewer-entries.json"));
        findViewById(R.id.rowImport).setOnClickListener(v ->
                importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"}));
    }

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
}
