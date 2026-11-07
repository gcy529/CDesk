package com.c.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * 自动黑白：取壁纸在「悬浮窗当前所在区域」的平均亮度，决定文字用白还是黑。
 *
 * 要点：
 *  - 只在需要时重采样（每 2s 一次 / 位置变化后一次），不常驻轮询
 *  - 先把该区域缩放到 8x8 再从 64 个像素算平均色，开销极小
 *  - 亮度用感知公式 0.299R + 0.587G + 0.114B，比算术平均更接近人眼
 *  - 缓存壁纸位图，避免每次 getDrawable 都重新解码
 *  - 任何异常退回白字，保证低端机也能看清
 */
public class AutoContrast {

    public static final float LUMINANCE_THRESHOLD = 0.62f;
    public static final int STYLE_WHITE = 0;
    public static final int STYLE_BLACK = 1;

    private static final int SAMPLE = 8;          // 采样网格 8x8
    private static final long CACHE_MS = 30000L;  // 壁纸缓存 30s

    private Bitmap cachedWallpaper;
    private long cachedAt = 0L;
    private int screenW = 0;
    private int screenH = 0;

    /** 屏幕尺寸是否已就绪 */
    public boolean ready() {
        return screenW > 0 && screenH > 0;
    }

    public void updateScreenSize(Context ctx) {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
        } catch (Throwable ignored) {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
        }
    }

    /** 取当前壁纸位图，缓存 30s */
    private Bitmap loadWallpaper(Context ctx) {
        long now = System.currentTimeMillis();
        if (cachedWallpaper != null && !cachedWallpaper.isRecycled() && now - cachedAt < CACHE_MS) {
            return cachedWallpaper;
        }
        try {
            WallpaperManager wmgr = WallpaperManager.getInstance(ctx.getApplicationContext());
            Drawable d = wmgr.getDrawable();
            if (d == null) return null;
            Bitmap bmp = null;
            if (d instanceof BitmapDrawable) {
                bmp = ((BitmapDrawable) d).getBitmap();
            } else {
                int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 256;
                int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 256;
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                d.setBounds(0, 0, w, h);
                d.draw(c);
            }
            if (bmp == null || bmp.isRecycled()) return null;
            cachedWallpaper = bmp;
            cachedAt = now;
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 计算指定屏幕矩形区域的感知亮度。
     * @param rect 悬浮窗在屏幕上的位置（像素）
     * @return 0..1，取不到壁纸时返回 -1
     */
    public float sampleLuminance(Context ctx, RectF rect) {
        if (screenW <= 0 || screenH <= 0) updateScreenSize(ctx);
        Bitmap wp = loadWallpaper(ctx);
        if (wp == null || rect == null) return -1f;

        try {
            int srcLeft = (int) (rect.left / screenW * wp.getWidth());
            int srcTop = (int) (rect.top / screenH * wp.getHeight());
            int srcRight = (int) (rect.right / screenW * wp.getWidth());
            int srcBottom = (int) (rect.bottom / screenH * wp.getHeight());
            srcLeft = clamp(srcLeft, 0, wp.getWidth() - 1);
            srcTop = clamp(srcTop, 0, wp.getHeight() - 1);
            srcRight = clamp(srcRight, srcLeft + 1, wp.getWidth());
            srcBottom = clamp(srcBottom, srcTop + 1, wp.getHeight());

            Bitmap region = Bitmap.createBitmap(wp, srcLeft, srcTop,
                    srcRight - srcLeft, srcBottom - srcTop);
            Bitmap small = Bitmap.createScaledBitmap(region, SAMPLE, SAMPLE, true);
            if (region != small) region.recycle();

            long sumLum = 0;
            int n = SAMPLE * SAMPLE;
            for (int y = 0; y < SAMPLE; y++) {
                for (int x = 0; x < SAMPLE; x++) {
                    int p = small.getPixel(x, y);
                    float lum = (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f;
                    sumLum += (long) (lum * 1000f);
                }
            }
            small.recycle();
            return (sumLum / (float) n) / 1000f;
        } catch (Throwable t) {
            return -1f;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * 根据区域亮度给出「文字色 + 胶囊底色」。
     * 亮度 < 阈值 -> 白字 + 深底；否则 -> 黑字 + 浅底。
     * 采样失败时兜底为白字 + 深底。
     */
    public static int[] styleFor(float luminance) {
        boolean dark = luminance < 0f || luminance < LUMINANCE_THRESHOLD;
        if (dark) {
            return new int[] { 0xFFFFFFFF, 0x66000000, 0x33FFFFFF };
        } else {
            return new int[] { 0xFF0C0F16, 0x55FFFFFF, 0x22000000 };
        }
    }
}