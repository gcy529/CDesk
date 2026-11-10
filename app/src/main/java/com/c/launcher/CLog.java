package com.c.launcher;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 轻量日志：同时写 logcat 和 app 私有文件，出问题时用户可一键复制发给我。
 * 只记关键节点（启动 / 崩溃 / 页面切换 / 加载失败），不刷屏。
 */
public final class CLog {

    private static final String TAG = "CDesk";
    private static final int MAX = 8000;   // 最多保留字节数
    private static File dir;

    private CLog() { }

    public static void init(Context c) {
        try { dir = new File(c.getFilesDir(), "log"); dir.mkdirs(); }
        catch (Throwable ignored) { }
    }

    public static void i(String msg) {
        try {
            Log.i(TAG, msg);
            append("I", msg);
        } catch (Throwable ignored) { }
    }

    public static void e(String msg) {
        try {
            Log.e(TAG, msg);
            append("E", msg);
        } catch (Throwable ignored) { }
    }

    public static void e(String msg, Throwable t) {
        try {
            Log.e(TAG, msg, t);
            append("E", msg + " | " + t);
        } catch (Throwable ignored) { }
    }

    private static synchronized void append(String lvl, String msg) {
        if (dir == null) return;
        try {
            File f = new File(dir, "cdesk.log");
            long size = f.exists() ? f.length() : 0;
            FileOutputStream fos = new FileOutputStream(f, size < MAX);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            w.write(ts + " " + lvl + " " + msg + "\n");
            w.close();
            // 超长截断：保留尾部
            if (f.length() > MAX) {
                byte[] all = new byte[(int) f.length()];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int got = in.read(all); in.close();
                int cut = Math.max(0, got - MAX / 2);
                FileOutputStream fo = new FileOutputStream(f, false);
                fo.write(all, cut, got - cut); fo.close();
            }
        } catch (Throwable ignored) { }
    }

    /** 读日志文本（设置页「复制日志」用） */
    public static String read() {
        if (dir == null) return "";
        try {
            File f = new File(dir, "cdesk.log");
            if (!f.exists()) return "";
            byte[] all = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int got = in.read(all); in.close();
            return new String(all, 0, Math.max(0, got), "UTF-8");
        } catch (Throwable t) { return ""; }
    }

    /** 设备信息 + 日志，方便发给我定位 */
    public static String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("CDesk 诊断报告\n");
        sb.append("App: ").append(appVer()).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("机型: ").append(Build.MODEL).append(" / ").append(Build.MANUFACTURER).append("\n");
        sb.append("--- 日志 ---\n");
        sb.append(read());
        return sb.toString();
    }

    /** 真实版本号：取自编译期生成的 BuildConfig，不再写死（之前写死 0.5.0 导致报告永远显示 0.5.0） */
    private static String appVer() {
        try {
            return BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
        } catch (Throwable t) { return "?"; }
    }
}