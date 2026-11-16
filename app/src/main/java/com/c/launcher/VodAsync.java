package com.c.launcher;

import android.app.Activity;

/** 点播各页共用：后台取数据，主线程给结果。失败必须把原因带回界面。 */
public final class VodAsync {
    public interface Job<T> { T run() throws Exception; }
    public interface Done<T> { void ok(T value); void fail(String reason); }

    private VodAsync() { }

    public static <T> void go(final Activity a, final Job<T> job, final Done<T> done) {
        new Thread(new Runnable() {
            @Override public void run() {
                T value = null;
                String err = null;
                try {
                    value = job.run();
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName() + ": " + t.getMessage();
                }
                final T v = value;
                final String e = err;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (a.isFinishing()) { return; }
                        if (e != null) { done.fail(e); } else { done.ok(v); }
                    }
                });
            }
        }).start();
    }
}
