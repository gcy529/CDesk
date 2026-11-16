package com.c.launcher;

import android.content.Intent;
import android.os.Looper;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 列表行必须真的能打开——这是影音壳的「入口」。
 *
 * setItemsCanFocus(true) + 行 setClickable(true) 但没有 OnClickListener 时，
 * 触摸和遥控器 DPAD_CENTER 都被行自己吃掉，ListView 的 OnItemClickListener
 * 永远不触发：看得见台，点不动。performClick() 正是这两条输入路径最终汇聚的地方。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ShellRowActivationTest {

    private LiveShellActivity launch() {
        ActivityController<LiveShellActivity> c = Robolectric.buildActivity(LiveShellActivity.class);
        LiveShellActivity a = c.setup().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    @Test
    public void channelRow_clickOpensPlayer() {
        LiveShellActivity a = launch();
        a.items.add(new PlaylistParser.Item("测试台", "央视", "http://example.com/1.m3u8"));
        a.adapter.notifyDataSetChanged();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View row = a.adapter.getView(0, null, a.lvItems);
        assertTrue("行必须自己接住点击，否则 ListView 的 OnItemClickListener 不会触发",
                row.performClick());

        Intent started = Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull("点频道应打开播放器", started);
        assertEquals(PlayerActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("http://example.com/1.m3u8", started.getStringExtra(PlayerActivity.EXTRA_URL));
        assertEquals("测试台", started.getStringExtra(PlayerActivity.EXTRA_NAME));
        assertTrue(started.getBooleanExtra(PlayerActivity.EXTRA_LIVE, false));
    }

    @Test
    public void groupHeader_isNotClickable() {
        LiveShellActivity a = launch();
        a.items.add(new PlaylistParser.Item("央视", "央视", null));
        a.adapter.notifyDataSetChanged();

        View header = a.adapter.getView(0, null, a.lvItems);
        assertFalse("分组头不该可点", header.isClickable());
        assertFalse("分组头不该抢遥控器焦点", header.isFocusable());
        assertFalse(header.performClick());
    }

    /** 复用 convertView 时，上一行的可点击状态不能漏到分组头上 */
    @Test
    public void recycledRow_doesNotLeakClickabilityIntoHeader() {
        LiveShellActivity a = launch();
        a.items.add(new PlaylistParser.Item("测试台", "央视", "http://example.com/1.m3u8"));
        a.items.add(new PlaylistParser.Item("卫视", "卫视", null));
        a.adapter.notifyDataSetChanged();

        View row = a.adapter.getView(0, null, a.lvItems);
        assertTrue(row.isClickable());
        View header = a.adapter.getView(1, row, a.lvItems);
        assertFalse("复用后分组头仍然不可点", header.isClickable());
        assertFalse("复用后分组头仍然不聚焦", header.isFocusable());
        assertFalse(header.performClick());
    }

    /** 反过来：分组头复用成频道行时，点击必须重新接上 */
    @Test
    public void recycledHeader_becomesClickableChannel() {
        LiveShellActivity a = launch();
        a.items.add(new PlaylistParser.Item("央视", "央视", null));
        a.items.add(new PlaylistParser.Item("测试台", "央视", "http://example.com/1.m3u8"));
        a.adapter.notifyDataSetChanged();

        View header = a.adapter.getView(0, null, a.lvItems);
        View row = a.adapter.getView(1, header, a.lvItems);
        assertTrue("复用后频道行必须可聚焦", row.isFocusable());
        assertTrue("复用后频道行必须能点", row.performClick());
        assertNotNull(Shadows.shadowOf(a).getNextStartedActivity());
    }

    /** 分组头插入规则：组名变化才插一个 url=null 的头，空分组不插 */
    @Test
    public void withGroups_insertsHeaderOnlyWhenGroupChanges() {
        List<PlaylistParser.Item> src = new ArrayList<PlaylistParser.Item>();
        src.add(new PlaylistParser.Item("CCTV1", "央视", "http://example.com/1"));
        src.add(new PlaylistParser.Item("CCTV2", "央视", "http://example.com/2"));
        src.add(new PlaylistParser.Item("湖南", "卫视", "http://example.com/3"));
        src.add(new PlaylistParser.Item("无分组", "", "http://example.com/4"));
        List<PlaylistParser.Item> out = new ArrayList<PlaylistParser.Item>();
        BaseShellActivity.withGroups(src, out);

        assertEquals(6, out.size());
        assertNull("央视分组头", out.get(0).url);
        assertEquals("央视", out.get(0).name);
        assertEquals("CCTV1", out.get(1).name);
        assertEquals("CCTV2", out.get(2).name);
        assertNull("卫视分组头", out.get(3).url);
        assertEquals("湖南", out.get(4).name);
        assertEquals("空分组不该插头", "无分组", out.get(5).name);
        assertNotNull(out.get(5).url);
    }
}