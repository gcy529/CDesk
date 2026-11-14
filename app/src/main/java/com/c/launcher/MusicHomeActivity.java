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
    private JsSource jsSource;
    private boolean jsLoaded = false;

    /** 播放列表加载结果：歌名 + 直链 */
    private static final class PlaylistResult {
        final List<String> names;
        final List<String> urls;
        PlaylistResult(List<String> n, List<String> u) { names = n; urls = u; }
    }

    /** 电台加载结果：歌名 + 直链 + 副标题 + 错误原因（全镜像失败时非空） */
    private static final class RadioResult {
        final List<String> names;
        final List<String> urls;
        final List<String> subs;
        final String err;
        RadioResult(List<String> n, List<String> u, List<String> s, String e) {
            names = n; urls = u; subs = s; err = e;
        }
    }

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
                // 走内核，桌面迷你条继续驱动；切页不断
                MusicKernel.get(MusicHomeActivity.this).play(urls.get(pos), titles.get(pos), false);
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

        View testBtn = findViewById(R.id.btnRadioTest);
        if (testBtn != null) {
            try { ThemeManager.styleButton((android.widget.Button) testBtn, ThemeManager.current(this)); } catch (Throwable ignored) { }
            testBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { testLatency(); }
            });
        }
        wireRadio(R.id.btnRadioCn, true);
        wireRadio(R.id.btnRadioIntl, false);
        wireJsSource();

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
        VodAsync.go(this, new VodAsync.Job<PlaylistResult>() {
            @Override public PlaylistResult run() throws Exception {
                String txt = fetch(api);
                List<String> n = new ArrayList<String>();
                List<String> u = new ArrayList<String>();
                parsePlaylist(txt, n, u);
                return new PlaylistResult(n, u);
            }
        }, new VodAsync.Done<PlaylistResult>() {
            @Override public void ok(PlaylistResult r) {
                reset();
                titles.addAll(r.names);
                urls.addAll(r.urls);
                for (int k = 0; k < r.names.size(); k++) { subs.add(""); }
                CLog.i("music: remote songs=" + r.names.size());
                afterLoad();
            }
            @Override public void fail(String e) {
                reset();
                afterLoad();
                tvState.setText(R.string.vodpg_failed);
                CLog.e("music: remote load failed " + e);
            }
        });
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
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) { sb.append(line).append('\n'); }
            return sb.toString();
        } finally {
            if (br != null) { try { br.close(); } catch (Throwable ignored) { } }
            c.disconnect();
        }
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

    /** 花音源：填了音源直链才显示搜索框，没填不干扰内置电台 */
    private void wireJsSource() {
        final android.widget.Button jsBtn = (android.widget.Button) findViewById(R.id.btnJsSearch);
        final android.widget.EditText jsSearch = (android.widget.EditText) findViewById(R.id.etJsSearch);
        if (jsBtn == null || jsSearch == null) return;
        try {
            ThemeManager.styleButton(jsBtn, ThemeManager.current(this));
        } catch (Throwable ignored) { }
        // 没填过花音源就完全藏起来（按钮和输入框都不显示）
        String saved = JsSource.savedSource(this);
        if (saved == null || saved.length() == 0) {
            jsBtn.setVisibility(View.GONE);
            jsSearch.setVisibility(View.GONE);
            return;
        }
        jsBtn.setVisibility(View.VISIBLE);
        jsSearch.setVisibility(View.VISIBLE);
        jsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askJsSource(); }
        });
        // 输入关键词 + OK：方向键选歌
        jsSearch.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
                doJsSearch(jsSearch.getText().toString().trim());
                return true;
            }
        });
    }

    private void askJsSource() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("https://.../flower/latest.js");
        et.setSingleLine(true);
        String cur = JsSource.savedSource(this);
        if (cur != null) et.setText(cur);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.musicpg_js_title)
                .setMessage(R.string.musicpg_js_msg)
                .setView(et)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String v = et.getText().toString().trim();
                        JsSource.saveSource(MusicHomeActivity.this, v);
                        CLog.i("music: js source set " + v);
                        if (v.length() == 0) {
                            jsLoaded = false;
                            ((android.widget.Button) findViewById(R.id.btnJsSearch)).setVisibility(View.GONE);
                            ((android.widget.EditText) findViewById(R.id.etJsSearch)).setVisibility(View.GONE);
                            return;
                        }
                        loadJsSource(v);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadJsSource(final String url) {
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<JsSource>() {
            @Override public JsSource run() throws Exception {
                JsSource s = new JsSource();
                s.load(url);
                return s;
            }
        }, new VodAsync.Done<JsSource>() {
            @Override public void ok(JsSource s) {
                jsSource = s;
                jsLoaded = true;
                // 搜索框显示出来
                ((android.widget.Button) findViewById(R.id.btnJsSearch)).setVisibility(View.VISIBLE);
                ((android.widget.EditText) findViewById(R.id.etJsSearch)).setVisibility(View.VISIBLE);
                CLog.i("music: js source ready " + url);
                loadJsList();
            }
            @Override public void fail(String e) {
                CLog.e("music: js source load failed " + e);
                tvState.setText(R.string.vodpg_failed);
            }
        });
    }

    /** 拉花音源全列表 */
    private void loadJsList() {
        if (!jsLoaded || jsSource == null) return;
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<List<JsSource.Song>>() {
            @Override public List<JsSource.Song> run() throws Exception {
                return jsSource.list();
            }
        }, new VodAsync.Done<List<JsSource.Song>>() {
            @Override public void ok(List<JsSource.Song> songs) {
                reset();
                for (JsSource.Song s : songs) {
                    titles.add(s.title);
                    subs.add(s.sub);
                    urls.add(s.url);
                }
                afterLoad();
                CLog.i("music: js list got=" + songs.size());
            }
            @Override public void fail(String e) {
                reset();
                afterLoad();
                tvState.setText(R.string.vodpg_failed);
                CLog.e("music: js list failed " + e);
            }
        });
    }

    /** 搜索关键词，结果直接进列表 */
    private void doJsSearch(final String kw) {
        if (!jsLoaded || jsSource == null || kw.length() == 0) return;
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<List<JsSource.Song>>() {
            @Override public List<JsSource.Song> run() throws Exception {
                return jsSource.search(kw);
            }
        }, new VodAsync.Done<List<JsSource.Song>>() {
            @Override public void ok(List<JsSource.Song> songs) {
                reset();
                for (JsSource.Song s : songs) {
                    titles.add(s.title);
                    subs.add(s.sub);
                    urls.add(s.url);
                }
                afterLoad();
                CLog.i("music: js search got=" + songs.size());
            }
            @Override public void fail(String e) {
                reset();
                afterLoad();
                tvState.setText(R.string.vodpg_failed);
                CLog.e("music: js search failed " + e);
            }
        });
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
        VodAsync.go(this, new VodAsync.Job<RadioResult>() {
            @Override public RadioResult run() throws Exception {
                List<String> n = new ArrayList<String>();
                List<String> u = new ArrayList<String>();
                List<String> s = new ArrayList<String>();
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
                        if (n.size() > 0) { break; }
                    } catch (Throwable t) {
                        err = String.valueOf(t);
                    }
                }
                return new RadioResult(n, u, s, err);
            }
        }, new VodAsync.Done<RadioResult>() {
            @Override public void ok(RadioResult r) {
                reset();
                titles.addAll(r.names);
                urls.addAll(r.urls);
                subs.addAll(r.subs);
                CLog.i("music: radio cn=" + cn + " got=" + r.names.size() + (r.err == null ? "" : (" err=" + r.err)));
                afterLoad();
                if (r.err != null && r.names.isEmpty()) { tvState.setText(R.string.vodpg_failed); }
            }
            @Override public void fail(String e) {
                reset();
                afterLoad();
                tvState.setText(R.string.vodpg_failed);
                CLog.e("music: radio load failed " + e);
            }
        });
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

    /**
     * 线路检测：真的去连每个台的地址，量握手耗时。
     * 拿到响应头就算通（不读完整个流），连不上的标「超时」。
     */
    private void testLatency() {
        if (urls.isEmpty()) { return; }
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.musicpg_testing);
        final String[] base = new String[subs.size()];
        for (int i = 0; i < subs.size(); i++) { base[i] = subs.get(i); }
        final List<String> targets = new ArrayList<String>(urls);
        VodAsync.go(this, new VodAsync.Job<long[]>() {
            @Override public long[] run() throws Exception {
                long[] ms = new long[targets.size()];
                for (int i = 0; i < targets.size(); i++) {
                    long t0 = android.os.SystemClock.elapsedRealtime();
                    java.net.HttpURLConnection conn = null;
                    try {
                        conn = (java.net.HttpURLConnection) new java.net.URL(targets.get(i)).openConnection();
                        conn.setConnectTimeout(3500);
                        conn.setReadTimeout(3500);
                        conn.setRequestProperty("User-Agent", "CDesk");
                        conn.getResponseCode();
                        ms[i] = android.os.SystemClock.elapsedRealtime() - t0;
                    } catch (Throwable t) {
                        ms[i] = -1;
                    } finally {
                        if (conn != null) { conn.disconnect(); }
                    }
                }
                return ms;
            }
        }, new VodAsync.Done<long[]>() {
            @Override public void ok(long[] ms) {
                for (int i = 0; i < subs.size() && i < ms.length; i++) {
                    String b = base[i] == null ? "" : base[i];
                    int cut = b.indexOf(" · ");
                    if (cut > 0) { b = b.substring(0, cut); }
                    subs.set(i, b + " · " + (ms[i] < 0 ? "超时" : ms[i] + "ms"));
                }
                afterLoad();
                tvState.setVisibility(View.GONE);
            }
            @Override public void fail(String e) {
                afterLoad();
                tvState.setVisibility(View.GONE);
                CLog.e("music: latency test failed " + e);
            }
        });
    }
}
