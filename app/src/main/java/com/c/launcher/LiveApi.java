package com.c.launcher;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 直播源解析：m3u 和 txt 两种。
 * m3u: #EXTINF:-1 group-title="央视",CCTV-1 然后下一行是地址
 * txt: 央视,#genre# 然后 频道名,地址
 * 不内置任何源，地址由用户自己填。
 */
public final class LiveApi {

    public static final class Channel {
        public final String name;
        public final String url;
        public Channel(String name, String url) { this.name = name; this.url = url; }
    }

    public static final class Group {
        public final String name;
        public final List<Channel> channels = new ArrayList<Channel>();
        public Group(String name) { this.name = name; }
    }

    public static final List<Group> CACHE = new ArrayList<Group>();

    private LiveApi() { }

    public static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url.trim()).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "CDesk/1.0");
        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) { sb.append(line).append('\n'); }
        r.close();
        c.disconnect();
        return sb.toString();
    }

    public static List<Group> parse(String txt) {
        List<Group> out = new ArrayList<Group>();
        if (txt == null) { return out; }
        if (txt.indexOf("#EXTINF") >= 0) { parseM3u(txt, out); } else { parseTxt(txt, out); }
        return out;
    }

    private static void parseM3u(String txt, List<Group> out) {
        String[] lines = txt.split("\n");
        Group cur = null;
        String pendingName = null;
        String pendingGroup = null;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.length() == 0) { continue; }
            if (l.startsWith("#EXTINF")) {
                pendingGroup = attr(l, "group-title");
                int comma = l.lastIndexOf(',');
                pendingName = comma >= 0 && comma < l.length() - 1 ? l.substring(comma + 1).trim() : null;
                continue;
            }
            if (l.startsWith("#")) { continue; }
            if (pendingName == null) { continue; }
            String gname = pendingGroup == null || pendingGroup.length() == 0 ? "其他" : pendingGroup;
            if (cur == null || !cur.name.equals(gname)) { cur = find(out, gname); }
            cur.channels.add(new Channel(pendingName, l));
            pendingName = null;
            pendingGroup = null;
        }
    }

    private static void parseTxt(String txt, List<Group> out) {
        String[] lines = txt.split("\n");
        Group cur = null;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.length() == 0 || l.startsWith("#")) { continue; }
            if (l.endsWith(",#genre#")) {
                String gname = l.substring(0, l.length() - 8).trim();
                cur = find(out, gname.length() == 0 ? "其他" : gname);
                continue;
            }
            int comma = l.indexOf(',');
            if (comma <= 0 || comma >= l.length() - 1) { continue; }
            String name = l.substring(0, comma).trim();
            String url = l.substring(comma + 1).trim();
            if (url.indexOf("://") < 0) { continue; }
            if (cur == null) { cur = find(out, "其他"); }
            cur.channels.add(new Channel(name, url));
        }
    }

    private static Group find(List<Group> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(name)) { return list.get(i); }
        }
        Group g = new Group(name);
        list.add(g);
        return g;
    }

    private static String attr(String line, String key) {
        int i = line.indexOf(key + "=\"");
        if (i < 0) { return null; }
        int s = i + key.length() + 2;
        int e = line.indexOf('"', s);
        return e > s ? line.substring(s, e) : null;
    }
}
