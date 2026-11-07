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
    public static final int LIGHT_BASE = 8;
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
        // —— 夜间深色池 ——
        // Tailwind violet-500 (oklch 60.6% 0.25 292.7) -> #8E51FF，底用 violet-950 派生
        new Palette("极光", 0xFF0D0B1E, 0xFF1D1240, 0xFF8E51FF, 0xFFECEBF5, 0xFF8B87A3, false),
        // cyan-400 -> #00D3F2
        new Palette("霓虹海", 0xFF05070F, 0xFF053345, 0xFF00D3F2, 0xFFE6F6FA, 0xFF6E93A8, false),
        // emerald-400 -> #00D492
        new Palette("薄荷夜", 0xFF071310, 0xFF0A2B20, 0xFF00D492, 0xFFE4F7F0, 0xFF6FA392, false),
        // pink-500 -> #F6339A
        new Palette("樱粉夜", 0xFF140B12, 0xFF33103A, 0xFFF6339A, 0xFFFBE9EF, 0xFFA87F90, false),
        // orange-500 -> #FF6900
        new Palette("火山", 0xFF120B08, 0xFF2E1403, 0xFFFF6900, 0xFFFBEAE2, 0xFFA8806B, false),
        // lime-400 -> #9AE600
        new Palette("Volt", 0xFF0B0D07, 0xFF1B2408, 0xFF9AE600, 0xFFF2F7E4, 0xFF99A37F, false),
        // 纯黑 OLED + 白强调（Apple 式）
        new Palette("纯黑", 0xFF050506, 0xFF101013, 0xFFF5F5F7, 0xFFFFFFFF, 0xFF8A8A93, false),
        // blue-500 -> #2B7FFF
        new Palette("蓝调夜", 0xFF0A0E1A, 0xFF0F1D3D, 0xFF2B7FFF, 0xFFE8EEFA, 0xFF7E8BB0, false),
        // —— 白天浅色池（底是白，强调色不变）——
        new Palette("晨雾", 0xFFF8FAFC, 0xFFEDF1F7, 0xFF2B7FFF, 0xFF1E2A3A, 0xFF7C8AA0, true),
        new Palette("奶油", 0xFFFFF9F1, 0xFFFFF1DF, 0xFFFF6900, 0xFF3A2E22, 0xFFA08C77, true),
        new Palette("薄荷白", 0xFFF1FBF6, 0xFFE6F5EE, 0xFF0DA678, 0xFF1E332B, 0xFF7B948A, true),
        new Palette("樱日", 0xFFFFF5F7, 0xFFFFE9EE, 0xFFF6339A, 0xFF3A242B, 0xFFA87F8D, true)
    };

    /** 现在是不是白天（6:00-18:00） */
    public static boolean isDayNow() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= DAY_START && h < DAY_END;
    }

    /** 今天的稳定哈希：同一天全设备结果一致 */
    private static int todayHash() {
        Calendar c = Calendar.getInstance();
        String key = c.get(Calendar.YEAR) + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
        int h = 0;
        for (int i = 0; i < key.length(); i++) {
            h = (h * 31 + key.charAt(i)) & 0x7fffffff;
        }
        return h;
    }

    /** 当前应使用的配色：按昼夜选池，池内用用户选过的，没选过就按今日哈希 */
    public static Palette current(Context ctx) {
        return PALETTES[currentIndex(ctx)];
    }

    public static int currentIndex(Context ctx) {
        boolean day = isDayNow();
        int poolSize = day ? PALETTES.length - LIGHT_BASE : LIGHT_BASE;
        String key = day ? KEY_DAY : KEY_NIGHT;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int saved = sp.getInt(key, -1);
        if (saved < 0 || saved >= poolSize) {
            saved = day ? (todayHash() % 4) : (todayHash() % LIGHT_BASE);
        }
        return (day ? LIGHT_BASE : 0) + saved;
    }

    /** 换肤：在当前时段的池子里循环 */
    public static Palette next(Context ctx) {
        boolean day = isDayNow();
        int poolSize = day ? PALETTES.length - LIGHT_BASE : LIGHT_BASE;
        String key = day ? KEY_DAY : KEY_NIGHT;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int saved = sp.getInt(key, -1);
        if (saved < 0 || saved >= poolSize) {
            saved = day ? (todayHash() % 4) : (todayHash() % LIGHT_BASE);
        }
        saved = (saved + 1) % poolSize;
        sp.edit().putInt(key, saved).commit();
        return PALETTES[(day ? LIGHT_BASE : 0) + saved];
    }

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
