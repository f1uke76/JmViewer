package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 图片下载 → 解扰 → 内存/磁盘缓存。 */
public class ImageLoader {

    public interface Callback {
        void onLoaded(Bitmap bitmap);

        void onError(Exception e);

        /** 网络下载进度（0-100），本地缓存命中时不会触发。 */
        default void onProgress(int percent) {
        }
    }

    private static ImageLoader instance;

    private final Context ctx;
    private final Site site;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory;
    private final File cacheDir;

    private ImageLoader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.site = Site.get(ctx);
        this.cacheDir = new File(ctx.getCacheDir(), "raw");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        int max = (int) (Runtime.getRuntime().maxMemory() / 6);
        memory = new LruCache<String, Bitmap>(max) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public static synchronized ImageLoader get(Context ctx) {
        if (instance == null) {
            instance = new ImageLoader(ctx);
        }
        return instance;
    }

    public void clearMemory() {
        memory.evictAll();
    }

    // ------------------------------------------------------------ 缓存管理

    /** 统计磁盘上每个作品（按 aid 前缀）已缓存的字节数，封面缓存不计入。 */
    public java.util.Map<String, Long> cachedAlbums() {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (!f.isFile()) {
                continue;
            }
            String name = f.getName();
            if (name.startsWith("cover_")) {
                continue;
            }
            int cut = name.indexOf('_');
            if (cut <= 0) {
                continue;
            }
            String aid = name.substring(0, cut);
            Long old = out.get(aid);
            out.put(aid, (old == null ? 0L : old) + f.length());
        }
        return out;
    }

    /** 单个作品已占用的缓存字节数。 */
    public long cacheSize(String aid) {
        if (aid == null || aid.isEmpty()) {
            return 0;
        }
        long total = 0;
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return 0;
        }
        String prefix = aid + "_";
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(prefix)) {
                total += f.length();
            }
        }
        return total;
    }

    /** 清除单个作品的图片缓存。 */
    public void clearAlbum(String aid) {
        if (aid == null || aid.isEmpty()) {
            return;
        }
        File[] files = cacheDir.listFiles();
        if (files != null) {
            String prefix = aid + "_";
            for (File f : files) {
                if (f.isFile() && f.getName().startsWith(prefix)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        clearMemory();
    }

    /** 清空全部图片缓存（含封面）。 */
    public void clearAll() {
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        clearMemory();
    }

    public long totalCacheSize() {
        long total = 0;
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            if (f.isFile()) {
                total += f.length();
            }
        }
        return total;
    }

    /**
     * @param local 已下载的本地成品图，优先使用
     */
    public void load(final Models.PageImage page, final String aid, final int scrambleId,
                     final File local, final Callback cb) {
        final String key = aid + "/" + page.name;
        Bitmap hit = memory.get(key);
        if (hit != null) {
            cb.onLoaded(hit);
            return;
        }
        pool.execute(() -> {
            Bitmap bmp = null;
            Exception err = null;
            try {
                if (local != null && local.exists() && local.length() > 0) {
                    bmp = BitmapFactory.decodeFile(local.getAbsolutePath());
                } else {
                    File raw = new File(cacheDir, aid + "_" + page.name);
                    byte[] data;
                    if (raw.exists() && raw.length() > 0) {
                        data = readAll(new FileInputStream(raw));
                    } else {
                        data = site.getBytes(page.url, pct -> {
                            final int p = pct;
                            main.post(() -> cb.onProgress(p));
                        });
                        writeAll(raw, data);
                    }
                    bmp = Scramble.decode(data, aid, page.name, scrambleId);
                }
            } catch (Exception e) {
                err = e;
            }
            final Bitmap result = bmp;
            final Exception error = err;
            main.post(() -> {
                if (result != null) {
                    memory.put(key, result);
                    cb.onLoaded(result);
                } else {
                    cb.onError(error);
                }
            });
        });
    }

    /** 封面等普通图片（未打乱），按 url 缓存。 */
    public void loadCover(final String url, final Callback cb) {
        if (url == null || url.isEmpty()) {
            cb.onError(new java.io.IOException("empty url"));
            return;
        }
        Bitmap hit = memory.get(url);
        if (hit != null) {
            cb.onLoaded(hit);
            return;
        }
        pool.execute(() -> {
            Bitmap bmp = null;
            Exception err = null;
            try {
                File f = new File(cacheDir, "cover_" + Integer.toHexString(url.hashCode()) + ".img");
                byte[] data;
                if (f.exists() && f.length() > 0) {
                    data = readAll(new FileInputStream(f));
                } else {
                    data = site.getBytes(url);
                    writeAll(f, data);
                }
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 2;
                opt.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
            } catch (Exception e) {
                err = e;
            } catch (OutOfMemoryError e) {
                err = new Exception("内存不足");
            }
            final Bitmap result = bmp;
            final Exception error = err;
            main.post(() -> {
                if (result != null) {
                    memory.put(url, result);
                    cb.onLoaded(result);
                } else {
                    cb.onError(error);
                }
            });
        });
    }

    /** 缩略图：解扰后按最大宽度缩放，内存缓存用独立 key，避免占满大图。 */
    public void loadThumb(final Models.PageImage page, final String aid, final int scrambleId,
                          final int maxWidth, final Callback cb) {
        final String key = "t:" + aid + "/" + page.name;
        Bitmap hit = memory.get(key);
        if (hit != null) {
            cb.onLoaded(hit);
            return;
        }
        pool.execute(() -> {
            Bitmap bmp = null;
            Exception err = null;
            try {
                File raw = new File(cacheDir, aid + "_" + page.name);
                byte[] data;
                if (raw.exists() && raw.length() > 0) {
                    data = readAll(new FileInputStream(raw));
                } else {
                    data = site.getBytes(page.url);
                    writeAll(raw, data);
                }
                Bitmap full = Scramble.decode(data, aid, page.name, scrambleId);
                if (full != null) {
                    int w = full.getWidth();
                    if (maxWidth > 0 && w > maxWidth) {
                        int h = Math.max(1, (int) (full.getHeight() * (float) maxWidth / w));
                        bmp = Bitmap.createScaledBitmap(full, maxWidth, h, true);
                        if (bmp != full) {
                            full.recycle();
                        }
                    } else {
                        bmp = full;
                    }
                }
            } catch (Exception e) {
                err = e;
            } catch (OutOfMemoryError e) {
                err = new Exception("内存不足");
            }
            final Bitmap result = bmp;
            final Exception error = err;
            main.post(() -> {
                if (result != null) {
                    memory.put(key, result);
                    cb.onLoaded(result);
                } else {
                    cb.onError(error);
                }
            });
        });
    }

    /** 预取原始字节到磁盘缓存（不解码），供阅读器直接命中。可随时取消。 */
    public void prefetch(final java.util.List<Models.PageImage> pages, final String aid,
                         final java.util.concurrent.atomic.AtomicBoolean cancel) {
        if (pages == null || pages.isEmpty() || aid == null || aid.isEmpty()) {
            return;
        }
        pool.execute(() -> {
            for (Models.PageImage p : pages) {
                if (cancel != null && cancel.get()) {
                    return;
                }
                if (p.url == null || p.url.isEmpty() || p.name == null || p.name.isEmpty()) {
                    continue;
                }
                File raw = new File(cacheDir, aid + "_" + p.name);
                if (raw.exists() && raw.length() > 0) {
                    continue;
                }
                try {
                    byte[] data = site.getBytes(p.url);
                    writeAll(raw, data);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static byte[] readAll(InputStream in) throws Exception {        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void writeAll(File f, byte[] data) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        } catch (Exception ignored) {
        }
    }
}
