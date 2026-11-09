package com.c.launcher;

import android.app.Activity;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 更新提醒：扫 GitHub 全部 release（正式版 + beta 预发布），报最新的那个。
 * 旧版只查 releases/latest —— 该接口永远忽略 beta，导致 0.4.x 用户收不到更新提示。
 * 现在改为遍历 /releases 列表，按版本号比大小；下载直链从 asset 里现取，不再拼死 app-debug.apk。
 *
 *  - 对比 versionName，远端更新 → 状态栏文字提示 + 点击直达下载
 *  - 每天最多打扰一次（记录上次提醒日期）
 *  - 拿不到 / 没网络 / 超时 → 完全静默，绝不弹窗打扰
 */
public final class UpdateChecker {

    public interface Callback {
        void onUpdate(String remoteVersion, String downloadUrl);
        void onLatest();
    }

    private UpdateChecker() { }

    private static final String API = "https://api.github.com/repos/gcy529/CDesk/releases?per_page=20";
    private static final String PREFS = "cdesk_update";
    private static final String KEY_LAST = "last_remind_day";
    private static final String KEY_SKIP = "skip_version";

    /** 每个候选版本 */
    private static class Rel {
        String tag;   // 如 v0.4.2 或 beta
        String ver;   // 纯数字版本 0.4.2
        String apk;   // asset 直链
    }

    /** 设置页「检查更新」用：不受每日一次限制，查完必定回调 */
    public static void checkNow(final Activity act, final Callback cb) {
        fetchAsync(act, cb, true);
    }

    /** 每天最多提醒一次；用户点过"跳过此版本"则不再提醒该版本 */
    public static void checkIfNeeded(final Activity act, final Callback cb) {
        final SharedPreferences p = act.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(new java.util.Date());
        if (today.equals(p.getString(KEY_LAST, null))) return;
        if (act.getString(R.string.app_version).equals(p.getString(KEY_SKIP, null))) return;
        fetchAsync(act, cb, false);
    }

    private static void fetchAsync(final Activity act, final Callback cb, final boolean manual) {
        new Thread(new Runnable() {
            @Override public void run() {
                String json = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                    c.setConnectTimeout(manual ? 6000 : 4000);
                    c.setReadTimeout(manual ? 8000 : 5000);
                    c.setRequestProperty("User-Agent", "CDesk/" + act.getString(R.string.app_version));
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    InputStream in = c.getInputStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    c.disconnect();
                    json = sb.toString();
                } catch (Throwable ignored) { }

                final Rel best = bestRelease(json, act.getString(R.string.app_version));
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (best == null) cb.onLatest();
                        else cb.onUpdate(best.ver, best.apk);
                    }
                });
            }
        }).start();
    }

    /** 在 releases 列表 JSON 里找「比 cur 新的最高版本」，同时把它的 apk 直链带上 */
    static Rel bestRelease(String json, String cur) {
        if (json == null) return null;
        Rel best = null;
        int i = json.indexOf("\"tag_name\"");
        while (i >= 0) {
            String tag = extract(json, "tag_name", i);
            if (tag != null) {
                // 在这个 release 的范围里找第一个 .apk asset 直链
                int next = json.indexOf("\"tag_name\"", i + 1);
                if (next < 0) next = json.length();
                String apk = findApk(json, i, next);
                String ver = tag.startsWith("v") ? tag.substring(1) : tag;
                // beta 渠道 tag 就是 "beta"，版本号从 asset 名 CDesk-0.4.2-beta.apk 里取
                if (!looksVer(ver) && apk != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("CDesk-([0-9.]+?)-beta\\.apk").matcher(apk);
                    if (m.find()) ver = m.group(1);
                }
                if (looksVer(ver) && isNewer(ver, cur) && apk != null) {
                    if (best == null || isNewer(ver, best.ver)) {
                        Rel r2 = new Rel();
                        r2.tag = tag; r2.ver = ver; r2.apk = apk;
                        best = r2;
                    }
                }
            }
            i = json.indexOf("\"tag_name\"", i + 10);
        }
        return best;
    }

    /** 从 [from,to) 区间里取第一个以 .apk 结尾的 browser_download_url */
    private static String findApk(String json, int from, int to) {
        String probe = "\"browser_download_url\":\"";
        int i = json.indexOf(probe, from);
        while (i >= 0 && i < to) {
            int s = i + probe.length();
            int e = json.indexOf("\"", s);
            if (e > s) {
                String u = json.substring(s, e);
                if (u.endsWith(".apk")) return u;
            }
            i = json.indexOf(probe, i + probe.length());
        }
        return null;
    }

    private static boolean looksVer(String s) {
        return s != null && s.matches("[0-9]+(\\.[0-9]+)*");
    }

    /** 从 json 的第 startAt 位起，取 "key":"value" 的 value */
    static String extract(String json, String key, int startAt) {
        String probe = "\"" + key + "\":\"";
        int i = json.indexOf(probe, startAt);
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