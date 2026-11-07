package com.moco.comicsearch;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前台服务下载更新包。
 *
 * GitHub 的发布资源在国内是几十 KB/s 且经常中途被掐断，一次性下载 5MB 很容易拿到半截文件。
 * 这里改成：先探测一次拿到真实 CDN 地址和总大小，然后按 256KB 分块 Range 下载，
 * 单块失败只重试这一块，最后校验文件长度 + zip 结构 + PackageManager 能否解析。
 */
public class UpdateService extends Service {

    public static final String EXTRA_API = "api_url";
    public static final String EXTRA_URL = "browser_url";
    public static final String EXTRA_VERSION = "version";

    private static final String CHANNEL = "update_download";
    private static final int NOTIF_ID = 1001;

    private static final int CHUNK = 256 * 1024;
    private static final int MAX_FAIL = 10;

    private static final Pattern TOTAL = Pattern.compile("/\\s*(\\d+)\\s*$");

    private static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context ctx, String apiUrl, String browserUrl, String version) {
        Intent i = new Intent(ctx, UpdateService.class)
                .putExtra(EXTRA_API, apiUrl)
                .putExtra(EXTRA_URL, browserUrl)
                .putExtra(EXTRA_VERSION, version);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String apiUrl = intent == null ? "" : intent.getStringExtra(EXTRA_API);
        final String browserUrl = intent == null ? "" : intent.getStringExtra(EXTRA_URL);
        final String version = intent == null ? "" : intent.getStringExtra(EXTRA_VERSION);

        createChannel();
        startForeground(NOTIF_ID, progressNotification(
                getString(R.string.update_downloading), 0, 0, true));

        if (running) {
            return START_NOT_STICKY;
        }
        running = true;
        new Thread(() -> {
            try {
                download(apiUrl, browserUrl, version);
            } finally {
                running = false;
            }
        }, "update-download").start();
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------ 下载

    /** 探测结果：跟随跳转后的真实地址 + 文件总大小（未知为 0）。 */
    private static final class Probe {
        String url = "";
        long total;
    }

    private void download(String apiUrl, String browserUrl, String version) {
        File dir = new File(getCacheDir(), "update");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "JmViewer-" + (version.isEmpty() ? "latest" : version) + ".apk");
        // 旧包可能是不完整的，先清掉
        //noinspection ResultOfMethodCallIgnored
        out.delete();

        String error = null;
        try {
            String primary = first(apiUrl, browserUrl, browserAssetUrl(version));
            if (primary.isEmpty()) {
                throw new IOException(getString(R.string.update_no_apk));
            }

            Probe probe = probe(primary);
            if (probe.url.isEmpty()) {
                // 探测失败（网络抖动）：退回一次性下载
                singleShot(primary, out);
            } else {
                chunked(primary, probe, out);
            }

            if (probe.total > 0 && out.length() != probe.total) {
                throw new IOException("下载不完整 " + out.length() + "/" + probe.total);
            }
            if (!validApk(out)) {
                throw new IOException("安装包不完整（" + out.length() + " 字节）");
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }

        final String err = error;
        main.post(() -> {
            if (err == null) {
                onDownloaded(out, version);
            } else {
                onFailed(err, version);
            }
        });
    }

    /** 一次请求拿到跳转后的真实地址和总大小（api 域名只打一次，避免触发限流）。 */
    private Probe probe(String url) {
        return probe(url, true);
    }

    private Probe probe(String url, boolean allowJson) {
        Probe p = new Probe();
        HttpURLConnection conn = null;
        try {
            conn = open(url, "bytes=0-0", 20000);
            final int code = conn.getResponseCode();
            if (isJson(conn)) {
                // API 返回的是资产元信息：取真实下载地址再探一次
                String alt = readJsonUrl(conn);
                conn.disconnect();
                conn = null;
                if (allowJson && !alt.isEmpty() && !alt.equals(url)) {
                    return probe(alt, false);
                }
                return p;
            }
            if (code == 206) {
                p.total = parseTotal(conn.getHeaderField("Content-Range"));
            } else if (code == 200) {
                p.total = conn.getContentLengthLong();
            } else {
                return p;
            }
            p.url = conn.getURL().toString();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return p;
    }

    /** 分块下载：单块失败只补这一块。 */
    private void chunked(String origin, Probe probe, File out) throws IOException {
        String url = probe.url;
        long total = probe.total;
        long have = 0;
        int fails = 0;
        int lastPct = -1;
        long lastTick = 0;

        while (total <= 0 || have < total) {
            long end = have + CHUNK - 1;
            if (total > 0) {
                end = Math.min(end, total - 1);
            }
            long got;
            try {
                got = fetchRange(url, out, have, end);
            } catch (IOException e) {
                got = -2;
            }
            if (got <= 0) {
                if (got == -1) {
                    // 416：已经下完
                    break;
                }
                fails++;
                if (fails > MAX_FAIL) {
                    throw new IOException("下载中断 " + have + "/" + total);
                }
                try {
                    Thread.sleep(Math.min(4000L, 600L * fails));
                } catch (InterruptedException ignored) {
                }
                if (fails % 3 == 0) {
                    // 签名地址可能过期，重新探测
                    Probe again = probe(origin);
                    if (!again.url.isEmpty()) {
                        url = again.url;
                        if (again.total > 0) {
                            total = again.total;
                        }
                    }
                }
                continue;
            }
            fails = 0;
            have = out.length();
            long now = System.currentTimeMillis();
            if (total > 0 && now - lastTick > 300) {
                int pct = (int) (have * 100 / total);
                if (pct != lastPct) {
                    lastPct = pct;
                    lastTick = now;
                    updateProgress(pct, true);
                }
            }
            if (total <= 0) {
                break;
            }
        }
    }

    /**
     * 取 [start, end] 区间写入文件末尾。
     * 返回写入字节数；返回 -1 表示服务端说已超出范围（416）；返回 -2 由调用方当作失败。
     */
    private long fetchRange(String url, File out, long start, long end) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = open(url, "bytes=" + start + "-" + end, 45000);
            final int code = conn.getResponseCode();
            if (code == 416) {
                return -1;
            }
            if (isJson(conn)) {
                throw new IOException("接口返回元信息，不是安装包");
            }
            if (code != 206 && code != 200) {
                throw new IOException("HTTP " + code);
            }
            final boolean append = code == 206;
            final long want = end - start + 1;
            long got = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream os = new FileOutputStream(out, append)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    got += n;
                    if (append && got >= want) {
                        break;
                    }
                }
            }
            if (got <= 0) {
                return -2;
            }
            return got;
        } catch (IOException e) {
            throw e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 探测失败时的兜底：整段下载（能下多少算多少，靠后续校验判断）。 */
    private void singleShot(String url, File out) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = open(url, null, 60000);
            final int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                throw new IOException("HTTP " + code);
            }
            if (isJson(conn)) {
                throw new IOException("接口返回元信息，不是安装包");
            }
            try (InputStream in = conn.getInputStream();
                 OutputStream os = new FileOutputStream(out, false)) {
                byte[] buf = new byte[32768];
                int n;
                long done = 0;
                long total = conn.getContentLengthLong();
                long lastTick = 0;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (total > 0 && now - lastTick > 300) {
                        lastTick = now;
                        updateProgress((int) (done * 100 / total), true);
                    }
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private HttpURLConnection open(String url, String range, int readTimeout) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(readTimeout);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", "JmViewer/" + BuildConfig.VERSION_NAME);
        // 必须精确匹配：带上 ", */*" 时 GitHub 会返回 JSON 元信息而不是安装包
        conn.setRequestProperty("Accept", "application/octet-stream");
        // 关掉压缩，避免进度按压缩后字节算不准
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (range != null) {
            conn.setRequestProperty("Range", range);
        }
        return conn;
    }

    private static boolean isJson(HttpURLConnection conn) {
        String ct = conn.getContentType();
        return ct != null && ct.toLowerCase(Locale.ROOT).contains("json");
    }

    /** 从资产元信息里取真实下载地址。 */
    private static String readJsonUrl(HttpURLConnection conn) {
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0 && sb.length() < 65536) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            String body = sb.toString();
            int i = body.indexOf("\"browser_download_url\"");
            if (i < 0) {
                return "";
            }
            int q1 = body.indexOf('"', i + 22);
            int q2 = q1 < 0 ? -1 : body.indexOf('"', q1 + 1);
            return (q1 < 0 || q2 < 0) ? "" : body.substring(q1 + 1, q2);
        } catch (Exception e) {
            return "";
        }
    }

    private static long parseTotal(String contentRange) {
        if (contentRange == null) {
            return 0;
        }
        Matcher m = TOTAL.matcher(contentRange);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    /** 用 tag 拼出浏览器下载地址，作为 api 之外的兜底。 */
    private static String browserAssetUrl(String version) {
        if (version == null || version.isEmpty()) {
            return "";
        }
        String name = "JmViewer-v" + version + ".apk";
        return "https://github.com/f1uke76/JmViewer/releases/download/v" + version + "/" + name;
    }

    /** APK 校验：zip 头 + 末尾目录 + 系统能否解析包信息。 */
    private boolean validApk(File f) {
        if (!isApk(f)) {
            return false;
        }
        try {
            PackageInfo info = getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
            return info != null && info.packageName != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** APK 本质是 zip：校验文件头 + 末尾目录，半截文件会缺 EOCD。 */
    private boolean isApk(File f) {
        if (!f.isFile() || f.length() < 100 * 1024) {
            return false;
        }
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            byte[] head = new byte[4];
            raf.readFully(head);
            if (!(head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04)) {
                return false;
            }
            long len = raf.length();
            int tailLen = (int) Math.min(66000L, len);
            byte[] tail = new byte[tailLen];
            raf.seek(len - tailLen);
            raf.readFully(tail);
            for (int i = tailLen - 4; i >= 0; i--) {
                if (tail[i] == 0x50 && tail[i + 1] == 0x4B
                        && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateProgress(int pct, boolean determinate) {
        Notification n = determinate
                ? progressNotification(getString(R.string.update_downloading), pct, 100, false)
                : progressNotification(getString(R.string.update_downloading), 0, 0, true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_ID, n);
        }
    }

    // ------------------------------------------------------------ 结果

    private void onDownloaded(File apk, String version) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationCompat.Builder b = baseBuilder()
                .setContentTitle(getString(R.string.update_download_done))
                .setContentText(getString(R.string.update_tap_install))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(installPendingIntent(apk));
        if (nm != null) {
            nm.notify(NOTIF_ID, b.build());
        }
        stopForeground(false);
        stopSelf();

        Activity a = App.current();
        if (a != null) {
            new MaterialAlertDialogBuilder(a)
                    .setTitle(getString(R.string.update_download_done))
                    .setMessage(getString(R.string.update_tap_install))
                    .setPositiveButton(R.string.install_now, (d, w) -> installApk(a, apk))
                    .setNegativeButton(R.string.later, null)
                    .show();
        }
    }

    private void onFailed(String error, String version) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_ID, baseBuilder()
                    .setContentTitle(getString(R.string.update_download_failed))
                    .setContentText(error)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build());
        }
        stopForeground(false);
        stopSelf();

        Activity a = App.current();
        if (a == null) {
            return;
        }
        new MaterialAlertDialogBuilder(a)
                .setTitle(getString(R.string.update_download_failed))
                .setMessage(error)
                .setPositiveButton(R.string.retry, (d, w) -> {
                    UpdateChecker.Result r = UpdateChecker.lastResult;
                    String api = r == null ? "" : r.apkApiUrl;
                    String browser = r == null ? "" : r.apkUrl;
                    if ((browser == null || browser.isEmpty()) && version != null
                            && !version.isEmpty()) {
                        browser = browserAssetUrl(version);
                    }
                    start(a, api, browser, version);
                    Ui.toast(a, a.getString(R.string.update_downloading));
                })
                .setNeutralButton(R.string.update_on_github, (d, w) -> {
                    try {
                        String url = UpdateChecker.LATEST_PAGE;
                        UpdateChecker.Result r = UpdateChecker.lastResult;
                        if (r != null && r.url != null && !r.url.isEmpty()) {
                            url = r.url;
                        }
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private PendingIntent installPendingIntent(File apk) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(apkUri(apk), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, i, flags);
    }

    private Uri apkUri(File apk) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
    }

    private void installApk(Activity activity, File apk) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(apkUri(apk), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Exception e) {
            Ui.toast(activity, getString(R.string.update_install_failed));
        }
    }

    // ------------------------------------------------------------ 通知

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private NotificationCompat.Builder baseBuilder() {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private Notification progressNotification(String text, int pct, int max, boolean indeterminate) {
        return baseBuilder()
                .setContentTitle(getString(R.string.update_channel))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(max, pct, indeterminate)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
