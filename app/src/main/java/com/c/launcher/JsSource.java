package com.c.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.NativeObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 花音源解析器：跑 lx-music 格式的 .js 源脚本，拿到歌曲列表与播放地址。
 *
 * 花/latest.js 这类源导出的接口形如：
 *   module.exports = {
 *     search: (kw) => [{title, url, extra, artist}],
 *     list: () => [...]
 *   }
 * Rhino 没有 Node 的 module/require，所以先注入 mock module + require，
 * 跑脚本后从 module.exports 读函数。
 *
 * 不做默认 —— 用户填了音源地址才启用（MusicHomeActivity），没填只显示内置电台。
 */
public class JsSource {

    public static class Song {
        public final String title;
        public final String url;
        public final String sub;
        public Song(String t, String u, String s) { title = t; url = u; sub = s; }
    }

    private static final String PREFS = "cdesk_core";
    private static final String KEY_SRC = "music_js_source";

    private String scriptPath;
    private String scriptContent;

    public JsSource() { }

    public static String savedSource(android.content.Context c) {
        return c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY_SRC, "");
    }

    public static void saveSource(android.content.Context c, String url) {
        c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putString(KEY_SRC, url).commit();
    }

    /** 下载花音源脚本并缓存 */
    public void load(String url) throws Exception {
        scriptPath = url;
        scriptContent = fetch(url);
        if (scriptContent == null || scriptContent.length() < 10) {
            throw new Exception("source script empty");
        }
        CLog.i("js source loaded " + url + " bytes=" + scriptContent.length());
    }

    public boolean isLoaded() { return scriptContent != null && scriptContent.length() > 0; }

    public String path() { return scriptPath; }

    /** 拉全列表（无关键词） */
    public List<Song> list() {
        try {
            Context ctx = Context.enter();
            try {
                ctx.setLanguageVersion(Context.VERSION_ES6);
                Scriptable scope = setupScope(ctx);
                ctx.evaluateString(scope, scriptContent, "source.js", 1, null);
                List<Song> out = callList(ctx, scope);
                CLog.i("js source list got=" + out.size());
                return out;
            } finally {
                Context.exit();
            }
        } catch (Throwable t) {
            CLog.e("js source list failed " + t);
            return new ArrayList<Song>();
        }
    }

    /** 搜索关键词 */
    public List<Song> search(String keyword) {
        if (keyword == null || keyword.length() == 0) return list();
        try {
            Context ctx = Context.enter();
            try {
                ctx.setLanguageVersion(Context.VERSION_ES6);
                Scriptable scope = setupScope(ctx);
                ctx.evaluateString(scope, scriptContent, "source.js", 1, null);
                List<Song> out = callSearch(ctx, scope, keyword);
                CLog.i("js source search '" + keyword + "' got=" + out.size());
                return out;
            } finally {
                Context.exit();
            }
        } catch (Throwable t) {
            CLog.e("js source search failed " + t);
            return new ArrayList<Song>();
        }
    }

    /** 注入 module + require + exports mock，跑脚本后读 module.exports */
    private Scriptable setupScope(Context ctx) {
        NativeObject so = new NativeObject();
        Scriptable scope = ScriptableObject.wrapObject(ctx, so, ScriptableObject.getObjectClass(ctx));
        NativeObject module = new NativeObject();
        NativeObject exports = new NativeObject();
        module.put("exports", module, exports);
        scope.put("module", scope, module);
        scope.put("exports", scope, exports);
        // mock require：花音源一般只 export，不 require 第三方
        scope.put("require", scope, new Function() {
            @Override public Scriptable call(Context cx, Scriptable thisObj, Scriptable argsObj, Function data) {
                cx.throwException(new RuntimeException("require not supported in CDesk js source"));
                return null;
            }
            @Override public int getLength() { return 1; }
            @Override public int getArity() { return 1; }
            @Override public String toString() { return "function require() { not supported }"; }
            @Override public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
                cx.throwException(new RuntimeException("require not supported"));
                return null;
            }
        });
        return scope;
    }

    private List<Song> callSearch(Context ctx, Scriptable scope, String keyword) {
        Scriptable exports = (Scriptable) scope.get("exports", scope);
        if (exports == null || ScriptableObject.isNullOrUndefined(exports)) {
            return new ArrayList<Song>();
        }
        Object searchFn = exports.get("search", exports);
        if (searchFn == null || !Function.hasInstance(searchFn)) {
            return new ArrayList<Song>();
        }
        Object r = ((Function) searchFn).call(ctx, scope, new Object[] { keyword }, null);
        return parseResults(r);
    }

    private List<Song> callList(Context ctx, Scriptable scope) {
        Scriptable exports = (Scriptable) scope.get("exports", scope);
        if (exports == null || ScriptableObject.isNullOrUndefined(exports)) {
            return new ArrayList<Song>();
        }
        Object listFn = exports.get("list", exports);
        if (listFn != null && Function.hasInstance(listFn)) {
            Object r = ((Function) listFn).call(ctx, scope, new Object[0], null);
            return parseResults(r);
        }
        // 没有 list 就尝试 search("") 拉全量
        return callSearch(ctx, scope, "");
    }

    private List<Song> parseResults(Object r) {
        List<Song> out = new ArrayList<Song>();
        if (r == null || Context.isPrimitiveOrNull(r)) return out;
        try {
            // Rhino 数组是 NativeArray，遍历取元素；每个元素可能是 NativeObject(对象)或String
            if (r instanceof org.mozilla.javascript.NativeArray) {
                org.mozilla.javascript.NativeArray arr = (org.mozilla.javascript.NativeArray) r;
                int n = arr.getLength();
                for (int i = 0; i < n; i++) {
                    Object item = arr.get(i, arr);
                    Song s = songFromJs(item);
                    if (s != null) out.add(s);
                }
            } else if (r instanceof String) {
                JSONArray arr = new JSONArray((String) r);
                for (int i = 0; i < arr.length(); i++) {
                    Song s = songFromJson(arr, i);
                    if (s != null) out.add(s);
                }
            }
            return out;
        } catch (Throwable t) {
            CLog.e("js source parse failed " + t);
            return out;
        }
    }

    /** 从 Rhino NativeObject 读 title/url/artist */
    private Song songFromJs(Object o) {
        try {
            if (o instanceof String) {
                String u = (String) o;
                return u.length() > 0 ? new Song("Song", u, "") : null;
            }
            if (!(o instanceof Scriptable)) return null;
            Scriptable sc = (Scriptable) o;
            String t = str(sc.get("title", sc));
            String u = str(sc.get("url", sc));
            if (u.length() == 0) u = str(sc.get("source", sc));
            if (u.length() == 0) u = str(sc.get("file", sc));
            String a = str(sc.get("artist", sc));
            if (a.length() == 0) a = str(sc.get("extra", sc));
            if (t.length() == 0 || u.length() == 0) return null;
            return new Song(t, u, a);
        } catch (Throwable ignored) { return null; }
    }

    private Song songFromJson(JSONArray arr, int i) {
        try {
            JSONObject o = arr.getJSONObject(i);
            String t = o.optString("title", "");
            String u = o.optString("url", "");
            if (u.length() == 0) u = o.optString("source", "");
            String a = o.optString("artist", "");
            if (a.length() == 0) a = o.optString("extra", "");
            if (t.length() == 0 || u.length() == 0) return null;
            return new Song(t, u, a);
        } catch (Throwable ignored) { return null; }
    }

    private static String str(Object o) {
        if (o == null || Context.isPrimitiveOrNull(o)) return "";
        try { return Context.toString(o); } catch (Throwable t) { return ""; }
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CDesk");
        c.setInstanceFollowRedirects(true);
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) { sb.append(line).append('\n'); }
        br.close();
        return sb.toString();
    }
}
