package com.c.launcher;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 音乐壳必须能打开。buildControls() 曾把 lvItems 的父布局强转成 LinearLayout，
 * 而 activity_playlist.xml 里它的父级是 FrameLayout，导致 onCreate 直接 ClassCastException。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicShellActivityTest {

    @Test
    public void launch_doesNotCrash() {
        ActivityController<MusicShellActivity> controller =
                Robolectric.buildActivity(MusicShellActivity.class);
        MusicShellActivity activity = controller.setup().get();
        assertNotNull(activity);
        assertTrue("Activity 应处于可用状态", !activity.isFinishing());
    }

    @Test
    public void launch_showsListAndControls() {
        MusicShellActivity activity =
                Robolectric.buildActivity(MusicShellActivity.class).setup().get();
        assertNotNull("列表应存在", activity.findViewById(R.id.lvItems));
        assertNotNull("地址输入框应存在", activity.findViewById(R.id.etSource));
        assertNotNull("加载按钮应存在", activity.findViewById(R.id.btnLoad));
    }

    @Test
    public void destroy_releasesPlayer() {
        ActivityController<MusicShellActivity> controller =
                Robolectric.buildActivity(MusicShellActivity.class).setup();
        controller.pause().stop().destroy();
        assertNotNull(controller.get());
    }
}