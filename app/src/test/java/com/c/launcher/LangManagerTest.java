package com.c.launcher;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class LangManagerTest {

    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("cdesk_lang", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void defaultsToFollowSystem() {
        assertEquals("默认必须跟随系统，否则英文用户开机就是中文",
                LangManager.FOLLOW, LangManager.index(app));
    }

    @Test
    public void followSystem_doesNotOverrideLocale() {
        assertSame("FOLLOW 时应原样返回 base，交给系统语言决定",
                app, LangManager.wrap(app));
    }

    @Test
    public void cycleOrder_isFollowSimpTradEngFollow() {
        assertEquals(LangManager.FOLLOW, LangManager.index(app));
        LangManager.setIndex(app, next(LangManager.index(app)));
        assertEquals(LangManager.SIMP, LangManager.index(app));
        LangManager.setIndex(app, next(LangManager.index(app)));
        assertEquals(LangManager.TRAD, LangManager.index(app));
        LangManager.setIndex(app, next(LangManager.index(app)));
        assertEquals(LangManager.ENG, LangManager.index(app));
        LangManager.setIndex(app, next(LangManager.index(app)));
        assertEquals(LangManager.FOLLOW, LangManager.index(app));
    }

    /** cycle() 会调 recreate()，单测里没有 Activity 栈，这里只验它写下的序号序列 */
    private static int next(int i) {
        if (i == LangManager.FOLLOW) return LangManager.SIMP;
        if (i == LangManager.SIMP) return LangManager.TRAD;
        if (i == LangManager.TRAD) return LangManager.ENG;
        return LangManager.FOLLOW;
    }

    @Test
    public void explicitLocale_isApplied() {
        LangManager.setIndex(app, LangManager.ENG);
        assertEquals(Locale.ENGLISH, LangManager.wrap(app).getResources().getConfiguration().locale);
        LangManager.setIndex(app, LangManager.SIMP);
        assertEquals(Locale.SIMPLIFIED_CHINESE, LangManager.wrap(app).getResources().getConfiguration().locale);
        LangManager.setIndex(app, LangManager.TRAD);
        assertEquals(Locale.TAIWAN, LangManager.wrap(app).getResources().getConfiguration().locale);
    }

    /**
     * 切语言只该改语言，不该顺手把屏幕密度和字体缩放重置掉。
     * API 16 分支拷贝了原配置，API 17+ 分支却 new 了一个空 Configuration——
     * 车机普遍改过 densityDpi，用户也常开大字体，切一次语言不该把这些抹掉。
     */
    @Test
    @SuppressWarnings("deprecation")
    public void wrap_preservesDensityAndFontScale() {
        Configuration base = app.getResources().getConfiguration();
        base.densityDpi = 320;
        base.fontScale = 1.3f;
        app.getResources().updateConfiguration(base, app.getResources().getDisplayMetrics());

        LangManager.setIndex(app, LangManager.ENG);
        Configuration got = LangManager.wrap(app).getResources().getConfiguration();

        assertEquals("切语言不能改屏幕密度（车机常自定义 densityDpi）", 320, got.densityDpi);
        assertEquals("切语言不能重置用户的大字体设置", 1.3f, got.fontScale, 0.001f);
        assertEquals(Locale.ENGLISH, got.locale);
    }

    @Test
    public void label_marksCurrentLanguage() {
        LangManager.setIndex(app, LangManager.FOLLOW);
        assertEquals("简·繁·EN", LangManager.label(app));
        LangManager.setIndex(app, LangManager.SIMP);
        assertEquals("【简】 繁 EN", LangManager.label(app));
        LangManager.setIndex(app, LangManager.TRAD);
        assertEquals("简 【繁】 EN", LangManager.label(app));
        LangManager.setIndex(app, LangManager.ENG);
        assertEquals("简 繁 【EN】", LangManager.label(app));
    }
}