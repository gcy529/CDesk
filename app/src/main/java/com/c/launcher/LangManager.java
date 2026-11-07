package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * 应用内语言切换：简 / 繁 / EN / 跟随系统，一个按钮循环。
 *
 * 原理：把 Activity 的 baseContext 包一层带指定 Locale 的 Configuration。
 *  - API 17+ 用 createConfigurationContext（官方推荐，互不影响）
 *  - API 16 退回 updateConfiguration（已废弃但能用，minSdk 是 16 必须留着）
 * 切换后 recreate() 重走一遍生命周期即可，不需要重启进程。
 */
public final class LangManager {

    public static final int FOLLOW = -1;
    public static final int SIMP = 0;
    public static final int TRAD = 1;
    public static final int ENG = 2;

    private static final String PREFS = "cdesk_lang";
    private static final String KEY = "lang";

    private LangManager() { }

    public static int index(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, FOLLOW);
    }

    public static void setIndex(Context c, int i) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, i).commit();
    }

    /** 循环切换：跟随系统 -> 简 -> 繁 -> EN -> 跟随系统 */
    public static void cycle(Activity a) {
        int i = index(a);
        if (i == FOLLOW) i = SIMP;
        else if (i == SIMP) i = TRAD;
        else if (i == TRAD) i = ENG;
        else i = FOLLOW;
        setIndex(a, i);
        a.recreate();
    }

    /** 语言按钮上显示的文字（语言名用它自己的文字写，全世界通用） */
    public static String label(Context c) {
        int i = index(c);
        if (i == SIMP) return "简";
        if (i == TRAD) return "繁";
        if (i == ENG) return "EN";
        return c.getString(R.string.lang_auto);
    }

    /** 在每个 Activity 的 attachBaseContext 里调用 */
    public static Context wrap(Context base) {
        int i = index(base);
        Locale loc;
        if (i == SIMP) loc = Locale.SIMPLIFIED_CHINESE;
        else if (i == TRAD) loc = Locale.TAIWAN;
        else if (i == ENG) loc = Locale.ENGLISH;
        else return base;

        if (Build.VERSION.SDK_INT >= 17) {
            Configuration cfg = new Configuration();
            cfg.locale = loc;
            return base.createConfigurationContext(cfg);
        }
        Configuration cfg16 = new Configuration(base.getResources().getConfiguration());
        cfg16.locale = loc;
        base.getResources().updateConfiguration(cfg16, base.getResources().getDisplayMetrics());
        return base;
    }
}
