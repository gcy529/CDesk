package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 片名过滤：默认开启，挡住明显的成人内容，避免在车机上误点。
 *
 * 只按标题关键字判断，不看内容、不上传、不联网；设置里可以整体关掉。
 * 刻意只收「明确」的词，避免误杀正经剧名。
 */
public final class ContentFilter {

    private static final String PREF = "cdesk_settings";
    private static final String KEY = "adult_filter";

    private static final String[] WORDS = new String[] {
        "成人", "色情", "情色", "三级片", "无码", "有码", "里番", "肉番",
        "女优", "做爱", "性爱", "巨乳", "裸露", "自慰", "淫", "乱伦",
        "痴汉", "调教", "援交", "约炮", "18禁", "18+", "av片", "av女",
        "h漫", "工口", "露出", "偷拍自拍"
    };

    private ContentFilter() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 默认开启 */
    public static boolean enabled(Context c) {
        try {
            return prefs(c).getBoolean(KEY, true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setEnabled(Context c, boolean on) {
        try {
            prefs(c).edit().putBoolean(KEY, on).commit();
        } catch (Throwable ignored) { }
    }

    /** 标题命中就隐藏 */
    public static boolean blocked(Context c, String name) {
        if (name == null || name.length() == 0) return false;
        if (!enabled(c)) return false;
        String s = name.toLowerCase();
        for (int i = 0; i < WORDS.length; i++) {
            if (s.indexOf(WORDS[i]) >= 0) return true;
        }
        return false;
    }
}
