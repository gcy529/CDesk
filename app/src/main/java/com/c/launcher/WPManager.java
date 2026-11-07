package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

/**
 * 壁纸管理：三张内置壁纸（金色时刻/晨光/鲸夜）+ 纯色模式 + 日夜自动。
 * 默认 = 金色时刻（用户钦定）。
 *
 * 为什么用 BitmapFactory.decodeResource 而不是 WallpaperManager：
 * 我们改的是自家布局背景，不是系统壁纸 —— 不申请权限、不碰系统设置，
 * 卸载即净，对老盒子最友好。
 *
 * 图片按 4K 原图打包（JPEG quality=95，视觉无损，单张约 1.1-1.4MB）。
 * decode 时 inSampleSize 只在屏幕分辨率低于图片时降采样：
 * 4K 电视吃到完整 4K，1080p 老屏自动降一半，两头都不吃亏。
 */
public final class WPManager {

    public static final int WP_NONE = 0;   // 纯色（跟随主题渐变）
    public static final int WP_NIGHT = 1;  // 鲸夜（半写实鲸鱼女）
    // 默认 = WP_SUNSET（金色时刻）：用户钦定
    public static final int WP_DAY = 2;    // 晨光（睁眼窗光）
    public static final int WP_SUNSET = 3; // 金色时刻（日落）

    private static final String PREFS = "cdesk_wallpaper";
    private static final String KEY_WP = "which";
    private static final String KEY_AUTO = "auto";

    private WPManager() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int which(Context c) { return prefs(c).getInt(KEY_WP, WP_SUNSET); }

    public static void setWhich(Context c, int v) {
        prefs(c).edit().putInt(KEY_WP, v).commit();
    }

    /** 日夜自动：开 = 6:00-18:00 用白天壁纸，其余用夜间壁纸；关 = 固定用户选择 */
    public static boolean isAuto(Context c) { return prefs(c).getBoolean(KEY_AUTO, true); }

    public static void setAuto(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_AUTO, v).commit(); }

    /** 当前时刻实际该显示哪张 */
    public static int effective(Context c) {
        if (!isAuto(c)) return which(c);
        int h = new java.util.Date().getHours();
        return (h >= 6 && h < 18) ? WP_DAY : WP_SUNSET;
    }

    /** 按屏幕尺寸解码：4K 屏吃全图，低分屏自动降采样防 OOM */
    private static Bitmap decode(Activity a, int resId, int reqW, int reqH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(a.getResources(), resId, o);
        int s = 1;
        while ((o.outWidth / (s * 2)) >= reqW && (o.outHeight / (s * 2)) >= reqH) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888; // 4K 屏要全质量，内存换画质
        return BitmapFactory.decodeResource(a.getResources(), resId, o2);
    }

    /** 把壁纸铺到背景 ImageView 上；返回 true 表示铺了图（背景色该隐藏） */
    public static boolean apply(Activity a, ImageView bg, View colorBg1, View colorBg2) {
        int wp = effective(a);
        if (wp == WP_NONE) {
            bg.setVisibility(View.GONE);
            colorBg1.setVisibility(View.VISIBLE);
            colorBg2.setVisibility(View.VISIBLE);
            return false;
        }
        int res = wp == WP_NIGHT ? R.drawable.wall_night
                : wp == WP_DAY ? R.drawable.wall_day : R.drawable.wall_sunset;
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        Bitmap b = decode(a, res, w, h);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageDrawable(new BitmapDrawable(a.getResources(), b));
        bg.setVisibility(View.VISIBLE);
        colorBg1.setVisibility(View.GONE);
        colorBg2.setVisibility(View.GONE);
        return true;
    }

    /** 壁纸是深色还是浅色（决定桌面文字用白还是深灰）：金色时刻/鲸夜深底，晨光浅底 */
    public static boolean isDarkWallpaper(int wp) {
        return wp != WP_DAY;
    }
}
