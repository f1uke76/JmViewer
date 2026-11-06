package com.moco.comicsearch;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 前台服务下载更新包：通知栏显示进度，完成后可直接安装。 */
public class UpdateService extends Service {

    public static final String EXTRA_API = "api_url";
    public static final String EXTRA_URL = "browser_url";
    public static final String EXTRA_VERSION = "version";

    private static final String CHANNEL = "update_download";
    private static final int NOTIF_ID = 1001;
    private static final String API_TAG =
            "https://api.github.com/repos/f1uke76/JmViewer/releases/tags/v";

    private final Handler main = new Handler(Looper.getMainLooper());

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

        new Thread(() -> download(apiUrl, browserUrl, version), "update-download").start();
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------ 下载

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
            String url = apiUrl;
            if (url == null || url.isEmpty()) {
                url = browserUrl;
            }
            if (url == null || url.isEmpty()) {
                url = resolveAssetUrl(version);
            }
            if (url == null || url.isEmpty()) {
                throw new IOException(getString(R.string.update_no_apk));
            }

            // 断点续传 + 最多 4 次重试：GitHub 资源经常几十 KB/s 且会中途断开
            IOException last = null;
            for (int attempt = 1; attempt <= 4; attempt++) {
                try {
                    fetch(url, out);
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    if (attempt == 4) {
                        throw e;
                    }
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            if (last != null) {
                throw last;
            }
            if (!isApk(out)) {
                throw new IOException("安装包不完整");
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
                onFailed(err);
            }
        });
    }

    /** 通过 API 查 tag 对应的 apk 资产地址（api 域名通常比 github.com 稳定）。 */
    private String resolveAssetUrl(String version) {
        if (version == null || version.isEmpty()) {
            return "";
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_TAG + version).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "JmViewer/" + BuildConfig.VERSION_NAME);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            if (conn.getResponseCode() != 200) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject o = new JSONObject(sb.toString());
            JSONArray assets = o.optJSONArray("assets");
            if (assets == null) {
                return "";
            }
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name").endsWith(".apk")) {
                    String api = a.optString("url");
                    return api.isEmpty() ? a.optString("browser_download_url") : api;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return "";
    }

    /** 下载到文件；文件已存在时用 Range 续传。 */
    private void fetch(String url, File out) throws IOException {
        long have = out.exists() ? out.length() : 0;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        // GitHub 资源服务器在国内经常只有几十 KB/s，超时放宽，靠通知栏进度提示
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "JmViewer/" + BuildConfig.VERSION_NAME);
        conn.setRequestProperty("Accept", "application/octet-stream, */*");
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (have > 0) {
            conn.setRequestProperty("Range", "bytes=" + have + "-");
        }

        final int code = conn.getResponseCode();
        final boolean append;
        final long total;
        if (code == 206) {
            total = have + conn.getContentLengthLong();
            append = true;
        } else if (code == 200) {
            have = 0;
            total = conn.getContentLengthLong();
            append = false;
        } else {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }

        long done = have;
        int lastPct = -1;
        long lastTick = 0;
        try (InputStream in = conn.getInputStream();
             OutputStream os = new FileOutputStream(out, append)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - lastTick > 400) {
                    lastTick = now;
                    int pct = total > 0 ? (int) (done * 100 / total) : 0;
                    if (pct != lastPct) {
                        lastPct = pct;
                        updateProgress(pct, total > 0);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        if (total > 0 && done != total) {
            throw new IOException("下载不完整 " + done + "/" + total);
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

    private void onFailed(String error) {
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
        if (a != null) {
            Ui.toast(a, getString(R.string.update_download_failed) + "：" + error);
        }
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
