package com.c.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.animation.ArgbEvaluator;
import android.view.View;
import android.widget.TextView;

import java.util.Calendar;

/**
 * 换肤管理。
 *
 * 职责：
 *  - 维护 12 套配色（8 深 + 4 浅），色值取自公开的开源设计系统（MIT / CC0）
 *  - 日夜自动：6:00-18:00 用浅色池，18:00-6:00 用深色池，像自动近光灯
 *  - 换肤：在当前时段的池子里循环，白天换白天用的，夜里换夜里用的
 *  - 换肤动画：ValueAnimator.ofFloat + ArgbEvaluator（都是 API 1，老车机能跑）
 */
public class ThemeManager {

    public static final String PREFS = "cdesk_prefs";
    public static final String KEY_DAY = "palette_day";     // 白天选中的下标（浅色池内）
    public static final String KEY_NIGHT = "palette_night"; // 夜里选中的下标（深色池内）

    /** 浅色池从第 8 个开始（0-7 深，8-11 浅） */
        private static final int DAY_START = 6;
    private static final int DAY_END = 18;

    /** 一套配色：bgTop/bgBottom 背景渐变两端，accent 强调色，
     *  fg 主文字，dim 次要文字，isLight 是否浅色。
     *  浅色主题的大字用 fg（深灰），accent 只给小元素 —— 
     *  紫色大字在白底上非常突兀，这是踩过坑的。 */
    public static final class Palette {
        public final String name;
        public final int bgTop;
        public final int bgBottom;
        public final int accent;
        public final int fg;
        public final int dim;
        public final boolean isLight;
        public final int accentSoft;
        public final String accentHex;
        public Palette(String name, int bgTop, int bgBottom, int accent,
                       int fg, int dim, boolean isLight) {
            this.name = name;
            this.bgTop = bgTop;
            this.bgBottom = bgBottom;
            this.accent = accent;
            this.fg = fg;
            this.dim = dim;
            this.isLight = isLight;
            this.accentSoft = (accent & 0x00FFFFFF) | 0x66000000;
            this.accentHex = String.format("#%06X", accent & 0x00FFFFFF);
        }
    }

    /** 12 套配色：0-7 深色（夜里用），8-11 浅色（白天用） */
    /**
     * 12 套配色（0-7 夜间深色池，8-11 白天浅色池）。
     *
     * 色值来源（2026-11 通过 GitHub API 实查）：
     *  - Tailwind CSS v4（tailwindlabs/tailwindcss，98k star，2026-11-06 仍在推送）
     *    从官方 theme.css 抓 oklch 值，用 OKLab 公式转成 Android 可用的 sRGB hex
     *  - 布局公式：近黑底（zinc-950 派生）+ 高饱和强调色 + 背景同色系渐变，
     *    这是 2025-2026 消费级产品（Arc/Linear/Spotify）的主流做法
     *  - 全部 MIT 授权，无版权风险
     */
    public static final Palette[] PALETTES = new Palette[] {
        // 只保留一深一浅兜底：壁纸模式下不走这里，纯色（自定义）模式用夜间组，浅色组给"晨雾"类浅壁纸兜底
        new Palette("深空", 0xFF0B1020, 0xFF1E2A4A, 0xFF38BDF8, 0xFFECEBF5, 0xFF8B93A8, false),
        new Palette("晨白", 0xFFF2F4F8, 0xFFE4E8EF, 0xFF2563EB, 0xFF232B3A, 0xFF5A6474, true),
    };

    /** 现在是不是白天（6:00-18:00） */
    public static boolean isDayNow() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= DAY_START && h < DAY_END;
    }

    /** 今天的稳定哈希：同一天全设备结果一致 */
    

    /** 当前应使用的配色：按昼夜选池，池内用用户选过的，没选过就按今日哈希 */
    public static Palette current(Context ctx) {
        return PALETTES[currentIndex(ctx)];
    }

    public static int currentIndex(Context ctx) {
        // 池子只有一深一浅：按时段取对应兜底组
        return isDayNow() ? 1 : 0;
    }

    /** 换肤：在当前时段的池子里循环 */
    

    /** 放弃手动选择，回到日夜自动 */
    public static void resetToToday(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAY, -1).putInt(KEY_NIGHT, -1).commit();
    }

    public static Palette paletteAt(int i) {
        if (i < 0) i = 0;
        return PALETTES[i % PALETTES.length];
    }

    public static final String KEY_SETUP_DONE = "setup_done";

    public static boolean isSetupDone(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SETUP_DONE, false);
    }

    public static void markSetupDone(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SETUP_DONE, true).commit();
    }

    // ---------------- 动画 ----------------

    /**
     * TextView 文字色平滑过渡（300ms）。
     * 注意：不能用 ValueAnimator.ofArgb —— 那是 API 21 才有的，
     * 安卓 4.x 会直接 NoSuchMethodError。ofFloat + ArgbEvaluator 都是 API 1。
     */
    public static void animateTextColor(final TextView tv, final int from, final int to) {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(300);
        final ArgbEvaluator ev = new ArgbEvaluator();
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                try {
                    float t = a.getAnimatedFraction();
                    tv.setTextColor((Integer) ev.evaluate(t, Integer.valueOf(from), Integer.valueOf(to)));
                } catch (Throwable ignored) { }
            }
        });
        anim.start();
    }

    /** 两个背景层从旧色平滑过渡到新色 */
    public static void animateBackground(final View top, final View bottom,
                                         final int oldTop, final int oldBottom,
                                         final int newTop, final int newBottom) {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = a.getAnimatedFraction();
                top.setBackgroundColor(interpolate(oldTop, newTop, t));
                bottom.setBackgroundColor(interpolate(oldBottom, newBottom, t));
            }
        });
        anim.start();
    }

    /** 统一的胶囊按钮样式：浅色底用黑 8%，深色底用白 10%，描边跟强调色 */
    public static void styleButton(android.widget.Button b, Palette p) {
        if (b == null || p == null) return;
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(999);
        g.setColor(p.isLight ? 0x14000000 : 0x1AFFFFFF);
        g.setStroke(2, p.accent);
        b.setBackgroundDrawable(g);
        b.setTextColor(p.accent);
    }

    public static int interpolate(int from, int to, float t) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }
}
