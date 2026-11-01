package com.moco.comicsearch;

import android.os.Bundle;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 版本说明：展示打包进 APK 的 README。 */
public class ReleaseNotesActivity extends SwipeBackActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setContentView(R.layout.activity_release_notes);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        TextView notesView = findViewById(R.id.notesView);
        notesView.setText(readNotes());
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
