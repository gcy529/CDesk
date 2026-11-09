package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.fail;

/**
 * 英文环境下真启动每个页面，遍历整棵 View 树，把界面上出现的中文抓出来。
 *
 * 四语 strings.xml 齐全不代表界面就是英文：Java 里 setText("复制错误信息")
 * 这种硬编码绕过资源系统，切到 EN 也照样显示中文。这个测试就是抓这类漏网的。
 *
 * 语言名（简/繁/EN）和公众号专名是故意保留原文的，走白名单。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, qualifiers = "en-rUS")
public class EnglishLocaleTextTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    public void noHardcodedChineseOnScreen() throws Exception {
        Set<String> allowed = new HashSet<String>();
        for (int i = -1; i <= 2; i++) {
            LangManager.setIndex(RuntimeEnvironment.getApplication(), i);
            allowed.add(LangManager.label(RuntimeEnvironment.getApplication()));
        }
        LangManager.setIndex(RuntimeEnvironment.getApplication(), LangManager.FOLLOW);
        allowed.add("韭菜超先生 (JiuCai Chao)");

        PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
        PackageInfo info = pm.getPackageInfo(
                RuntimeEnvironment.getApplication().getPackageName(), PackageManager.GET_ACTIVITIES);
        ActivityInfo[] all = info.activities;

        List<String> problems = new ArrayList<String>();
        for (int i = 0; i < all.length; i++) {
            Class<? extends Activity> cls;
            try {
                cls = Class.forName(all[i].name).asSubclass(Activity.class);
            } catch (Throwable t) {
                continue;   // 启动问题由 ActivityLaunchSmokeTest 负责报
            }
            ActivityController<? extends Activity> controller = null;
            try {
                Intent intent = new Intent(RuntimeEnvironment.getApplication(), cls);
                if (cls == PlayerActivity.class) {
                    intent.putExtra(PlayerActivity.EXTRA_URL, "http://127.0.0.1:1/none.m3u8");
                    intent.putExtra(PlayerActivity.EXTRA_NAME, "Smoke");
                } else if (cls == CrashActivity.class) {
                    intent.putExtra(CrashActivity.EXTRA_MSG, "smoke");
                }
                controller = Robolectric.buildActivity(cls, intent);
                Activity a = controller.setup().get();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                collect(a.getWindow().getDecorView(), cls.getSimpleName(), allowed, problems);
            } catch (Throwable ignored) {
                // 启动崩溃不在本用例职责内
            } finally {
                if (controller != null) {
                    try { controller.pause().stop().destroy(); } catch (Throwable ignored) { }
                }
            }
        }
        if (!problems.isEmpty()) {
            StringBuilder sb = new StringBuilder("英文界面上出现中文（")
                    .append(problems.size()).append(" 处，说明是 Java 硬编码而非资源）:\n");
            for (int i = 0; i < problems.size(); i++) sb.append("  - ").append(problems.get(i)).append('\n');
            fail(sb.toString());
        }
    }

    private static void collect(View v, String page, Set<String> allowed, List<String> out) {
        // 不按 visibility 过滤：控制条、空状态这些平时藏着的控件，一旦露出来同样要是英文
        if (v == null) return;
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            String s = cs == null ? "" : cs.toString();
            if (s.length() > 0 && CJK.matcher(s).find() && !allowed.contains(s)) {
                out.add(page + " / " + idName(v) + " = " + s);
            }
            CharSequence hint = ((TextView) v).getHint();
            String h = hint == null ? "" : hint.toString();
            if (h.length() > 0 && CJK.matcher(h).find() && !allowed.contains(h)) {
                out.add(page + " / " + idName(v) + " hint = " + h);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), page, allowed, out);
        }
    }

    private static String idName(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return v.getClass().getSimpleName();
        try {
            return v.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return v.getClass().getSimpleName() + "#" + id;
        }
    }
}