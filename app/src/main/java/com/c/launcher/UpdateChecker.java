package com.c.launcher;

import android.app.Activity;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 更新检查。
 *
 * 关键修复（0.6.1）：以前任何网络异常都走 onLatest —— 国内连不上 api.github.com 时，
 * 用户点「检查更新」永远看到「已是最新」，等于把故障伪装成正常。
 * 现在网络失败会明确回报失败原因，不再冒充最新。
 *
 * 取版本按顺序试，前三个是国内能通的加速源，最后才回落官方 API：
 *   1. gh-proxy 加速的 raw version.json
 *   2. ghfast 加速的 raw version.json
 *   3. jsDelivr CDN 上的 version.json
 *   4. api.github.com/releases（旧通道，国内多半不通）
 */
public final class UpdateChecker {

    public interface Callback {
        void onUpdate(String remoteVersion, String downloadUrl);
        void onLatest();
        /** 全部源都拿不到时回调；默认静默，避免每日自动检查打扰 */
        default void onError(String reason) { }
    }

    private UpdateChecker() { }

    /** gh-proxy.org 的加速节点；任一不通会自动跳到下一个 */
    private static final String[] MIRRORS = new String[] {
            "https://gh-proxy.org/",
            "https://v4.gh-proxy.org/",
            "https://cdn.gh-proxy.org/",
            "https://axisnow.gh-proxy.org/"
    };

    private static final String VERSION_PATH =
            "https://raw.githubusercontent.com/gcy529/CDesk/beta/version.json";

    private static final String[] ENDPOINTS = buildEndpoints();

    private static String[] buildEndpoints() {
        String[] out = new String[MIRRORS.length + 2];
        for (int i = 0; i < MIRRORS.length; i++) out[i] = MIRRORS[i] + VERSION_PATH;
        out[MIRRORS.length] = "https://cdn.jsdelivr.net/gh/gcy529/CDesk@beta/version.json";
        out[MIRRORS.length + 1] = "https://api.github.com/repos/gcy529/CDesk/releases?per_page=20";
        return out;
    }

    /**
     * 下载链接也走加速：直连 github.com 在国内基本下不动。
     * 用刚才命中的那个镜像包一层；没命中过就默认第一个。
     */
    static String downloadUrl(String url, String mirror) {
        if (url == null) return null;
        if (url.startsWith("https://github.com/")) {
            String m = mirror != null ? mirror : MIRRORS[0];
            return m + url;
        }
        return url;
    }

    /** 一次手动检查最多花这么久，超时就报失败，不无限等 */
    private static final long BUDGET_MS = 15000L;

    private static final String PREFS = "cdesk_update";
    private static final String KEY_LAST = "last_remind_day";
    private static final String KEY_SKIP = "skip_version";

    static final class Res {
        boolean failed;
        String err;
        String ver;
        String url;
        /** 命中的加速镜像前缀，用于给下载链接加速 */
        String mirror;
    }

    private static class Rel {
        String tag;
        String ver;
        String apk;
    }

    public static void checkNow(final Activity act, final Callback cb) {
        fetchAsync(act, cb);
    }

    public static void checkIfNeeded(final Activity act, final Callback cb) {
        final SharedPreferences p = act.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(new java.util.Date());
        if (today.equals(p.getString(KEY_LAST, null))) return;
        if (act.getString(R.string.app_version).equals(p.getString(KEY_SKIP, null))) return;
        fetchAsync(act, cb);
    }

    private static void fetchAsync(final Activity act, final Callback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String cur = act.getString(R.string.app_version);
                final Res res = fetch(cur);
                act.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (res.failed) {
                            cb.onError(res.err == null ? "network" : res.err);
                        } else if (res.ver != null && res.url != null && isNewer(res.ver, cur)) {
                            cb.onUpdate(res.ver, downloadUrl(res.url, res.mirror));
                        } else {
                            cb.onLatest();
                        }
                    }
                });
            }
        }).start();
    }

    /** 依次试所有源，第一个能解析出明确答案的就用 */
    static Res fetch(String cur) {
        long deadline = System.currentTimeMillis() + BUDGET_MS;
        StringBuilder errs = new StringBuilder();
        for (int i = 0; i < ENDPOINTS.length; i++) {
            if (System.currentTimeMillis() > deadline) { errs.append("timeout"); break; }
            String ep = ENDPOINTS[i];
            String body = httpGet(ep);
            if (body == null) { errs.append(hostOf(ep)).append("× "); continue; }
            Res r = parse(body, cur);
            if (r != null) {
                r.mirror = i < MIRRORS.length ? MIRRORS[i] : MIRRORS[0];
                return r;
            }
            errs.append(hostOf(ep)).append("? ");
        }
        Res f = new Res();
        f.failed = true;
        f.err = errs.length() == 0 ? "network" : errs.toString().trim();
        return f;
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost().replace("www.", "");
        } catch (Throwable t) {
            return "url";
        }
    }

    static String httpGet(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(3000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CDesk-updater");
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 256 * 1024) break;
            }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 解析一个响应体；返回 null 表示这个源的内容不可用 */
    static Res parse(String body, String cur) {
        if (body == null) return null;
        String s = body.trim();
        if (s.length() == 0) return null;
        // 我们自己维护的 version.json
        if (s.charAt(0) == '{' && s.contains("\"version\"")) {
            String v = extract(s, "version", 0);
            if (!looksVer(v)) return null;
            Res r = new Res();
            r.ver = v;
            r.url = extract(s, "url", 0);
            return r;
        }
        // GitHub releases 列表
        Rel best = bestRelease(s, cur);
        if (best != null) {
            Res r = new Res();
            r.ver = best.ver;
            r.url = best.apk;
            return r;
        }
        // 合法的 release 列表但没有更新的 → 真·最新
        if (s.contains("\"tag_name\"") || s.equals("[]")) return new Res();
        return null;
    }

    static Rel bestRelease(String json, String cur) {
        if (json == null) return null;
        Rel best = null;
        int i = json.indexOf("\"tag_name\"");
        while (i >= 0) {
            String tag = extract(json, "tag_name", i);
            if (tag != null) {
                int next = json.indexOf("\"tag_name\"", i + 1);
                if (next < 0) next = json.length();
                String apk = findApk(json, i, next);
                String ver = tag.startsWith("v") ? tag.substring(1) : tag;
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

    static String extract(String json, String key, int startAt) {
        String probe = "\"" + key + "\":\"";
        int i = json.indexOf(probe, startAt);
        if (i < 0) return null;
        int s = i + probe.length();
        int e = json.indexOf("\"", s);
        return e > s ? json.substring(s, e) : null;
    }

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
