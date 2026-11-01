package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;

/** 下载整话：把解扰后的图片存到应用私有目录，可离线阅读。 */
public class Downloader {

    public interface Progress {
        void onProgress(int done, int total);

        void onDone(boolean ok, String message);
    }

    public static File dir(Context ctx, String aid) {
        File root = ctx.getExternalFilesDir("manga");
        if (root == null) {
            root = new File(ctx.getFilesDir(), "manga");
        }
        File d = new File(root, aid);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static File pageFile(Context ctx, String aid, int index) {
        return new File(dir(ctx, aid), String.format("%05d.jpg", index + 1));
    }

    public static boolean isComplete(Context ctx, String aid, int count) {
        if (count <= 0) {
            return false;
        }
        return pageFile(ctx, aid, count - 1).exists();
    }

    public static long sizeOf(Context ctx, String aid) {
        long total = 0;
        File[] files = dir(ctx, aid).listFiles();
        if (files != null) {
            for (File f : files) {
                total += f.length();
            }
        }
        return total;
    }

    public static void delete(Context ctx, String aid) {
        File[] files = dir(ctx, aid).listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** 同步下载，请放在子线程调用。 */
    public static boolean download(Context ctx, Models.ChapterContent content, Progress cb) {
        Site site = Site.get(ctx);
        int total = content.pages.size();
        for (int i = 0; i < total; i++) {
            File out = pageFile(ctx, content.aid, i);
            if (out.exists() && out.length() > 0) {
                if (cb != null) {
                    cb.onProgress(i + 1, total);
                }
                continue;
            }
            try {
                Models.PageImage page = content.pages.get(i);
                byte[] data = site.getBytes(page.url);
                Bitmap bmp = Scramble.decode(data, content.aid, page.name, content.scrambleId);
                if (bmp == null) {
                    continue;
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                }
                bmp.recycle();
            } catch (Exception e) {
                if (cb != null) {
                    cb.onDone(false, "第 " + (i + 1) + " 页下载失败：" + e.getMessage());
                }
                return false;
            }
            if (cb != null) {
                cb.onProgress(i + 1, total);
            }
        }
        if (cb != null) {
            cb.onDone(true, "已下载 " + total + " 页");
        }
        return true;
    }
}
