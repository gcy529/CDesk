package com.c.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表解析：把用户自己导入的文本转成统一的频道/条目列表。
 *
 * 支持三种最常见格式（全部是本项目自己实现，不依赖任何第三方库）：
 *  1. M3U / M3U8 扩展格式   `#EXTINF:-1 tvg-name="x" group-title="分组",名称` + 下一行 URL
 *  2. TXT 分组格式           `分组名,#genre#` 起分组，之后每行 `名称,URL`
 *  3. JSON                  数组 [{name,url,group}]，或对象里的 channels/list/items 数组
 *
 * 本类只做文本到条目的转换，不内置、不下载、不推荐任何内容源。
 */
public final class PlaylistParser {

    public static final class Item {
        public final String name;
        public final String group;
        public final String url;
        public Item(String name, String group, String url) {
            this.name = name; this.group = group; this.url = url;
        }
    }

    private PlaylistParser() { }

    /** 音乐 json 歌单：{"name":..,"list":[{name,artist,url}]}，也接受裸数组 */
    public static List<Item> parseMusicJson(String raw) {
        List<Item> out = new ArrayList<Item>();
        if (raw == null) return out;
        String text = raw.replace("\uFEFF", "").trim();
        if (text.length() == 0) return out;
        try {
            org.json.JSONArray arr;
            if (text.startsWith("[")) {
                arr = new org.json.JSONArray(text);
            } else {
                org.json.JSONObject obj = new org.json.JSONObject(text);
                arr = obj.optJSONArray("list");
                if (arr == null) arr = obj.optJSONArray("songs");
                if (arr == null) arr = obj.optJSONArray("music");
                if (arr == null) arr = obj.optJSONArray("data");
                if (arr == null) return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = first(o, new String[] { "url", "src", "link", "address", "uri" });
                if (url == null || url.length() < 5) continue;
                String name = first(o, new String[] { "name", "title", "song" });
                String artist = first(o, new String[] { "artist", "singer", "author" });
                String album = first(o, new String[] { "album", "group" });
                String label = (name == null ? guessName(url) : name)
                        + (artist != null && artist.length() > 0 ? " - " + artist : "");
                out.add(new Item(label, album == null ? "" : album, url));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static List<Item> parse(String raw) {
        List<Item> out = new ArrayList<Item>();
        if (raw == null) return out;
        String text = raw.replace("\uFEFF", "").trim();
        if (text.length() == 0) return out;

        String lower = text.toLowerCase();
        if (lower.contains("#extinf") || lower.startsWith("#extm3u")) {
            parseM3u(text, out);
        } else if (text.contains("#genre#")) {
            parseGenre(text, out);
        } else if (text.startsWith("[") || text.startsWith("{")) {
            if (!parseJson(text, out)) parsePairs(text, out);
        } else {
            parsePairs(text, out);
        }
        return out;
    }

    /** M3U：EXTINF 行描述，下一行是地址 */
    private static void parseM3u(String text, List<Item> out) {
        String[] lines = text.split("\r?\n");
        String pendingName = null;
        String pendingGroup = "";
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            if (line.startsWith("#EXTINF")) {
                pendingGroup = attr(line, "group-title");
                int comma = titleComma(line);
                pendingName = comma >= 0 && comma + 1 < line.length()
                        ? line.substring(comma + 1).trim() : attr(line, "tvg-name");
            } else if (!line.startsWith("#")) {
                // EXTINF 没写名字就拿地址尾巴当名字：这里不能硬编码中文兜底名，
                // 否则英文界面会冒出「频道 1」
                String name = pendingName == null || pendingName.length() == 0
                        ? guessName(line) : pendingName;
                out.add(new Item(name, pendingGroup, line));
                pendingName = null;
            }
        }
    }

    /** TXT：#genre# 分组 */
    private static void parseGenre(String text, List<Item> out) {
        String[] lines = text.split("\r?\n");
        String group = "";
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            if (line.contains("#genre#")) {
                group = line.substring(0, line.indexOf("#genre#")).replace(",", "").trim();
                continue;
            }
            int comma = line.indexOf(',');
            if (comma > 0 && comma + 1 < line.length()) {
                String name = line.substring(0, comma).trim();
                String url = line.substring(comma + 1).trim();
                if (url.length() > 4) out.add(new Item(name, group, url));
            }
        }
    }

    /** JSON：数组 或 对象内 channels/list/items 数组 */
    private static boolean parseJson(String text, List<Item> out) {
        try {
            JSONArray arr = null;
            if (text.startsWith("[")) {
                arr = new JSONArray(text);
            } else {
                JSONObject obj = new JSONObject(text);
                String[] keys = new String[] { "channels", "list", "items", "lives", "data" };
                for (int i = 0; i < keys.length; i++) {
                    JSONArray a = obj.optJSONArray(keys[i]);
                    if (a != null) { arr = a; break; }
                }
            }
            if (arr == null) return false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = first(o, new String[] { "name", "title", "tvg-name" });
                String url = first(o, new String[] { "url", "src", "link", "address" });
                String group = first(o, new String[] { "group", "group-title", "category" });
                if (url != null && url.length() > 4) {
                    out.add(new Item(name == null || name.length() == 0 ? guessName(url) : name,
                            group == null ? "" : group, url));
                }
            }
            return !out.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 兜底：`名称,地址` 每行一条 */
    private static void parsePairs(String text, List<Item> out) {
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            int comma = line.indexOf(',');
            if (comma > 0 && comma + 1 < line.length()) {
                String name = line.substring(0, comma).trim();
                String url = line.substring(comma + 1).trim();
                if (url.length() > 4) out.add(new Item(name, "", url));
            } else if (line.length() > 8 && line.contains("://")) {
                out.add(new Item(guessName(line), "", line));
            }
        }
    }

    /**
     * 标题分隔逗号 = 引号外的第一个逗号。
     * group-title="央视,卫视" 里的逗号属于属性值，不能当分隔符；
     * 而台名自身的逗号（"新闻,高清"）在分隔符之后，要整段保留。
     */
    private static int titleComma(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) return i;
        }
        return -1;
    }

    private static String attr(String line, String key) {
        String needle = key + "=\"";
        int i = line.indexOf(needle);
        if (i < 0) return "";
        int s = i + needle.length();
        int e = line.indexOf('"', s);
        return e > s ? line.substring(s, e) : "";
    }

    private static String first(JSONObject o, String[] keys) {
        for (int i = 0; i < keys.length; i++) {
            String v = o.optString(keys[i], null);
            if (v != null && v.length() > 0) return v;
        }
        return null;
    }

    private static String guessName(String url) {
        int slash = url.lastIndexOf('/');
        String tail = slash >= 0 ? url.substring(slash + 1) : url;
        int dot = tail.lastIndexOf('.');
        if (dot > 0) tail = tail.substring(0, dot);
        return tail.length() == 0 ? url : tail;
    }
}
