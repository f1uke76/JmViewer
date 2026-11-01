package com.moco.comicsearch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 通过 GitHub Release 检测新版本。API 不可用时回退到 releases 页面。 */
public class UpdateChecker {

    public static final String API =
            "https://api.github.com/repos/haowen76/JmViewer/releases/latest";
    public static final String LATEST_PAGE =
            "https://github.com/haowen76/JmViewer/releases/latest";

    public static class Result {
        public boolean ok;
        public boolean newer;
        public int code;
        public String version = "";
        public String url = "";
        public String notes = "";
    }

    public interface Callback {
        void onResult(Result result);
    }

    private static volatile Result latest;
    /** 已经弹过更新提示的版本，避免反复打扰。 */
    public static volatile String promptedVersion = "";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static Result latest() {
        return latest;
    }

    public static boolean hasUpdate() {
        Result r = latest;
        return r != null && r.ok && r.newer;
    }

    public static void check(Context ctx, Callback cb) {
        new Thread(() -> {
            Result r = fetchApi();
            if (!r.ok) {
                Result html = fetchHtml();
                if (html.ok) {
                    r = html;
                }
            }
            final Result result = r;
            latest = result;
            MAIN.post(() -> cb.onResult(result));
        }, "update-check").start();
    }

    private static Result fetchApi() {
        Result r = new Result();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "JmViewer/" + BuildConfig.VERSION_NAME);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = conn.getResponseCode();
            r.code = code;
            if (code == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject o = new JSONObject(sb.toString());
                r.version = trimV(o.optString("tag_name"));
                r.url = o.optString("html_url");
                r.notes = o.optString("body");
                r.ok = !r.version.isEmpty();
                r.newer = r.ok && compare(r.version, BuildConfig.VERSION_NAME) > 0;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return r;
    }

    /** API 被限流 / 被墙时，从 releases/latest 跳转后的 /tag/xxx 地址取版本号。 */
    private static Result fetchHtml() {
        Result r = new Result();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LATEST_PAGE).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) JmViewer/" + BuildConfig.VERSION_NAME);
            r.code = conn.getResponseCode();
            String finalUrl = conn.getURL().toString();
            Matcher m = Pattern.compile("/releases/tag/([^/?#]+)").matcher(finalUrl);
            if (m.find()) {
                r.version = trimV(m.group(1));
                r.url = finalUrl;
                r.ok = !r.version.isEmpty();
                r.newer = r.ok && compare(r.version, BuildConfig.VERSION_NAME) > 0;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return r;
    }

    public static String trimV(String v) {
        if (v == null) {
            return "";
        }
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v;
    }

    /** 返回 a 相对 b 的大小：a>b 返回 1，相等 0，小于 -1。 */
    public static int compare(String a, String b) {
        String[] pa = trimV(a).split("\\.");
        String[] pb = trimV(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? num(pa[i]) : 0;
            int y = i < pb.length ? num(pb[i]) : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
