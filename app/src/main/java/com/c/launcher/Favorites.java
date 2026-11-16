package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 收藏：影视条目 / 音乐条目 / 直播频道都能收藏，首页里直接点开。 */
public final class Favorites {

    public static final class Item {
        public final String name;
        public final String url;
        public final String kind;   // vod / live / music
        public Item(String name, String url, String kind) {
            this.name = name; this.url = url; this.kind = kind;
        }
    }

    private static final String PREF = "cdesk_fav";

    private Favorites() { }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Item> all(Context c) {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray a = new JSONArray(p(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                out.add(new Item(o.optString("name"), o.optString("url"), o.optString("kind")));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static boolean has(Context c, String url) {
        for (Item it : all(c)) if (it.url.equals(url)) return true;
        return false;
    }

    public static void add(Context c, String name, String url, String kind) {
        if (has(c, url)) return;
        try {
            JSONArray a = new JSONArray(p(c).getString("items", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("url", url);
            o.put("kind", kind);
            a.put(o);
            p(c).edit().putString("items", a.toString()).commit();
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
}
