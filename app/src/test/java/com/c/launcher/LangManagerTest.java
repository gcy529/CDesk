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

import static org.junit.Assert.assertEquals;

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
    public void defaultsToSimp() {
        assertEquals(LangManager.SIMP, LangManager.index(app));
    }

    @Test
    public void defaultLocaleIsSimplifiedChinese() {
        assertEquals("zh", LangManager.wrap(app).getResources().getConfiguration().locale.getLanguage());
    }

    @Test
    public void cycleOrder_isSimpTradSimp() {
        assertEquals(LangManager.SIMP, LangManager.index(app));
        LangManager.setIndex(app, LangManager.TRAD);
        assertEquals(LangManager.TRAD, LangManager.index(app));
        LangManager.setIndex(app, LangManager.SIMP);
        assertEquals(LangManager.SIMP, LangManager.index(app));
    }

    @Test
    public void explicitLocale_isApplied() {
        LangManager.setIndex(app, LangManager.SIMP);
        assertEquals("CN", LangManager.wrap(app).getResources().getConfiguration().locale.getCountry());
        LangManager.setIndex(app, LangManager.TRAD);
        assertEquals("TW", LangManager.wrap(app).getResources().getConfiguration().locale.getCountry());
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

        LangManager.setIndex(app, LangManager.TRAD);
        Configuration got = LangManager.wrap(app).getResources().getConfiguration();

        assertEquals("切语言不能改屏幕密度（车机常自定义 densityDpi）", 320, got.densityDpi);
        assertEquals("切语言不能重置用户的大字体设置", 1.3f, got.fontScale, 0.001f);
        assertEquals("TW", got.locale.getCountry());
    }

    @Test
    public void label_marksCurrentLanguage() {
        LangManager.setIndex(app, LangManager.SIMP);
        assertEquals("【简】 繁", LangManager.label(app));
        LangManager.setIndex(app, LangManager.TRAD);
        assertEquals("簡 【繁】", LangManager.label(app));
    }

    @Test
    public void oldEnglishSettingFallsBackToSimp() {
        app.getSharedPreferences("cdesk_lang", Context.MODE_PRIVATE).edit().putInt("lang", 2).commit();
        assertEquals(LangManager.SIMP, LangManager.index(app));
    }

    @Test
    public void setIndex_rejectsUnsupportedValues() {
        LangManager.setIndex(app, 2);
        assertEquals(LangManager.SIMP, LangManager.index(app));
        LangManager.setIndex(app, -1);
        assertEquals(LangManager.SIMP, LangManager.index(app));
    }
}
