package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyEvent;

import java.lang.ref.WeakReference;

/**
 * 手机遥控的按键落点。
 *
 * 手机端 HTTP 指令进来后，这里把按键派发到「当前正在前台的 C桌面 页面」。
 * 走的是 Activity.dispatchKeyEvent，也就是系统那条正常按键管线，
 * 所以焦点移动、选中、翻页、返回的行为和物理遥控器完全一致。
 *
 * 边界说明：只能控制 C桌面 自己的界面。向第三方应用注入按键需要无障碍或 root，
 * C桌面 不做越权的事；但手机上的「主页」键随时可以把桌面叫回来。
 */
public final class RemoteKeys {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String LEFT = "LEFT";
    public static final String RIGHT = "RIGHT";
    public static final String OK = "OK";
    public static final String BACK = "BACK";
    public static final String MENU = "MENU";
    public static final String HOME = "HOME";
    public static final String SETTINGS = "SETTINGS";
    public static final String VOL_UP = "VOLUP";
    public static final String VOL_DOWN = "VOLDOWN";
    public static final String MUTE = "MUTE";

    private static WeakReference<Activity> resumed = new WeakReference<Activity>(null);

    private RemoteKeys() { }

    public static void attach(Activity a) {
        if (a != null) resumed = new WeakReference<Activity>(a);
    }

    public static void detach(Activity a) {
        Activity cur = resumed.get();
        if (a != null && cur == a) resumed = new WeakReference<Activity>(null);
    }

    /** @return true 表示指令已被处理 */
    public static boolean dispatch(Context ctx, String code) {
        if (code == null) return false;
        if (HOME.equals(code)) return openHome(ctx);
        if (SETTINGS.equals(code)) return openSettings(ctx);
        if (VOL_UP.equals(code)) return volume(ctx, AudioManager.ADJUST_RAISE);
        if (VOL_DOWN.equals(code)) return volume(ctx, AudioManager.ADJUST_LOWER);
        if (MUTE.equals(code)) return mute(ctx);

        final int keyCode = mapKey(code);
        if (keyCode < 0) return false;

        final Activity a = resumed.get();
        if (a == null || a.isFinishing()) return false;
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                long now = android.os.SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent up = new KeyEvent(now, now + 1, KeyEvent.ACTION_UP, keyCode, 0);
                a.dispatchKeyEvent(down);
                a.dispatchKeyEvent(up);
            }
        });
        return true;
    }

    private static int mapKey(String code) {
        if (UP.equals(code)) return KeyEvent.KEYCODE_DPAD_UP;
        if (DOWN.equals(code)) return KeyEvent.KEYCODE_DPAD_DOWN;
        if (LEFT.equals(code)) return KeyEvent.KEYCODE_DPAD_LEFT;
        if (RIGHT.equals(code)) return KeyEvent.KEYCODE_DPAD_RIGHT;
        if (OK.equals(code)) return KeyEvent.KEYCODE_DPAD_CENTER;
        if (BACK.equals(code)) return KeyEvent.KEYCODE_BACK;
        if (MENU.equals(code)) return KeyEvent.KEYCODE_MENU;
        return -1;
    }

    private static boolean openHome(Context ctx) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(home);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean openSettings(Context ctx) {
        try {
            Intent i = new Intent(ctx, SettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean volume(Context ctx, int direction) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean mute(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            if (Build.VERSION.SDK_INT >= 23) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
            } else {
                am.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
