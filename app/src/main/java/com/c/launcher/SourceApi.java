package com.c.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 影视源接口客户端（壳子）。
 *
 * 只认「苹果CMS / MacCMS」这一类通用采集接口，用户自己填地址，本应用不内置、不推荐、不分发任何源：
 *   {base}?ac=list                        分类
 *   {base}?ac=videolist&t={type}&pg={n}   列表
 *   {base}?ac=detail&ids={id}             详情与播放地址
 *
 * 全部手写解析，不依赖第三方库；网络异常一律抛给调用方显示，不静默吞掉。
 */
public final class SourceApi {

    public static final class Cat {
        public final String id;
        public final String name;
        public Cat(String id, String name) { this.id = id; this.name = name; }
    }

    public static final class Video {
        public final String id;
        public final String name;
        public final String pic;
        public final String remarks;
        public Video(String id, String name, String pic, String remarks) {
            this.id = id; this.name = name; this.pic = pic; this.remarks = remarks;
        }
    }

    public static final class Ep {
        public final String name;
        public final String url;
        public Ep(String name, String url) { this.name = name; this.url = url; }
    }

    private SourceApi() { }

    /** 拼参数，兼容 base 里已经带 ? 的情况 */
    static String join(String base, String query) {
        if (base == null) return null;
        String b = base.trim();
        if (b.length() == 0) return b;
        char last = b.charAt(b.length() - 1);
        if (last == '?' || last == '&') return b + query;
        return b + (b.indexOf('?') >= 0 ? "&" : "?") + query;
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "CDesk/1.0");
        c.setRequestProperty("Accept", "application/json,text/plain,*/*");
        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }

    /** 分类列表 */
    public static List<Cat> categories(String base) throws Exception {
        return catsFrom(get(join(base, "ac=list")));
    }

    /** 从已经拿到的 json 文本里抠分类（ac=list 的返回里带 class 数组） */
    public static List<Cat> catsFrom(String txt) {
        List<Cat> out = new ArrayList<Cat>();
        try {
        JSONObject root = root(txt);
        JSONArray arr = root.optJSONArray("class");
        if (arr == null) arr = root.optJSONArray("classes");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = str(o, new String[] { "type_id", "id", "tid" });
            String name = str(o, new String[] { "type_name", "name", "title" });
            if (id != null && name != null) out.add(new Cat(id, name));
        }
        } catch (Throwable ignored) { }
        return out;
    }

    /** 某个分类下的影片列表 */
    public static List<Video> videos(String base, String typeId, int page) throws Exception {
        String q = "ac=videolist&pg=" + page;
        if (typeId != null && typeId.length() > 0) q = "ac=videolist&t=" + typeId + "&pg=" + page;
        String txt = get(join(base, q));
        List<Video> out = new ArrayList<Video>();
        JSONArray arr = root(txt).optJSONArray("list");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = str(o, new String[] { "vod_id", "id" });
            String name = str(o, new String[] { "vod_name", "name", "title" });
            if (id == null || name == null) continue;
            out.add(new Video(id, name,
                    str(o, new String[] { "vod_pic", "pic" }),
                    str(o, new String[] { "vod_remarks", "remarks", "note" })));
        }
        return out;
    }

    /** 详情：返回可播放的剧集 / 线路 */
    public static List<Ep> episodes(String base, String vodId) throws Exception {
        String txt = get(join(base, "ac=detail&ids=" + vodId));
        JSONArray arr = root(txt).optJSONArray("list");
        if (arr == null || arr.length() == 0) return new ArrayList<Ep>();
        JSONObject o = arr.optJSONObject(0);
        if (o == null) return new ArrayList<Ep>();
        String play = str(o, new String[] { "vod_play_url", "play_url", "url" });
        return parsePlayUrl(play);
    }

    /** 解析 `第1集$http://a#第2集$http://b`，也支持 $$$ 分隔的多条线路（取第一条线路） */
    static List<Ep> parsePlayUrl(String s) {
        List<Ep> out = new ArrayList<Ep>();
        if (s == null || s.length() == 0) return out;
        String line = s;
        int group = s.indexOf("$$$");
        if (group > 0) line = s.substring(0, group);
        String[] parts = line.split("#");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.length() == 0) continue;
            int d = p.indexOf('$');
            String name = d > 0 ? p.substring(0, d).trim() : ("第 " + (i + 1) + " 集");
            String url = d > 0 ? p.substring(d + 1).trim() : p;
            if (url.length() > 4 && (url.contains("://"))) out.add(new Ep(name, url));
        }
        return out;
    }

    private static JSONObject root(String txt) throws Exception {
        String t = txt == null ? "" : txt.trim();
        if (t.startsWith("\uFEFF")) t = t.substring(1).trim();
        if (t.startsWith("[")) {
            JSONObject wrap = new JSONObject();
            wrap.put("list", new JSONArray(t));
            return wrap;
        }
        if (!t.startsWith("{")) {
            throw new Exception("not json");
        }
        return new JSONObject(t);
    }

    private static String str(JSONObject o, String[] keys) {
        for (int i = 0; i < keys.length; i++) {
            String v = o.optString(keys[i], null);
            if (v != null) {
                v = v.trim();
                if (v.length() > 0 && !"null".equalsIgnoreCase(v)) return v;
            }
        }
        return null;
    }
}
