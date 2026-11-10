package com.c.launcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 音乐：两种用法。
 *   1. 本机扫歌（默认）——U 盘/本机里的歌直接列出来
 *   2. 填播放列表地址——m3u/m3u8 或「名字,地址」的 txt，一行一首
 * 注意：TVBox 那种 .js 源不能直接用，那是给它的爬虫引擎跑的脚本，我们是播放器不是什么都能执行。
 */
public class MusicHomeActivity extends Activity {

    private static final String PREFS = "cdesk_core";
    private static final String KEY_API = "music_api";

    private final List<String> titles = new ArrayList<String>();
    private final List<String> subs = new ArrayList<String>();
    private final List<String> urls = new ArrayList<String>();
    private TextView tvState;
    private BaseAdapter adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_music_home);
        Screen.apply(this);
        tvState = (TextView) findViewById(R.id.tvMusicState);
        ListView list = (ListView) findViewById(R.id.lvMusic);
        adapter = new SongAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= urls.size()) { return; }
                Intent i = new Intent(MusicHomeActivity.this, PlayerActivity.class);
                i.putExtra(PlayerActivity.EXTRA_URL, urls.get(pos));
                i.putExtra(PlayerActivity.EXTRA_NAME, titles.get(pos));
                startActivity(i);
                CLog.i("music: play " + titles.get(pos));
            }
        });

        android.widget.Button api = (android.widget.Button) findViewById(R.id.btnMusicApi);
        try { ThemeManager.styleButton(api, ThemeManager.current(this)); } catch (Throwable ignored) { }
        api.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askApi(); }
        });
        android.widget.Button local = (android.widget.Button) findViewById(R.id.btnMusicLocal);
        try { ThemeManager.styleButton(local, ThemeManager.current(this)); } catch (Throwable ignored) { }
        local.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_API, "").commit();
                scanLocal();
            }
        });

        wireRadio(R.id.btnRadioCn, true);
        wireRadio(R.id.btnRadioIntl, false);

        String api0 = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_API, "");
        if (api0 != null && api0.trim().length() > 0) { loadRemote(api0.trim()); }
        else { scanLocal(); }
    }

    private void askApi() {
        final EditText et = new EditText(this);
        et.setHint(R.string.musicpg_api_hint);
        et.setSingleLine(true);
        String cur = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_API, "");
        if (cur != null) { et.setText(cur); }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.musicpg_api_title)
                .setMessage(R.string.musicpg_api_msg)
                .setView(et)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String v = et.getText().toString().trim();
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_API, v).commit();
                        CLog.i("music: api set");
                        if (v.length() == 0) { scanLocal(); } else { loadRemote(v); }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reset() {
        titles.clear();
        subs.clear();
        urls.clear();
    }

    private void scanLocal() {
        reset();
        Cursor c = null;
        try {
            ContentResolver r = getContentResolver();
            c = r.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] { MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA },
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " ASC");
            while (c != null && c.moveToNext()) {
                String t = c.getString(0);
                String a = c.getString(1);
                String d = c.getString(2);
                if (d == null || d.length() == 0) { continue; }
                titles.add(t == null ? d : t);
                subs.add(a == null || "<unknown>".equals(a) ? "" : a);
                urls.add(d);
            }
        } catch (Throwable t) {
            CLog.e("music: scan failed " + t);
        } finally {
            if (c != null) { try { c.close(); } catch (Throwable ignored) { } }
        }
        CLog.i("music: local songs=" + titles.size());
        afterLoad();
    }

    /** 播放列表：m3u/m3u8（#EXTINF 后跟地址）或 txt（名字,地址） */
    private void loadRemote(final String api) {
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> n = new ArrayList<String>();
                final List<String> u = new ArrayList<String>();
                String err = null;
                try {
                    String txt = fetch(api);
                    parsePlaylist(txt, n, u);
                } catch (Throwable t) {
                    err = String.valueOf(t);
                }
                final String e = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        reset();
                        titles.addAll(n);
                        urls.addAll(u);
                        for (int k = 0; k < n.size(); k++) { subs.add(""); }
                        CLog.i("music: remote songs=" + n.size() + (e == null ? "" : (" err=" + e)));
                        afterLoad();
                        if (e != null) { tvState.setText(R.string.vodpg_failed); }
                    }
                });
            }
        }).start();
    }

    private void afterLoad() {
        adapter.notifyDataSetChanged();
        tvState.setVisibility(titles.isEmpty() ? View.VISIBLE : View.GONE);
        if (titles.isEmpty()) { tvState.setText(R.string.musicpg_empty); }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "CDesk");
        InputStream in = c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) { sb.append(line).append('\n'); }
        br.close();
        return sb.toString();
    }

    private static void parsePlaylist(String txt, List<String> names, List<String> urls) {
        if (txt == null) { return; }
        String pending = null;
        for (String raw : txt.split("\n")) {
            String s = raw.trim();
            if (s.length() == 0) { continue; }
            if (s.startsWith("#EXTINF")) {
                int c = s.lastIndexOf(',');
                pending = c >= 0 ? s.substring(c + 1).trim() : null;
                continue;
            }
            if (s.startsWith("#")) { continue; }
            if (s.startsWith("http") || s.startsWith("rtmp") || s.startsWith("ftp")) {
                names.add(pending == null || pending.length() == 0 ? ("Song " + (names.size() + 1)) : pending);
                urls.add(s);
                pending = null;
                continue;
            }
            // txt：名字,地址
            int c = s.lastIndexOf(',');
            if (c > 0 && c < s.length() - 1) {
                String u = s.substring(c + 1).trim();
                if (u.startsWith("http") || u.startsWith("/")) {
                    names.add(s.substring(0, c).trim());
                    urls.add(u);
                }
            }
        }
    }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return titles.size(); }
        @Override public Object getItem(int p) { return titles.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) { v = LayoutInflater.from(MusicHomeActivity.this).inflate(R.layout.item_vod_video, parent, false); }
            ((TextView) v.findViewById(R.id.tvName)).setText(titles.get(pos));
            TextView r = (TextView) v.findViewById(R.id.tvRemark);
            String a = pos < subs.size() ? subs.get(pos) : "";
            if (a == null || a.length() == 0) { r.setVisibility(View.GONE); }
            else { r.setVisibility(View.VISIBLE); r.setText(a); }
            return v;
        }
    }

    /** 内置公开电台：Radio Browser（公共接口，不用 key） */
    private void wireRadio(int id, final boolean cn) {
        android.widget.Button b = (android.widget.Button) findViewById(id);
        if (b == null) { return; }
        try { ThemeManager.styleButton(b, ThemeManager.current(this)); } catch (Throwable ignored) { }
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadBuiltin(cn); }
        });
    }

    /**
     * 国内台按国家码 CN 取，国际台取全球点播最多的。
     * 接口挂掉就换镜像，全挂再报错——不能让车机上一片空白。
     */
    private void loadRadio(final boolean cn) {
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> n = new ArrayList<String>();
                final List<String> u = new ArrayList<String>();
                final List<String> s = new ArrayList<String>();
                String err = null;
                String query = cn
                        ? "/json/stations/search?countrycode=CN&limit=200&hidebroken=true&order=clickcount&reverse=true"
                        : "/json/stations/search?limit=200&hidebroken=true&order=clickcount&reverse=true";
                String[] hosts = new String[] {
                        "https://de1.api.radio-browser.info",
                        "https://de2.api.radio-browser.info",
                        "https://at1.api.radio-browser.info",
                        "https://nl1.api.radio-browser.info"
                };
                for (int i = 0; i < hosts.length; i++) {
                    try {
                        String json = fetch(hosts[i] + query);
                        org.json.JSONArray arr = new org.json.JSONArray(json);
                        for (int k = 0; k < arr.length(); k++) {
                            org.json.JSONObject o = arr.getJSONObject(k);
                            String url = o.optString("url_resolved", "");
                            if (url.length() == 0) { url = o.optString("url", ""); }
                            String name = o.optString("name", "").trim();
                            if (url.length() == 0 || name.length() == 0) { continue; }
                            String country = o.optString("country", "");
                            String bitrate = o.optString("bitrate", "");
                            String sub = country;
                            if (bitrate.length() > 0 && !"0".equals(bitrate)) { sub = country + " · " + bitrate + "kbps"; }
                            n.add(name);
                            u.add(url);
                            s.add(sub);
                        }
                        if (n.size() > 0) { err = null; break; }
                    } catch (Throwable t) {
                        err = String.valueOf(t);
                    }
                }
                final String e = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        reset();
                        titles.addAll(n);
                        urls.addAll(u);
                        subs.addAll(s);
                        CLog.i("music: radio cn=" + cn + " got=" + n.size() + (e == null ? "" : (" err=" + e)));
                        afterLoad();
                        if (e != null && n.isEmpty()) { tvState.setText(R.string.vodpg_failed); }
                    }
                });
            }
        }).start();
    }

    /**
     * 内置电台：assets/radio.json。
     * 里面每一条都是实测能出流的地址（国内央广 5 个 + 国外 11 个），
     * 不依赖任何第三方接口，打开就能听；内置表空了才退回公共接口。
     */
    private void loadBuiltin(final boolean cn) {
        final List<String> n = new ArrayList<String>();
        final List<String> u = new ArrayList<String>();
        final List<String> s = new ArrayList<String>();
        try {
            java.io.InputStream in = getAssets().open("radio.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) { bo.write(buf, 0, len); }
            in.close();
            org.json.JSONArray arr = new org.json.JSONArray(new String(bo.toByteArray(), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (cn != "cn".equals(o.optString("c", "intl"))) { continue; }
                String name = o.optString("n", "");
                String url = o.optString("u", "");
                if (name.length() == 0 || url.length() == 0) { continue; }
                n.add(name);
                u.add(url);
                s.add(o.optString("s", ""));
            }
        } catch (Throwable t) {
            CLog.e("music: builtin read failed " + t);
        }
        CLog.i("music: builtin cn=" + cn + " got=" + n.size());
        if (n.isEmpty()) { loadRadio(cn); return; }
        reset();
        titles.addAll(n);
        urls.addAll(u);
        subs.addAll(s);
        afterLoad();
    }
}
