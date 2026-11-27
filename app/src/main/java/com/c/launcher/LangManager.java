package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * 应用内语言切换：简体 / 繁体，一个按钮循环。
 *
 * 原理：把 Activity 的 baseContext 包一层带指定 Locale 的 Configuration。
 *  - API 17+ 用 createConfigurationContext（官方推荐，互不影响）
 *  - API 16 退回 updateConfiguration（已废弃但能用，minSdk 是 16 必须留着）
 * 切换后 recreate() 重走一遍生命周期即可，不需要重启进程。
 */
public final class LangManager {

    public static final int SIMP = 0;
    public static final int TRAD = 1;

    private static final String PREFS = "cdesk_lang";
    private static final String KEY = "lang";

    private LangManager() { }

    public static int index(Context c) {
        int saved = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SIMP);
        // 旧版英文与跟随系统的设置统一回退到简体。
        return saved == TRAD ? TRAD : SIMP;
    }

    public static void setIndex(Context c, int i) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, i == TRAD ? TRAD : SIMP).commit();
    }

    /** 在简体和繁体之间切换。 */
    public static void cycle(Activity a) {
        setIndex(a, index(a) == SIMP ? TRAD : SIMP);
        a.recreate();
    }

    /** 显示两个选项，标出当前语言。 */
    public static String label(Context c) {
        return index(c) == TRAD ? "簡 【繁】" : "【简】 繁";
    }

    /** 在每个 Activity 的 attachBaseContext 里调用 */
    public static Context wrap(Context base) {
        Locale loc = index(base) == TRAD ? Locale.TAIWAN : Locale.SIMPLIFIED_CHINESE;

        if (Build.VERSION.SDK_INT >= 17) {
            // 必须拷贝原配置：空 Configuration 会把 fontScale 等已设字段一起覆盖掉，
            // 切一次语言就抹掉用户的大字体设置。下面的 API 16 分支本来就是拷贝的。
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.setLocale(loc);
            return base.createConfigurationContext(cfg);
        }
        Configuration cfg16 = new Configuration(base.getResources().getConfiguration());
        cfg16.locale = loc;
        base.getResources().updateConfiguration(cfg16, base.getResources().getDisplayMetrics());
        return base;
    }
}
