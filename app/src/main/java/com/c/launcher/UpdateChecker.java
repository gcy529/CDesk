package com.c.launcher;

import android.app.Activity;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 更新提醒：桌面启动时后台查一次 GitHub latest 版本号。
 *
 *  - 对比 versionName，远端更新 → 状态栏文字提示 + 点击直达下载
 *  - 每天最多打扰一次（记录上次提醒日期）
 *  - 拿不到 / 没网络 / 超时 → 完全静默，绝不弹窗打扰
 *
 * 加速线路：提示语走 ghfast.top 的 latest 通配链接，
 * 发新版后无需改任何代码，永远指向最新 APK。
 */
public final class UpdateChecker {

    public interface Callback {
        void onUpdate(String remoteVersion, String downloadUrl);
    }

    private UpdateChecker() { }

    private static final String API = "https://api.github.com/repos/gcy529/CDesk/releases/latest";
    /** GHFast 的 latest 通配：新版本发布后自动指向新包 */
    private static final String FAST_DL =
            "https://ghfast.top/https://github.com/gcy529/CDesk/releases/latest/download/app-debug.apk";
    private static final String PREFS = "cdesk_update";
    private static final String KEY_LAST = "last_remind_day";
    private static final String KEY_SKIP = "skip_version";

    /** 每天最多提醒一次；用户点过"跳过此版本"则不再提醒该版本 */
    public static void checkIfNeeded(final Activity act, final Callback cb) {
        final SharedPreferences p = act.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(new java.util.Date());
        if (today.equals(p.getString(KEY_LAST, null))) return;
        if (act.getString(R.string.app_version).equals(p.getString(KEY_SKIP, null))) return;

        new Thread(new Runnable() {
            @Override public void run() {
                String remote = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(5000);
                    c.setRequestProperty("User-Agent", "CDesk/" + act.getString(R.string.app_version));
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    InputStream in = c.getInputStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    c.disconnect();
                    String json = sb.toString();
                    remote = extract(json, "tag_name");
                    
                } catch (Throwable ignored) { }
                if (remote == null) return;
                final String ver = remote.startsWith("v") ? remote.substring(1) : remote;
                final String cur = act.getString(R.string.app_version);
                if (!isNewer(ver, cur)) return;

                String today2 = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                        .format(new java.util.Date());
                p.edit().putString(KEY_LAST, today2).commit();

                act.runOnUiThread(new Runnable() {
                    @Override public void run() { cb.onUpdate(ver, FAST_DL); }
                });
            }
        }).start();
    }

    /** 从 JSON 里取 "key":"value" 字符串值（手写解析，不引第三方库） */
    static String extract(String json, String key) {
        String probe = "\"" + key + "\":\"";
        int i = json.indexOf(probe);
        if (i < 0) return null;
        int s = i + probe.length();
        int e = json.indexOf("\"", s);
        return e > s ? json.substring(s, e) : null;
    }

    /** 语义化版本比较：0.2.1 > 0.2.0 */
    static boolean isNewer(String remote, String current) {
        try {
            String[] a = remote.split("\\.");
            String[] b = current.split("\\.");
            int n = Math.max(a.length, b.length);
            for (int i = 0; i < n; i++) {
                int x = i < a.length ? Integer.parseInt(a[i].replaceAll("[^0-9]", "")) : 0;
                int y = i < b.length ? Integer.parseInt(b[i].replaceAll("[^0-9]", "")) : 0;
                if (x != y) return x > y;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
