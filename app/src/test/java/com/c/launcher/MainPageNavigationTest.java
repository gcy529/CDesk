package com.c.launcher;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 桌面分页与影音入口。
 *
 * 页序按首页重设计 v2：最近 / 应用 / 影音，默认落「最近」。
 * 影音页每张卡都必须真的打开一个页面——这是「入口做好了没有」的直接验证，
 * 而不是看界面上有没有画那张卡。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class MainPageNavigationTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        // 跳过首启的悬浮窗权限弹窗，否则它会混进「点了卡片之后启动了什么」的断言里
        app.getSharedPreferences("cdesk_core", Context.MODE_PRIVATE)
                .edit().putBoolean("overlay_asked", true).commit();
        app.getSharedPreferences("cdesk_recent", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private MainActivity launch() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class);
        MainActivity a = c.setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        drain(a);
        return a;
    }

    /** 启动过程本身可能发权限/更新意图，清掉，只留后面点击产生的 */
    private static void drain(MainActivity a) {
        while (Shadows.shadowOf(a).getNextStartedActivity() != null) { }
        while (Shadows.shadowOf(a).getNextStartedActivityForResult() != null) { }
    }

    private static String title(MainActivity a) {
        return ((TextView) a.findViewById(R.id.tvPageTitle)).getText().toString();
    }

    private static Button next(MainActivity a) {
        return (Button) a.findViewById(R.id.btnPageNext);
    }

    @Test
    public void defaultPageIsRecent() {
        MainActivity a = launch();
        assertEquals("默认应落在「最近」", a.getString(R.string.page_recent), title(a));
    }

    @Test
    public void pageOrderIsRecentAppsMediaAndWraps() {
        MainActivity a = launch();
        assertEquals(a.getString(R.string.page_recent), title(a));
        next(a).performClick();
        assertEquals(a.getString(R.string.page_apps), title(a));
        next(a).performClick();
        assertEquals(a.getString(R.string.page_media), title(a));
        next(a).performClick();
        assertEquals("末页再下一页应绕回首页", a.getString(R.string.page_recent), title(a));
    }

    @Test
    public void mediaGridColumnsMatchAdapter() {
        MainActivity a = launch();
        next(a).performClick();
        next(a).performClick();
        GridView grid = (GridView) a.findViewById(R.id.gridApps);
        MediaAdapter ma = new MediaAdapter(a);
        assertEquals("列数必须与适配器同源，否则 DPAD 边界判断和实际布局会错位",
                ma.columns(), grid.getNumColumns());
        assertEquals(ma.getCount(), grid.getAdapter().getCount());
    }

    @Test
    public void everyMediaCardOpensItsShell() {
        MainActivity a = launch();
        next(a).performClick();
        next(a).performClick();
        GridView grid = (GridView) a.findViewById(R.id.gridApps);
        MediaAdapter ma = new MediaAdapter(a);
        Class<?>[] expected = new Class<?>[] {
                LiveShellActivity.class, VodShellActivity.class,
                MusicShellActivity.class, LibraryActivity.class };

        assertEquals("卡片数与路由表必须一样长", expected.length, ma.getCount());
        for (int pos = 0; pos < ma.getCount(); pos++) {
            drain(a);
            assertTrue("第 " + pos + " 张卡应可点击", grid.performItemClick(null, pos, pos));
            Intent started = Shadows.shadowOf(a).getNextStartedActivity();
            assertNotNull("第 " + pos + " 张卡（" + ma.typeAt(pos) + "）点了必须打开页面", started);
            assertNotNull(started.getComponent());
            assertEquals(expected[pos].getName(), started.getComponent().getClassName());
        }
    }

    /** 空「最近」页不能把遥控器困住：左右键必须能翻到应用页 */
    @Test
    public void emptyRecentPageStillAllowsPaging() {
        MainActivity a = launch();
        GridView grid = (GridView) a.findViewById(R.id.gridApps);
        assertEquals("清掉历史后最近页应为空", 0, grid.getAdapter().getCount());
        assertEquals(View.VISIBLE, a.findViewById(R.id.tvPageHint).getVisibility());
        next(a).performClick();
        assertEquals(a.getString(R.string.page_apps), title(a));
    }
}