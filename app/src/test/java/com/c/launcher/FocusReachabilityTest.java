package com.c.launcher;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 遥控器可达性。
 *
 * 为什么要有这个测试：鼠标是按坐标点的，遥控器是按焦点链走的。
 * 一个控件只要不在焦点链上，鼠标点得到、遥控器永远到不了 ——
 * 而这一点光看代码看不出来，装到车机上才发现就晚了。
 *
 * 做法：把界面按 1280x720 真实布局一遍，从任意一个能聚焦的控件出发，
 * 只按上下左右（focusSearch）做广度优先，看能不能走遍所有可点控件。
 * 走不到的那个会让 CI 直接失败，并打印出它的 id。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class FocusReachabilityTest {

    private static final int W = 1280;
    private static final int H = 720;
    private static final int[] DIRS = {
            View.FOCUS_UP, View.FOCUS_DOWN, View.FOCUS_LEFT, View.FOCUS_RIGHT
    };

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        // 首启的悬浮窗权限弹窗会挡在桌面前面，先标记成已询问过
        app.getSharedPreferences("cdesk_core", Context.MODE_PRIVATE)
                .edit().putBoolean("overlay_asked", true).commit();
        app.getSharedPreferences("cdesk_recent", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void everyClickableControlIsReachableByDpad() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(a);

        List<View> all = new ArrayList<View>();
        collect(a.getWindow().getDecorView(), all);

        assertFalse("一个可聚焦控件都没找到，测试本身失效了", all.isEmpty());

        for (View start : all) {
            Set<View> reached = walk(start);
            List<String> missing = new ArrayList<String>();
            for (View t : all) {
                if (!reached.contains(t)) missing.add(name(t));
            }
            assertTrue("从 " + name(start) + " 出发，按方向键到不了：" + missing
                            + "（这些控件遥控器永远选不中，鼠标却点得到）",
                    missing.isEmpty());
        }
    }

    /** 从 start 出发，只按方向键能走到的所有控件 */
    private Set<View> walk(View start) {
        Set<View> seen = new HashSet<View>();
        ArrayDeque<View> q = new ArrayDeque<View>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            View v = q.poll();
            for (int d : DIRS) {
                View n = v.focusSearch(d);
                if (n != null && seen.add(n)) q.add(n);
            }
        }
        return seen;
    }

    /** 收集所有「可聚焦 + 可见 + 有实际尺寸」的控件：这些才是遥控器该选得中的 */
    private void collect(View v, List<View> out) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        boolean focusable = v.isFocusable() || v.isFocusableInTouchMode();
        if (focusable && v.getWidth() > 0 && v.getHeight() > 0) out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    /** 按目标分辨率真实布局一遍，否则所有控件尺寸都是 0，焦点搜索没有意义 */
    private void layout(MainActivity a) {
        View decor = a.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
    }

    private String name(View v) {
        String n;
        try {
            n = v.getResources().getResourceEntryName(v.getId());
        } catch (Exception e) {
            n = v.getClass().getSimpleName();
        }
        ViewParent p = v.getParent();
        if (p instanceof View && ((View) p).getId() != View.NO_ID) {
            try {
                n = ((View) p).getResources().getResourceEntryName(((View) p).getId()) + "/" + n;
            } catch (Exception ignored) { }
        }
        return n;
    }
}
