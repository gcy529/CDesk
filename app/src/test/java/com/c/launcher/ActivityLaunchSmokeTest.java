package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 全组件启动冒烟。
 *
 * 功能是一次性写出来的，没有逐页验证过，所以这里不手写页面清单：
 * 直接从 manifest 读出所有声明的 Activity 逐个真启动，新增页面自动纳入覆盖。
 * 失败不提前退出，收集完整清单一次报出来——一轮 CI 就能看到全部坏页，
 * 而不是修一个跑一次。
 *
 * sdk 31 = Android 12，用户实测机型之一，也是 PendingIntent 强制 FLAG_IMMUTABLE 的起点。
 * 低于 21 的 API 误用由 lint NewApi 单独枚举（Robolectric 4.x 已不支持 sdk<=18）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ActivityLaunchSmokeTest {

    @Test
    public void everyDeclaredActivity_launchesAndTearsDown() throws Exception {
        PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
        PackageInfo info = pm.getPackageInfo(
                RuntimeEnvironment.getApplication().getPackageName(), PackageManager.GET_ACTIVITIES);
        ActivityInfo[] all = info.activities;
        assertTrue("manifest 应至少声明一个 Activity", all != null && all.length > 0);

        List<String> failures = new ArrayList<String>();
        for (int i = 0; i < all.length; i++) {
            String name = all[i].name;
            Class<? extends Activity> cls;
            try {
                cls = Class.forName(name).asSubclass(Activity.class);
            } catch (Throwable t) {
                failures.add(name + " → 类加载失败: " + describe(t));
                continue;
            }
            ActivityController<? extends Activity> controller = null;
            try {
                controller = Robolectric.buildActivity(cls, intentFor(cls));
                controller.setup();
                // post 出去的 Runnable 才是真正干活的地方，必须跑掉才算启动完成
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            } catch (Throwable t) {
                failures.add(name + " → 启动崩溃: " + describe(t));
            } finally {
                if (controller != null) {
                    try {
                        controller.pause().stop().destroy();
                        Shadows.shadowOf(Looper.getMainLooper()).idle();
                    } catch (Throwable t) {
                        failures.add(name + " → 销毁崩溃: " + describe(t));
                    }
                }
            }
        }
        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(failures.size()).append('/').append(all.length).append(" 个页面有问题:\n");
            for (int i = 0; i < failures.size(); i++) sb.append("  - ").append(failures.get(i)).append('\n');
            fail(sb.toString());
        }
    }

    @Test
    public void speedService_createsAndDestroys() {
        ServiceController<SpeedService> controller = null;
        try {
            controller = Robolectric.buildService(SpeedService.class);
            controller.create().startCommand(0, 0);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        } catch (Throwable t) {
            fail("SpeedService 启动崩溃: " + describe(t));
        } finally {
            if (controller != null) {
                try {
                    controller.destroy();
                } catch (Throwable t) {
                    fail("SpeedService 销毁崩溃: " + describe(t));
                }
            }
        }
    }

    private static Intent intentFor(Class<? extends Activity> cls) {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), cls);
        if (cls == PlayerActivity.class) {
            i.putExtra(PlayerActivity.EXTRA_URL, "http://127.0.0.1:1/none.m3u8");
            i.putExtra(PlayerActivity.EXTRA_NAME, "冒烟测试");
            i.putExtra(PlayerActivity.EXTRA_LIVE, true);
        } else if (cls == CrashActivity.class) {
            i.putExtra(CrashActivity.EXTRA_MSG, "smoke test");
        }
        return i;
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        StackTraceElement[] st = root.getStackTrace();
        String where = st.length > 0 ? " @ " + st[0] : "";
        return root.getClass().getName() + ": " + root.getMessage() + where;
    }
}