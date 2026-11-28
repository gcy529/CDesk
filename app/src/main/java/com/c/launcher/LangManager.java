package com.c.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * 应用语言：只做台繁体，不提供简体与其它语言切换。
 *
 * wrap 把 Activity 的 baseContext 固定成台繁体 Locale，
 * 保证系统语言是英文等其它语言时，界面仍显示台繁体资源。
 *  - API 17+ 用 createConfigurationContext（官方推荐，互不影响）
 *  - API 16 退回 updateConfiguration（已废弃但能用，minSdk 是 16 必须留着）
 */
public final class LangManager {

    private LangManager() { }

    /** 在每个 Activity 的 attachBaseContext 里调用 */
    public static Context wrap(Context base) {
        Locale loc = Locale.TAIWAN;

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
