package com.moco.comicsearch;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 站点网络访问层。Cookie 与 WebView 共用一套（android.webkit.CookieManager），
 * 这样网页登录和接口登录的登录态是互通的。
 */
public class Site {

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Mobile Safari/537.36";

    /** 官方发布页，列出各分流线路。 */
    public static final String OFFICIAL_PAGE = "https://jmcomictt.site";

    private static Site instance;

    private final OkHttpClient client;
    private final OkHttpClient quickClient;
    private final Prefs prefs;

    private Site(Context ctx) {
        prefs = new Prefs(ctx);
        client = new OkHttpClient.Builder()
                .cookieJar(new WebCookieJar())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        // 探测分流用，短超时，避免个别线路卡住整个流程
        quickClient = client.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized Site get(Context ctx) {
        if (instance == null) {
            instance = new Site(ctx.getApplicationContext());
        }
        return instance;
    }

    public Prefs prefs() {
        return prefs;
    }

    public String base() {
        return prefs.getBaseUrl();
    }

    public String absolute(String path) {
        if (path == null) {
            return base();
        }
        if (path.startsWith("http")) {
            return path;
        }
        return path.startsWith("/") ? base() + path : base() + "/" + path;
    }

    // ------------------------------------------------------------ 请求

    public String get(String path) throws IOException {
        return getUrl(absolute(path));
    }

    public String getUrl(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", base() + "/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.body() == null) {
                return "";
            }
            return resp.body().string();
        }
    }

    public byte[] getBytes(String url) throws IOException {
        return getBytes(url, null);
    }

    /** 带下载进度的取字节（进度回调在调用线程上触发）。 */
    public byte[] getBytes(String url, ProgressListener listener) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", base() + "/")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("HTTP " + resp.code());
            }
            long total = resp.body().contentLength();
            java.io.InputStream in = resp.body().byteStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long read = 0;
            int last = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0 && listener != null) {
                    int pct = (int) (read * 100 / total);
                    if (pct >= last + 2 || pct == 100) {
                        last = pct;
                        listener.onProgress(Math.min(pct, 100));
                    }
                }
            }
            return out.toByteArray();
        }
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    private String post(String path, Map<String, String> form) throws IOException {
        FormBody.Builder b = new FormBody.Builder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            b.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        RequestBody body = b.build();
        Request req = new Request.Builder()
                .url(absolute(path))
                .header("User-Agent", UA)
                .header("Referer", base() + "/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .post(body)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.body() == null) {
                return "";
            }
            return resp.body().string();
        }
    }

    // ------------------------------------------------------------ 业务

    public String searchUrl(String keyword, int page) {
        String q = encode(keyword);
        String url = base() + "/search/photos?search_query=" + q;
        if (page > 1) {
            url += "&page=" + page;
        }
        return url;
    }

    public static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    public JSONObject login(String username, String password) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("username", username);
        form.put("password", password);
        form.put("id_remember", "1");
        form.put("login_remember", "1");
        form.put("submit_login", "1");
        String body = post("/login", form);
        return new JSONObject(body);
    }

    public void logout() {
        try {
            post("/logout", new HashMap<>());
        } catch (Exception ignored) {
        }
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Exception ignored) {
        }
    }

    /** 收藏 / 取消收藏，返回站点 JSON。 */
    public JSONObject favorite(String albumId, boolean add) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("album_id", albumId);
        if (add) {
            form.put("fid", "0");
            return new JSONObject(post("/ajax/favorite_album", form));
        }
        return new JSONObject(post("/ajax/delete_favorite_album", form));
    }

    /** 通过主页是否出现「登出」链接判断登录态。 */
    public boolean checkLoggedIn() {
        try {
            String html = get("/");
            return html.contains("/logout");
        } catch (Exception e) {
            return false;
        }
    }

    /** 抓取官方发布页（内含各分流线路）。 */
    public String getOfficialPage() throws IOException {
        final String page = prefs.getOfficialPage();
        Request req = new Request.Builder()
                .url(page)
                .header("User-Agent", UA)
                .header("Referer", page + "/")
                .build();
        try (Response resp = quickClient.newCall(req).execute()) {
            if (resp.body() == null) {
                return "";
            }
            return resp.body().string();
        }
    }

    /** 探测某个域名是否可访问（用于标注分流可用性）。 */
    public boolean probeHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        try {
            Request req = new Request.Builder()
                    .url("https://" + host + "/")
                    .header("User-Agent", UA)
                    .build();
            try (Response resp = quickClient.newCall(req).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** 并行探测多条线路，整体最多等 8 秒。 */
    public void probeAll(java.util.List<Models.Mirror> mirrors) {
        if (mirrors == null || mirrors.isEmpty()) {
            return;
        }
        int n = mirrors.size();
        java.util.concurrent.ExecutorService ex =
                java.util.concurrent.Executors.newFixedThreadPool(Math.min(8, n));
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(n);
        for (final Models.Mirror m : mirrors) {
            ex.execute(() -> {
                try {
                    m.reachable = probeHost(m.host);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        ex.shutdownNow();
    }

    public void flushCookies() {
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ Cookie

    private static class WebCookieJar implements CookieJar {

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            try {
                CookieManager cm = CookieManager.getInstance();
                for (Cookie c : cookies) {
                    cm.setCookie(url.toString(), c.toString());
                }
                cm.flush();
            } catch (Exception ignored) {
            }
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> out = new ArrayList<>();
            try {
                String raw = CookieManager.getInstance().getCookie(url.toString());
                if (raw == null || raw.isEmpty()) {
                    return out;
                }
                for (String part : raw.split(";")) {
                    Cookie c = Cookie.parse(url, part.trim());
                    if (c != null) {
                        out.add(c);
                    }
                }
            } catch (Exception ignored) {
            }
            return out;
        }
    }
}
