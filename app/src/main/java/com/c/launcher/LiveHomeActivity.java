package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 直播第一层：分组宫格 */
public class LiveHomeActivity extends Activity {

    private GridView grid;
    private TextView tvState;
    private final List<LiveApi.Group> groups = new ArrayList<LiveApi.Group>();
    private GroupAdapter adapter;
    private String url;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_live_home);
        wireApiButton();
        Screen.apply(this);
        url = getSharedPreferences("cdesk_core", MODE_PRIVATE).getString("live_api", "");
        grid = (GridView) findViewById(R.id.gridLive);
        tvState = (TextView) findViewById(R.id.tvLiveState);
        adapter = new GroupAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Intent i = new Intent(LiveHomeActivity.this, LiveListActivity.class);
                i.putExtra(LiveListActivity.EXTRA_INDEX, pos);
                i.putExtra(LiveListActivity.EXTRA_TITLE, groups.get(pos).name);
                startActivity(i);
            }
        });
        load();
    }

    private void load() {
        if (url == null || url.trim().length() == 0) {
            // 没配源就先把内置频道摆出来，别让直播页是一片空白
            List<LiveApi.Group> g0 = builtinGroups();
            groups.clear();
            LiveApi.CACHE.clear();
            groups.addAll(g0);
            LiveApi.CACHE.addAll(g0);
            adapter.notifyDataSetChanged();
            tvState.setVisibility(g0.isEmpty() ? View.VISIBLE : View.GONE);
            if (g0.isEmpty()) { tvState.setText(R.string.livepg_no_source); }
            CLog.i("live: builtin groups=" + g0.size());
            return;
        }
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<List<LiveApi.Group>>() {
            @Override public List<LiveApi.Group> run() throws Exception {
                // 一个框里可以填多个源地址（换行或 | 分隔），全部抓下来合并
                String[] srcs = url.split("[\\r\\n|]+");
                List<LiveApi.Group> merged = new ArrayList<LiveApi.Group>();
                String lastErr = null;
                int ok = 0;
                for (int i = 0; i < srcs.length; i++) {
                    String s = srcs[i].trim();
                    if (s.length() == 0) { continue; }
                    try {
                        List<LiveApi.Group> g = LiveApi.parse(LiveApi.fetch(s));
                        for (int j = 0; j < g.size(); j++) { merged.add(g.get(j)); }
                        ok++;
                    } catch (Throwable t) {
                        lastErr = t.getClass().getSimpleName() + ": " + t.getMessage();
                        CLog.e("live: source " + i + " failed " + lastErr);
                    }
                }
                if (ok == 0 && lastErr != null) { throw new Exception(lastErr); }
                CLog.i("live: sources ok=" + ok + "/" + srcs.length);
                return merged;
            }
        }, new VodAsync.Done<List<LiveApi.Group>>() {
            @Override public void ok(List<LiveApi.Group> v) {
                groups.clear();
                LiveApi.CACHE.clear();
                if (v != null) { groups.addAll(v); LiveApi.CACHE.addAll(v); }
                adapter.notifyDataSetChanged();
                if (groups.isEmpty()) {
                    tvState.setVisibility(View.VISIBLE);
                    tvState.setText(R.string.vodpg_empty);
                } else {
                    tvState.setVisibility(View.GONE);
                }
                int ch = 0;
                for (int i = 0; i < groups.size(); i++) { ch += groups.get(i).channels.size(); }
                CLog.i("live: groups=" + groups.size() + " channels=" + ch);
            }
            @Override public void fail(String reason) {
                tvState.setVisibility(View.VISIBLE);
                tvState.setText(getString(R.string.vodpg_failed, reason));
                CLog.e("live: load failed " + reason);
            }
        });
    }

    private final class GroupAdapter extends BaseAdapter {
        @Override public int getCount() { return groups.size(); }
        @Override public Object getItem(int p) { return groups.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) { v = LayoutInflater.from(LiveHomeActivity.this).inflate(R.layout.item_vod_cat, parent, false); }
            LiveApi.Group g = groups.get(pos);
            ((TextView) v).setText(g.name + " (" + g.channels.size() + ")");
            return v;
        }
    }

    /**
     * 接口入口：跟点播页一样直接填，不跳设置。
     * 直播源可以填多个，一行一个（也支持用 | 分隔）。
     */
    private void wireApiButton() {
        android.widget.Button b = (android.widget.Button) findViewById(R.id.btnLiveApi);
        if (b == null) { return; }
        try { ThemeManager.styleButton(b, ThemeManager.current(this)); } catch (Throwable ignored) { }
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final android.widget.EditText et = new android.widget.EditText(LiveHomeActivity.this);
                et.setHint(R.string.livepg_api_hint);
                et.setMinLines(3);
                et.setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT);
                String cur = getSharedPreferences("cdesk_core", MODE_PRIVATE).getString("live_api", "");
                if (cur != null) { et.setText(cur); }
                new android.app.AlertDialog.Builder(LiveHomeActivity.this)
                        .setTitle(R.string.livepg_api_title)
                        .setMessage(R.string.livepg_api_msg)
                        .setView(et)
                        .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                String val = et.getText().toString().trim();
                                getSharedPreferences("cdesk_core", MODE_PRIVATE).edit().putString("live_api", val).commit();
                                CLog.i("live: api saved lines=" + (val.length() == 0 ? 0 : val.split("[\\r\\n|]+").length));
                                recreate();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
    }

    /** 直播页同理：没源就直接弹出来 */
    private void autoPromptLive() {
        String cur = getSharedPreferences("cdesk_core", MODE_PRIVATE).getString("live_api", "");
        if (cur != null && cur.trim().length() > 0) { return; }
        View root = findViewById(android.R.id.content);
        if (root == null) { return; }
        root.post(new Runnable() {
            @Override public void run() {
                View b = findViewById(R.id.btnLiveApi);
                if (b != null) { b.requestFocus(); b.performClick(); }
            }
        });
    }

    /**
     * 内置频道：没配置任何源时的兜底。
     * 这里每一条都是实测能拉到 m3u8 才写进去的公开频道；国内 IPTV 列表我这侧连不通，不往里放。
     */
    private List<LiveApi.Group> builtinGroups() {
        List<LiveApi.Group> out = new ArrayList<LiveApi.Group>();
        try {
            java.io.InputStream in = getAssets().open("live.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) { bo.write(buf, 0, len); }
            in.close();
            org.json.JSONArray arr = new org.json.JSONArray(new String(bo.toByteArray(), "UTF-8"));
            java.util.LinkedHashMap<String, LiveApi.Group> byName = new java.util.LinkedHashMap<String, LiveApi.Group>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                String gname = o.optString("g", "Built-in");
                String name = o.optString("n", "");
                String u = o.optString("u", "");
                if (name.length() == 0 || u.length() == 0) { continue; }
                LiveApi.Group g = byName.get(gname);
                if (g == null) { g = new LiveApi.Group(gname); byName.put(gname, g); out.add(g); }
                g.channels.add(new LiveApi.Channel(name, u));
            }
        } catch (Throwable t) {
            CLog.e("live: builtin read failed " + t);
        }
        return out;
    }
}
