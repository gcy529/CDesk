package com.c.launcher;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 进程入口：把日志和崩溃兜底提前到「任何 Activity 之前」。
 *
 * 之前这两件事都写在 MainActivity.onCreate 里，结果：
 *  1. 启动阶段（Application / 资源加载）崩溃一行日志都留不下；
 *  2. 崩溃页 startActivity 之后紧接着 killProcess，页面刚画出来就把自己杀了 ——
 *     用户根本来不及截图，这就是「要看手速」的原因。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CLog.init(this);
        CLog.i("App onCreate");
        installCrashHandler();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    CLog.e("CRASH " + t.getName(), e);
                } catch (Throwable ignored) { }

                String report = buildReport(t, e);
                try {
                    Intent i = new Intent(App.this, CrashActivity.class);
                    i.putExtra(CrashActivity.EXTRA_MSG, report);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                } catch (Throwable ignored) { }

                // 关键：不立刻 killProcess。崩溃页要留在屏幕上让人慢慢看、慢慢截图，
                // 用户点「退出」时 CrashActivity 自己再杀进程。
                try {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                if (prev != null) { /* 交给系统收尾 */ }
                            } catch (Throwable ignored) { }
                        }
                    }, 1000L);
                } catch (Throwable ignored) { }
            }
        });
    }

    private static String buildReport(Thread t, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("CRASH ").append(t == null ? "?" : t.getName()).append("\n");
        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }
        sb.append("\n--- CLog ---\n");
        try { sb.append(CLog.read()); } catch (Throwable ignored) { }
        return sb.toString();
    }
}
