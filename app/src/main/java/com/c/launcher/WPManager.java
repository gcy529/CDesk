package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;

/**
 * 壁纸管理：晨光(默认) + 鲸夜 + 傍晚(4k) + 纯色 + 薄荷 + 自定义图片 + 日夜自动。
 *
 * 为什么用 BitmapFactory.decodeResource 而不是 WallpaperManager：
 * 我们改的是自家布局背景，不是系统壁纸 —— 不申请权限、不碰系统设置，
 * 卸载即净，对老盒子最友好。
 *
 * 防 OOM：4K 原图绝不走 setImageResource / 直接 decode。
 * decode 时 inJustDecodeBounds 先读尺寸，再按屏幕分辨率算 inSampleSize 降采样，
 * 4K 电视吃到完整 4K，1080p 老屏自动降一半，两头都不吃亏。
 */
public final class WPManager {

    /** 壁纸顺序（换肤循环 & 老存档迁移共用）：
     *  晨光(默认) -> 纯色 -> 鲸夜 -> 傍晚(4k) -> 薄荷 -> 自定义 -> 晨光
     *  0.7.9 之前旧枚举 0=纯色 1=鲸夜 2=晨光 3=金色时刻；
     *  0.7.9/0.7.10 中间态 0=少女 1=纯色 2=鲸夜 3=晨光 4=金色 5=自定义 6=薄荷。
     *  这里直接按新顺序做老存档归一，老用户存的值迁到对应新值。 */
    public static final int WP_MORNING = 0;  // 日光少女/晨光（4K，默认，明亮）
    public static final int WP_NONE = 1;     // 纯色（跟随主题渐变）
    public static final int WP_NIGHT = 2;    // 鲸夜（半写实鲸鱼女，夜晚档，深蓝）
    public static final int WP_DUSK = 3;     // 金色时刻/傍晚（4k 日落，同少女那张但黄昏光）
    public static final int WP_MINT = 4;     // 薄荷纯色
    public static final int WP_CUSTOM = 5;   // 用户自己的图片（局域网传入或本地选择）

    private static final String PREFS = "cdesk_wallpaper";
    private static final String KEY_WP = "which";
    private static final String KEY_AUTO = "auto";
    private static final String KEY_CUSTOM = "custom_path";

    private WPManager() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final String KEY_MIGRATED = "enum_migrated_v7";

    public static int which(Context c) {
        SharedPreferences p = prefs(c);
        // 老存档归一：0.7.9 前(0=纯色1=鲸夜2=晨光3=金色)、0.7.9/0.7.10 中间态(0..6)
        // 都迁到新枚举(0=晨光1=纯色2=鲸夜3=傍晚4=薄荷5=自定义)。未知值落回默认晨光。
        if (!p.getBoolean(KEY_MIGRATED, false)) {
            int old = p.getInt(KEY_WP, WP_MORNING);
            int now = migrate(old);
            p.edit().putInt(KEY_WP, now).putBoolean(KEY_MIGRATED, true).commit();
            return now;
        }
        int v = p.getInt(KEY_WP, WP_MORNING);
        return (v < WP_MORNING || v > WP_CUSTOM) ? WP_MORNING : v;
    }

    /** 老存档值 -> 新枚举。新存档已迁过会带 KEY_MIGRATED，这里只处理 0.7.9 前与中间态。 */
    private static int migrate(int old) {
        // 0.7.9/0.7.10 中间态（含少女，3=晨光 4=金色时刻 是同一张 4k 的重编码）
        if (old >= 0 && old <= 6) {
            switch (old) {
                case 0: return WP_MORNING;   // 旧少女(4k) -> 新晨光（同一张图）
                case 1: return WP_NONE;      // 纯色
                case 2: return WP_NIGHT;     // 鲸夜
                case 3: return WP_MORNING;    // 旧晨光(重编码少女) -> 新晨光（同一张图）
                case 4: return WP_DUSK;      // 旧金色时刻(4k日落) -> 新傍晚（同一张 4k）
                case 5: return WP_CUSTOM;
                case 6: return WP_MINT;
            }
        }
        // 0.7.9 前老存档（无少女，0=纯色 1=鲸夜 2=晨光 3=金色）
        switch (old) {
            case 0: return WP_NONE;
            case 1: return WP_NIGHT;
            case 2: return WP_MORNING;
            case 3: return WP_DUSK;
        }
        return WP_MORNING;
    }

    public static void setWhich(Context c, int v) {
        // 手动选过就以手动为准，不再被时间覆盖
        prefs(c).edit().putInt(KEY_WP, v).putBoolean(KEY_AUTO, false).commit();
    }

    /**
     * 自动跟随时间：默认关。
     * 开 = 6:00-18:00 用晨光(明亮)、其余用鲸夜(夜晚)。
     * 原来默认开，等于用户选什么都白选 —— 选少女/鲸夜永远被覆盖。
     */
    public static boolean isAuto(Context c) {
        SharedPreferences p = prefs(c);
        // 老版本默认开过自动跟随时间，存进去的 true 得清一次，
        // 否则装了新包还是"到白天就变晨光了"，选什么都白选。
        if (!p.getBoolean("auto_off_v1", false)) {
            p.edit().putBoolean(KEY_AUTO, false).putBoolean("auto_off_v1", true).commit();
            return false;
        }
        return p.getBoolean(KEY_AUTO, false);
    }

    public static void setAuto(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_AUTO, v).commit(); }

    /** 自定义图片：记住路径，decode 时读文件 */
    public static void setCustomPath(Context c, String path) {
        prefs(c).edit().putString(KEY_CUSTOM, path).commit();
        setWhich(c, WP_CUSTOM);
    }

    public static String customPath(Context c) { return prefs(c).getString(KEY_CUSTOM, ""); }

    /** 图片被删了：清掉引用，回到默认晨光 */
    public static void clearCustom(Context c) {
        prefs(c).edit().remove(KEY_CUSTOM).commit();
        if (which(c) == WP_CUSTOM) setWhich(c, WP_MORNING);
    }

    /** 当前时刻实际该显示哪张 */
    public static int effective(Context c) {
        if (!isAuto(c)) return which(c);
        // 自定义图片不参与日夜自动（用户的照片没有日夜之分）
        if (which(c) == WP_CUSTOM) return WP_CUSTOM;
        int h = new java.util.Date().getHours();
        // 白天用晨光(明亮)，夜晚用鲸夜(深蓝)；傍晚/纯色/薄荷不参与自动档
        return (h >= 6 && h < 18) ? WP_MORNING : WP_NIGHT;
    }

    /** 按屏幕尺寸解码：4K 屏吃全图，低分屏自动降采样防 OOM；OOM 时逐级加倍采样重试 + RGB_565 兜底 */
    private static Bitmap decode(Activity a, int resId, int reqW, int reqH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(a.getResources(), resId, o);
        int s = 1;
        while ((o.outWidth / (s * 2)) >= reqW && (o.outHeight / (s * 2)) >= reqH) s *= 2;
        // 低内存设备：从当前采样往上加倍重试，最多 5 次；ARGB_8888 失败降 RGB_565
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = s;
                o2.inPreferredConfig = attempt < 3 ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeResource(a.getResources(), resId, o2);
                if (b != null) return b;
            } catch (OutOfMemoryError e) {
                s *= 2;   // 内存不够：加倍采样再来
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** 取当前生效的壁纸位图；WP_NONE 或解码失败返回 null */
    public static Bitmap bitmapFor(Activity a) {
        int wp = effective(a);
        if (wp == WP_NONE) return null;
        Bitmap b = null;
        if (wp == WP_CUSTOM) {
            String path = customPath(a);
            if (path.length() > 0) {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, o);
                    int w0 = a.getResources().getDisplayMetrics().widthPixels;
                    int h0 = a.getResources().getDisplayMetrics().heightPixels;
                    int s = 1;
                    while ((o.outWidth / (s * 2)) >= w0 && (o.outHeight / (s * 2)) >= h0) s *= 2;
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = s;
                    o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    b = BitmapFactory.decodeFile(path, o2);
                } catch (Throwable t) { b = null; }
            }
            if (b == null) {
                // 自定义图丢了：退回默认晨光
                setWhich(a, WP_MORNING);
                wp = WP_MORNING;
            }
        }
        if (b == null) {
            try {
                int res = wp == WP_NIGHT ? R.drawable.wall_night
                        : wp == WP_MINT ? R.drawable.wall_mint
                        : wp == WP_DUSK ? R.drawable.final_wallpaper_4k
                        : R.drawable.wall_morning;   // 晨光默认，4K 降采样吃全
                int w = a.getResources().getDisplayMetrics().widthPixels;
                int h = a.getResources().getDisplayMetrics().heightPixels;
                b = decode(a, res, w, h);
            } catch (Throwable t) {
                b = null;   // 崩不了：壁纸失败就回落纯色
            }
        }
        return b;
    }

    /** 子页面（打赏 / 传输 / 设置）用首页同一张壁纸，压一层暗色保证文字能看清 */
    public static void applyToRoot(Activity a, View root) {
        if (root == null) return;
        ThemeManager.Palette p = ThemeManager.current(a);
        Bitmap b = null;
        try { b = bitmapFor(a); } catch (Throwable ignored) { }
        if (b == null) {
            root.setBackgroundColor(p.bgTop);
            return;
        }
        BitmapDrawable bd = new BitmapDrawable(a.getResources(), b);
        bd.setGravity(Gravity.FILL);
        Drawable[] layers = new Drawable[] { new ColorDrawable(0xC40B0F19), bd };
        try {
            root.setBackgroundDrawable(new LayerDrawable(layers));
        } catch (Throwable t) {
            root.setBackgroundColor(p.bgTop);
        }
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
        Bitmap b = null;
        try { b = bitmapFor(a); } catch (Throwable t) { b = null; }
        if (b == null) {
            bg.setVisibility(View.GONE);
            colorBg1.setVisibility(View.VISIBLE);
            colorBg2.setVisibility(View.VISIBLE);
            return false;
        }
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageDrawable(new BitmapDrawable(a.getResources(), b));
        bg.setVisibility(View.VISIBLE);
        colorBg1.setVisibility(View.GONE);
        colorBg2.setVisibility(View.GONE);
        return true;
    }

    /** 壁纸是深色还是浅色（决定桌面文字用白还是深灰） */
    public static boolean isDarkWallpaper(int wp) {
        // 桌面文字下面有半透明底衬，所以一律按"深色底、白字"走，壁纸换哪张都不影响可读性
        return true;
    }
}
