package com.c.launcher;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * strings.xml 里的 app_version 曾写死 0.4.1，而 build.gradle 的 versionName 已是 0.5.1。
 * UpdateChecker 拿这个字符串比版本，于是永远提示"发现新版本"并指向用户自己装的包。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppVersionConsistencyTest {

    @Test
    public void versionStringMatchesBuildConfig() {
        Application app = RuntimeEnvironment.getApplication();
        assertEquals(
                "R.string.app_version 必须与 BuildConfig.VERSION_NAME 一致，否则更新检查会自指",
                BuildConfig.VERSION_NAME,
                app.getString(R.string.app_version));
    }
}