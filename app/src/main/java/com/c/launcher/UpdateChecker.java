package com.c.launcher;

import android.app.Activity;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        void onLatest(String remoteVersion);
        /** 全部源都拿不到时回调；默认静默，避免每日自动检查打扰 */
        default void onError(String reason) { }
        /**
         * 服务端在 version.json 里标了 "force":"true" 才走这里：这次不更新就用不了。
         * 开关放服务器而不是写死在代码里 —— 万一哪天下载线路全挂了，
         * 改一下 json 就能把人放出来，不用等用户卸载重装。
         */
        default void onForceUpdate(String remoteVersion, String downloadUrl) {
            onUpdate(remoteVersion, downloadUrl);
        }
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
        // 只认 beta 这一个 release：以前这里是 releases 列表，会按 tag 名挑"最新"，
        // 而老 release 的 tag（v0.5.1）和里面那个 app-debug.apk 的实际版本根本不是一回事，
        // 结果就是更新下回来一个 0.6.1 的旧包。
        out[MIRRORS.length + 1] = "https://api.github.com/repos/gcy529/CDesk/releases/tags/beta";
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
        boolean force;
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

    /** 同一条下载地址的所有加速线路；应用内下载失败就自动换下一条 */
    public static String[] mirrorUrls(String url) {
        if (url == null || url.length() == 0) { return new String[0]; }
        if (!url.startsWith("https://github.com/")) { return new String[] { url }; }
        String[] out = new String[MIRRORS.length];
        for (int i = 0; i < MIRRORS.length; i++) { out[i] = MIRRORS[i] + url; }
        return out;
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
                            if (res.force) { cb.onForceUpdate(res.ver, downloadUrl(res.url, res.mirror)); }
                            else { cb.onUpdate(res.ver, downloadUrl(res.url, res.mirror)); }
                        } else {
                            cb.onLatest(res.ver);
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
            try {
                JSONObject obj = new JSONObject(s);
                String v = stringField(obj, "version");
                if (!looksVer(v)) return null;
                Res r = new Res();
                r.ver = v;
                r.url = stringField(obj, "url");
                r.force = "true".equalsIgnoreCase(stringField(obj, "force"));
                return r;
            } catch (JSONException e) {
                return null;
            }
        }
        // GitHub releases 列表
        Rel best = bestReleaseJson(s, cur);
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

    /**
     * 解析 GitHub releases 数组（JSONArray）。逻辑与旧版字符串扫描等价：
     * 只认 browser_download_url 以 .apk 结尾的下载链接，排除 app-debug 通用名，
     * 版本按 tag 去 v 前缀；若版本不是数字形式，从 apk 文件名按 CDesk-版本-beta.apk 纠正。
     */
    static Rel bestReleaseJson(String json, String cur) {
        if (json == null) return null;
        try {
            JSONArray arr = new JSONArray(json);
            Rel best = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String tag = stringField(o, "tag_name");
                String apk = stringField(o, "browser_download_url");
                // 只认 .apk 且非 app-debug 通用名
                if (apk != null && apk.endsWith(".apk") && !apk.endsWith("app-debug.apk")) apk = apk;
                else apk = null;
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
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksVer(String s) {
        return s != null && s.matches("[0-9]+(\\.[0-9]+)*");
    }

    /** 从 JSONObject 取字段值；数组取第一个字符串元素，其余返回空串 */
    private static String stringField(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v == null) return "";
        if (v instanceof String) return (String) v;
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            try {
                if (a.length() > 0 && a.get(0) instanceof String) return (String) a.get(0);
            } catch (JSONException e) {
                return "";
            }
        }
        return "";
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
