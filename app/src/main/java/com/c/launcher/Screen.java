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
            // 不再给内容区补 cutout 安全边距：挖孔机型上系统报的边距有 100 多像素，
            // 内容被整体推进去，空出来的那块露出窗口底色，就是"旁边一道黑边"。
            // 壁纸要铺满，宁可让挖孔压住一点点画面。
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

    /** 内容躲开刘海 / 挖孔：给内容区补 padding，背景仍然铺满 */
    private static void applySafeArea(final Activity a) {
        if (Build.VERSION.SDK_INT < 20) return;
        final View root = a.findViewById(android.R.id.content);
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
}
