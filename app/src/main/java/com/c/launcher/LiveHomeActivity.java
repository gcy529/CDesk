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
            tvState.setVisibility(View.VISIBLE);
            tvState.setText(R.string.livepg_no_source);
            CLog.i("live: no source configured");
            return;
        }
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<List<LiveApi.Group>>() {
            @Override public List<LiveApi.Group> run() throws Exception {
                return LiveApi.parse(LiveApi.fetch(url));
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
}
