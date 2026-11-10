package com.c.launcher;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * 全屏铺满 + 避开刘海：
 * 1) 允许铺进异形屏短边，避免系统把窗口缩成带黑边的小矩形；
 * 2) 隐藏状态栏 / 导航栏，遥控器和触屏都靠边缘下拉唤出；
 * 3) 把内容从刘海、挖孔下面推进来，四边不再被遮挡。
 */
public final class Screen {

    private Screen() { }

    public static void apply(final Activity a) {
        try {
            Window w = a.getWindow();
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
            w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            hideBars(a);
            applySafeArea(a);
        } catch (Throwable ignored) { }
    }

    /** 隐藏系统栏。用 IMMERSIVE_STICKY：切出去再回来会自己恢复隐藏 */
    public static void hideBars(Activity a) {
        try {
            View d = a.getWindow().getDecorView();
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | 0x00001000);
        } catch (Throwable ignored) { }
    }

    /**
     * 内容躲开刘海 / 挖孔。
     *
     * 关键：只给【内容层】补 padding，不能碰 android.R.id.content。
     * 之前补在最外层，壁纸 ImageView 是它的子 View，跟着一起被缩小，
     * 空出来的那条就露出窗口底色 —— 手机上看到的那道黑边就是这么来的。
     * 现在壁纸铺满整屏，只有图标/文字往安全区内缩。
     */
    private static void applySafeArea(final Activity a) {
        if (Build.VERSION.SDK_INT < 20) return;
        final View root = a.findViewById(R.id.contentMain);
        if (root == null) return;
        final int pl = root.getPaddingLeft();
        final int pt = root.getPaddingTop();
        final int pr = root.getPaddingRight();
        final int pb = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l = 0, t = 0, r = 0, b = 0;
                if (Build.VERSION.SDK_INT >= 28) {
                    android.view.DisplayCutout dc = insets.getDisplayCutout();
                    if (dc != null) {
                        l = dc.getSafeInsetLeft();
                        t = dc.getSafeInsetTop();
                        r = dc.getSafeInsetRight();
                        b = dc.getSafeInsetBottom();
                    }
                }
                v.setPadding(pl + l, pt + t, pr + r, pb + b);
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    /**
     * 图标边长（px）：按屏幕短边取比例，不写死 dp。
     * dp 只跟 density 走，而车机经常把 dpi 报成 mdpi —— 1080p 的屏按 dp 算出来只有 640dp 宽，
     * 图标就小得用遥控器点不准；按比例算，大屏自然大、小屏自然小。
     */
    public static int iconSide(android.content.Context c) {
        try {
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int min = Math.min(dm.widthPixels, dm.heightPixels);
            int side = (int) (min * 0.105f);
            int lo = (int) (40 * dm.density);
            int hi = (int) (110 * dm.density);
            return Math.max(lo, Math.min(hi, side));
        } catch (Throwable t) {
            return 64;
        }
    }
}
