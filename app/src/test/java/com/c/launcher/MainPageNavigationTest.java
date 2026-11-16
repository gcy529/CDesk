package com.c.launcher;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
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
 * 桌面分页。
 *
 * 页序为应用 / 最近两页，默认落「应用」；末页再下一页绕回首页。
 * 0.3 稳定版去掉了手动翻页按钮，翻页只靠横向滑动与遥控器方向键，
 * 所以这里用真实滑动（dispatchTouchEvent）驱动翻页，校验链路真的通了、入口真的能点开。
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
        // 每次用例回到应用页，避免上一次用例记住的页号串场
        app.getSharedPreferences("cdesk_core", Context.MODE_PRIVATE)
                .edit().remove("page_last").commit();
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

    /**
     * 横向滑动翻页。toNext=true 向左滑（下一页），false 向右滑（回到上一页）。
     * 位移必须明显超过 60dp 阈值且横向占优，否则会被当成普通点击。
     */
    private static void swipePage(MainActivity a, boolean toNext) {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int y = dm.heightPixels / 2;
        int x0 = dm.widthPixels / 2;
        int delta = (int) (60 * dm.density) + 200;
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y, 0);
        a.dispatchTouchEvent(down);
        MotionEvent up = MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP,
                toNext ? x0 - delta : x0 + delta, y, 0);
        a.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void defaultPageIsApps() {
        MainActivity a = launch();
        assertEquals("默认应落在「应用」", a.getString(R.string.page_apps), title(a));
    }

    @Test
    public void pageOrderIsAppsRecentAndWraps() {
        MainActivity a = launch();
        assertEquals(a.getString(R.string.page_apps), title(a));
        swipePage(a, true);
        assertEquals(a.getString(R.string.page_recent), title(a));
        swipePage(a, true);
        assertEquals("末页再下一页应绕回首页", a.getString(R.string.page_apps), title(a));
    }

    @Test
    public void appsGridUsesAppAdapterAndColumnsMatchLayout() {
        MainActivity a = launch();
        GridView grid = (GridView) a.findViewById(R.id.gridApps);
        AppAdapter adapter = new AppAdapter(a);
        assertEquals("应用页必须用 AppAdapter", AppAdapter.class, grid.getAdapter().getClass());
        // API 31 起 setNumColumns 只写"请求列数"，getNumColumns 返回布局后的生效值；
        // Robolectric 不跑自动布局，手动 measure + layout 一次让 onMeasure 同步两值。
        int w = a.getResources().getDisplayMetrics().widthPixels;
        grid.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, w, 600);
        int cols = grid.getNumColumns();
        if (cols != GridView.AUTO_FIT) {
            assertTrue("列数必须落在 2~6，否则 DPAD 边界判断和实际布局会错位: grid=" + cols,
                    cols >= 2 && cols <= 6);
        }
        assertEquals(adapter.getCount(), grid.getAdapter().getCount());
    }

    @Test
    public void tappingSelfCardOpensHomeChooser() {
        MainActivity a = launch();
        GridView grid = (GridView) a.findViewById(R.id.gridApps);
        assertEquals("自己应排在应用列表首位，作为切默认桌面的入口",
                a.getPackageName(), ((AppAdapter.AppItem) grid.getAdapter().getItem(0)).pkg);
        drain(a);
        assertTrue("点应用卡片必须能打开页面", grid.performItemClick(null, 0, 0));
        Intent started = Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull("点自己那一下必须启动系统桌面选择器", started);
        assertEquals(Intent.ACTION_MAIN, started.getAction());
        assertTrue(started.hasCategory(Intent.CATEGORY_HOME));
    }

    /** 空「最近」页不能把遥控器困住：翻页必须能回到应用页 */
    @Test
    public void emptyRecentPageStillAllowsPaging() {
        MainActivity a = launch();
        GridView grid = (GridView) a.findViewById(R.id.gridApps);
        swipePage(a, true);
        assertEquals("清掉历史后最近页应为空", 0, grid.getAdapter().getCount());
        assertEquals(View.VISIBLE, a.findViewById(R.id.tvPageHint).getVisibility());
        swipePage(a, false);
        assertEquals(a.getString(R.string.page_apps), title(a));
    }
}
