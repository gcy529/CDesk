package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 最近播放：影视剧集 / 直播频道 / 音乐，最近 50 条，带播放进度。 */
public final class History {

    public static final class Entry {
        public final String name;
        public final String url;
        public final String kind;   // vod / live / music
        public final long at;       // 时间戳
        public final long pos;      // 播放位置（毫秒），0=没记录
        public Entry(String name, String url, String kind, long at, long pos) {
            this.name = name; this.url = url; this.kind = kind; this.at = at; this.pos = pos;
        }
    }

    private static final String PREF = "cdesk_history";
    private static final int MAX = 50;

    private History() { }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Entry> all(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        try {
            JSONArray a = new JSONArray(p(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                out.add(new Entry(o.optString("name"), o.optString("url"),
                        o.optString("kind"), o.optLong("at"), o.optLong("pos")));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** 播放开始/进行中调用：同 url 去重置顶 */
    public static void push(Context c, String name, String url, String kind, long pos) {
        if (url == null || url.length() < 5) return;
        try {
            JSONArray a = new JSONArray(p(c).getString("items", "[]"));
            JSONArray b = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("url", url);
            o.put("kind", kind);
            o.put("at", System.currentTimeMillis());
            o.put("pos", pos);
            b.put(o);
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e == null) continue;
                if (!url.equals(e.optString("url"))) b.put(e);
            }
            while (b.length() > MAX) b.remove(b.length() - 1);
            p(c).edit().putString("items", b.toString()).commit();
        } catch (Throwable ignored) { }
    }

    public static void remove(Context c, String url) {
        try {
            JSONArray a = new JSONArray(p(c).getString("items", "[]"));
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !url.equals(o.optString("url"))) b.put(o);
            }
            p(c).edit().putString("items", b.toString()).commit();
        } catch (Throwable ignored) { }
    }

    public static void clear(Context c) {
        p(c).edit().remove("items").commit();
    }
}
