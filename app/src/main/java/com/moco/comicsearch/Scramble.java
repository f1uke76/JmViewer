package com.moco.comicsearch;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

import java.security.MessageDigest;

/**
 * 站点图片分块打乱的还原。
 *
 * 站点前端用 canvas 把图片切成 N 条横条再倒序贴回去，这里按同样的规则做一次。
 * N 由 md5(aid + 文件名数字) 的最后一位决定。
 */
public final class Scramble {

    private Scramble() {
    }

    /** 与站点 get_num() 等价。 */
    public static int getNum(String aid, String fileName) {
        String base = fileName == null ? "" : fileName;
        int dot = base.indexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        try {
            String seed = aid + base;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(seed.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            int n = hex.charAt(hex.length() - 1);   // charCodeAt
            long a = Long.parseLong(aid);
            if (a >= 268850 && a <= 421925) {
                n %= 10;
            } else if (a >= 421926) {
                n %= 8;
            }
            switch (n) {
                case 0: return 2;
                case 1: return 4;
                case 2: return 6;
                case 3: return 8;
                case 4: return 10;
                case 5: return 12;
                case 6: return 14;
                case 7: return 16;
                case 8: return 18;
                case 9: return 20;
                default: return 10;
            }
        } catch (Exception e) {
            return 10;
        }
    }

    /** 下载字节 → 解码 → 还原。 */
    public static Bitmap decode(byte[] data, String aid, String fileName, int scrambleId) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap src;
        try {
            src = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        } catch (OutOfMemoryError e) {
            opt.inSampleSize = 2;
            src = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        }
        if (src == null) {
            return null;
        }
        int aidNum = 0;
        try {
            aidNum = Integer.parseInt(aid);
        } catch (Exception ignored) {
        }
        if (aidNum < scrambleId) {
            return src;
        }
        Bitmap fixed = unscramble(src, getNum(aid, fileName));
        if (fixed != src) {
            src.recycle();
        }
        return fixed;
    }

    /** 按站点逻辑把横条倒序还原。 */
    public static Bitmap unscramble(Bitmap src, int slices) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (slices <= 1 || w <= 0 || h <= 0) {
            return src;
        }
        Bitmap out;
        try {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return src;
        }
        Canvas canvas = new Canvas(out);
        int unit = h / slices;
        int rest = h % slices;
        if (unit <= 0) {
            return src;
        }
        for (int m = 0; m < slices; m++) {
            int g = unit;
            int c = unit * m;
            int y = h - unit * (m + 1) - rest;
            if (m == 0) {
                g += rest;
            } else {
                c += rest;
            }
            if (y < 0) {
                y = 0;
            }
            if (c + g > h) {
                g = h - c;
            }
            if (y + g > h) {
                g = h - y;
            }
            if (g <= 0) {
                continue;
            }
            canvas.drawBitmap(src,
                    new Rect(0, y, w, y + g),
                    new Rect(0, c, w, c + g), null);
        }
        return out;
    }
}
