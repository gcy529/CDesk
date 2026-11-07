package com.c.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 自定义壁纸沙盒。
 *
 * 三条硬约束，都是为了避免把老车机/盒子的存储塞满：
 *  1. 只存在应用私有目录 files/wallpaper/ —— 不申请任何存储权限，卸载即清空
 *  2. 最多 3 张，第 4 张进来时自动淘汰最旧的那张
 *  3. 导入时按 1920 长边降采样、JPEG q=85 重压，单张约 200-400KB，满打满算不到 1.5MB
 */
public final class CustomWallpapers {

    public static final int MAX = 3;

    private CustomWallpapers() { }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "wallpaper");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 按写入时间从旧到新 */
    public static List<File> list(Context c) {
        File[] fs = dir(c).listFiles();
        List<File> out = new ArrayList<File>();
        if (fs == null) return out;
        Arrays.sort(fs, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                long x = a.lastModified(), y = b.lastModified();
                return x < y ? -1 : (x > y ? 1 : 0);
            }
        });
        for (File f : fs) {
            if (f.isFile() && f.length() > 0) out.add(f);
        }
        return out;
    }

    public static int count(Context c) { return list(c).size(); }

    /** 导入一张：降采样 + 重压 + 落盘；满 3 张淘汰最旧。失败返回 null */
    public static File add(Context c, Uri uri) {
        InputStream in = null;
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            in = c.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, probe);
            in.close();
            in = null;

            int sample = 1;
            int longest = Math.max(probe.outWidth, probe.outHeight);
            while (longest / (sample * 2) >= 1920) sample *= 2;

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(in, null, opt);
            in.close();
            in = null;
            if (bm == null) return null;

            List<File> cur = list(c);
            if (cur.size() >= MAX) {
                remove(c, cur.get(0));
            }

            File out = new File(dir(c), "w" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(out);
            bm.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();
            bm.recycle();
            return out;
        } catch (Throwable t) {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
            return null;
        }
    }

    public static void remove(Context c, File f) {
        if (f == null) return;
        try {
            if (f.getAbsolutePath().equals(WPManager.customPath(c))) {
                WPManager.clearCustom(c);
            }
            f.delete();
        } catch (Throwable ignored) { }
    }
}
