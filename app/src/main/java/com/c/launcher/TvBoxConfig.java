package com.c.launcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * TVBox 配置解析（只认结构，不含任何内置源）。
 *
 * 典型结构：
 *   { "sites": [ {"key","name","type","api","ext"} ],
 *     "lives": [ {"name","url","type"} ],
 *     "parses": [...] }
 *
 * 本类只把用户提供的配置拆成列表，不下载、不缓存、不推荐任何内容。
 */
public final class TvBoxConfig {

    public static final class Site {
        public final String name;
        public final String api;
        public final int type;
        public Site(String name, String api, int type) {
            this.name = name; this.api = api; this.type = type;
        }
    }

    public static final class Live {
        public final String name;
        public final String url;
        public Live(String name, String url) { this.name = name; this.url = url; }
    }

    private TvBoxConfig() { }

    /** 判断是不是 TVBox 配置：有 sites 数组且元素带 api */
    public static boolean looks(String json) {
        try {
            String t = json == null ? "" : json.trim();
            if (!t.startsWith("{")) return false;
            JSONObject o = new JSONObject(t);
            JSONArray a = o.optJSONArray("sites");
            if (a == null || a.length() == 0) return false;
            JSONObject first = a.optJSONObject(0);
            return first != null && first.optString("api", "").length() > 4;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 影视源列表（type 1 走 JSON 接口，type 0 走 XML；XML 暂不支持，标出来） */
    public static List<Site> sites(String json) {
        List<Site> out = new ArrayList<Site>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("sites");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String api = o.optString("api", "");
                String name = o.optString("name", "");
                int type = o.optInt("type", 1);
                if (api.length() < 5) continue;
                // csp_xxx 那类靠蜘蛛 jar 解析的源我们放不了，别列出来让人白点
                if (!api.startsWith("http")) continue;
                if (name.length() == 0) name = api;
                out.add(new Site(name, api, type));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** 直播源列表（url 通常本身是一个 m3u / txt 播放列表） */
    public static List<Live> lives(String json, Context c) {
        List<Live> out = new ArrayList<Live>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("lives");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                String name = o.optString("name", "");
                if (url.length() < 5) continue;
                if (name.length() == 0) name = c.getString(R.string.source_fallback, i + 1);
                out.add(new Live(name, url));
            }
        } catch (Throwable ignored) { }
        return out;
    }
}
