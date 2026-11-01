package com.moco.comicsearch;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 后台整本下载：逐页下载并解扰，全部完成后打包成 zip 图档包。
 * 支持开始 / 暂停，进度持久化，下载页可随时查看。
 */
public class DownloadManager {

    public static class Task {
        public String albumId = "";
        public String title = "";
        public String cover = "";
        public String chapterId = "";
        public String zipPath = "";
        public int total = 0;
        public int done = 0;
        public int state = 0; // 0 待开始 / 1 下载中 / 2 暂停 / 3 完成

        public int progress() {
            return total <= 0 ? 0 : Math.min(100, done * 100 / total);
        }
    }

    public interface Listener {
        void onChanged();
    }

    private static DownloadManager instance;

    private final Context ctx;
    private final Site site;
    private final SharedPreferences sp;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> pauseFlags = new ConcurrentHashMap<>();
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private DownloadManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.site = Site.get(ctx);
        this.sp = ctx.getApplicationContext()
                .getSharedPreferences("comic_downloads", Context.MODE_PRIVATE);
    }

    public static synchronized DownloadManager get(Context ctx) {
        if (instance == null) {
            instance = new DownloadManager(ctx);
        }
        return instance;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        main.post(() -> {
            for (Listener l : listeners) {
                l.onChanged();
            }
        });
    }

    public List<Task> tasks() {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("tasks", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Task t = new Task();
                t.albumId = o.optString("albumId");
                t.title = o.optString("title");
                t.cover = o.optString("cover");
                t.chapterId = o.optString("chapterId");
                t.zipPath = o.optString("zipPath");
                t.total = o.optInt("total");
                t.done = o.optInt("done");
                t.state = o.optInt("state");
                if (!t.albumId.isEmpty()) {
                    out.add(t);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void save(List<Task> tasks) {
        JSONArray arr = new JSONArray();
        for (Task t : tasks) {
            try {
                JSONObject o = new JSONObject();
                o.put("albumId", t.albumId);
                o.put("title", t.title);
                o.put("cover", t.cover);
                o.put("chapterId", t.chapterId);
                o.put("zipPath", t.zipPath);
                o.put("total", t.total);
                o.put("done", t.done);
                o.put("state", t.state);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString("tasks", arr.toString()).apply();
    }

    public Task get(String albumId) {
        for (Task t : tasks()) {
            if (t.albumId.equals(albumId)) {
                return t;
            }
        }
        return null;
    }

    private void update(Task task) {
        List<Task> all = tasks();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).albumId.equals(task.albumId)) {
                all.set(i, task);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(0, task);
        }
        save(all);
        notifyChanged();
    }

    public void enqueue(Models.Album album, String chapterId) {
        if (album == null || album.id == null || album.id.isEmpty()) {
            return;
        }
        List<Task> all = tasks();
        Task t = null;
        for (Task x : all) {
            if (x.albumId.equals(album.id)) {
                t = x;
                break;
            }
        }
        if (t == null) {
            t = new Task();
            t.albumId = album.id;
            all.add(0, t);
        }
        t.title = album.title == null ? "" : album.title;
        t.cover = album.cover == null ? "" : album.cover;
        if (chapterId != null && !chapterId.isEmpty()) {
            t.chapterId = chapterId;
        }
        if (t.state != 1) {
            t.state = 0;
        }
        save(all);
        notifyChanged();
    }

    public void start(String albumId) {
        pauseFlags.put(albumId, false);
        if (Boolean.TRUE.equals(running.get(albumId))) {
            return;
        }
        Task t = get(albumId);
        if (t == null || t.state == 3) {
            return;
        }
        t.state = 1;
        update(t);
        running.put(albumId, true);
        pool.execute(() -> run(t));
    }

    public void pause(String albumId) {
        pauseFlags.put(albumId, true);
        Task t = get(albumId);
        if (t != null && t.state == 1) {
            t.state = 2;
            update(t);
        }
    }

    public void remove(String albumId) {
        pauseFlags.put(albumId, true);
        Task t = get(albumId);
        if (t != null && t.zipPath != null && !t.zipPath.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            new File(t.zipPath).delete();
        }
        List<Task> all = tasks();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).albumId.equals(albumId)) {
                all.remove(i);
                break;
            }
        }
        save(all);
        notifyChanged();
    }

    private void run(Task t) {
        try {
            String chapterId = t.chapterId == null ? "" : t.chapterId;
            if (chapterId.isEmpty()) {
                Models.Album a = Parsers.parseAlbum(site.get("/album/" + t.albumId), site.base());
                chapterId = a.readId;
                if (chapterId.isEmpty() && !a.chapters.isEmpty()) {
                    chapterId = a.chapters.get(0).id;
                }
                t.chapterId = chapterId;
                if (t.title.isEmpty()) {
                    t.title = a.title;
                }
                if (t.cover.isEmpty()) {
                    t.cover = a.cover;
                }
                update(t);
            }
            if (chapterId.isEmpty()) {
                throw new IOException("no chapter");
            }
            Models.ChapterContent content =
                    Parsers.parseChapter(site.get("/photo/" + chapterId), site.base());
            if (content.aid.isEmpty()) {
                content.aid = chapterId;
            }
            t.total = content.pages.size();
            update(t);

            // 把详情元信息与章节信息写入缓存，详情页 / 阅读器可离线命中
            Store store = new Store(ctx);
            Models.Album album = new Models.Album();
            album.id = t.albumId;
            album.title = t.title;
            album.cover = t.cover;
            album.readId = chapterId;
            Models.Chapter ch = new Models.Chapter();
            ch.id = chapterId;
            ch.name = "开始阅读";
            album.chapters.add(ch);
            store.saveAlbum(album);
            store.saveChapter(chapterId, content);

            File tmp = new File(ctx.getCacheDir(), "dl_" + t.albumId);
            //noinspection ResultOfMethodCallIgnored
            tmp.mkdirs();

            for (int i = 0; i < content.pages.size(); i++) {
                if (Boolean.TRUE.equals(pauseFlags.get(t.albumId))) {
                    t.state = 2;
                    update(t);
                    return;
                }
                Models.PageImage p = content.pages.get(i);
                File out = new File(tmp, String.format(Locale.ROOT, "%05d.jpg", i + 1));
                if (!out.exists() || out.length() == 0) {
                    byte[] data = site.getBytes(p.url);
                    // 同步写入图片缓存，详情页 / 阅读器可直接命中
                    File rawDir = new File(ctx.getCacheDir(), "raw");
                    //noinspection ResultOfMethodCallIgnored
                    rawDir.mkdirs();
                    File rawFile = new File(rawDir, content.aid + "_" + p.name);
                    if (!rawFile.exists() || rawFile.length() == 0) {
                        try (FileOutputStream fos = new FileOutputStream(rawFile)) {
                            fos.write(data);
                        }
                    }
                    Bitmap bmp = Scramble.decode(data, content.aid, p.name, content.scrambleId);
                    if (bmp != null) {
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        }
                        bmp.recycle();
                    }
                }
                t.done = i + 1;
                update(t);
            }

            File zip = new File(downloadDir(), "JmViewer_" + t.albumId + ".zip");
            zipDir(tmp, zip, t);
            t.zipPath = zip.getAbsolutePath();
            t.state = 3;
            update(t);
            deleteRecursive(tmp);
        } catch (Exception e) {
            t.state = 2;
            update(t);
        } finally {
            running.remove(t.albumId);
            notifyChanged();
        }
    }

    private File downloadDir() {
        File dir = ctx.getExternalFilesDir("downloads");
        if (dir == null) {
            dir = new File(ctx.getFilesDir(), "downloads");
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private void zipDir(File dir, File zip, Task t) throws IOException {
        File[] files = dir.listFiles();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            JSONObject info = new JSONObject();
            try {
                info.put("app", "JmViewer");
                info.put("code", t.albumId);
                info.put("title", t.title);
                info.put("pages", t.total);
            } catch (Exception ignored) {
            }
            zos.putNextEntry(new ZipEntry("info.json"));
            zos.write(info.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            if (files != null) {
                java.util.Arrays.sort(files);
                byte[] buf = new byte[16384];
                for (File f : files) {
                    if (!f.isFile()) {
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(f.getName()));
                    try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            zos.write(buf, 0, n);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursive(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
