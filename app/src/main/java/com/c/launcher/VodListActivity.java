package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 点播第二层：分类下的影片列表，滑到底自动翻页 */
public class VodListActivity extends Activity {

    public static final String EXTRA_BASE = "base";
    public static final String EXTRA_TID = "tid";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_KEY = "key";

    private ListView list;
    private TextView tvState;
    private final List<SourceApi.Video> items = new ArrayList<SourceApi.Video>();
    private VideoAdapter adapter;
    private String base, tid, key;
    private int page = 1;
    private boolean loading, ended;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_vod_list);
        Screen.apply(this);
        base = getIntent().getStringExtra(EXTRA_BASE);
        tid = getIntent().getStringExtra(EXTRA_TID);
        key = getIntent().getStringExtra(EXTRA_KEY);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView tvTitle = (TextView) findViewById(R.id.tvVodTitle);
        tvTitle.setText(title == null ? getString(R.string.vod_title) : title);
        list = (ListView) findViewById(R.id.lvVideos);
        tvState = (TextView) findViewById(R.id.tvVodState);
        adapter = new VideoAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { open(pos); }
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) { }
            @Override public void onScroll(AbsListView v, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 2) { loadMore(); }
            }
        });
        loadMore();
    }

    private void loadMore() {
        if (loading || ended) { return; }
        loading = true;
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        final int want = page;
        VodAsync.go(this, new VodAsync.Job<List<SourceApi.Video>>() {
            @Override public List<SourceApi.Video> run() throws Exception {
                if (key != null && key.length() > 0) { return SourceApi.search(base, key); }
                return SourceApi.videos(base, tid, want);
            }
        }, new VodAsync.Done<List<SourceApi.Video>>() {
            @Override public void ok(List<SourceApi.Video> v) {
                loading = false;
                if (v == null || v.isEmpty()) {
                    ended = true;
                    if (items.isEmpty()) {
                        tvState.setVisibility(View.VISIBLE);
                        tvState.setText(R.string.vodpg_empty);
                    } else {
                        tvState.setVisibility(View.GONE);
                    }
                } else {
                    items.addAll(v);
                    page++;
                    adapter.notifyDataSetChanged();
                    tvState.setVisibility(View.GONE);
                }
                CLog.i("plugin: category tid=" + tid + " page=" + want
                        + " got=" + (v == null ? 0 : v.size()) + " total=" + items.size());
            }
            @Override public void fail(String reason) {
                loading = false;
                ended = true;
                tvState.setVisibility(View.VISIBLE);
                tvState.setText(getString(R.string.vodpg_failed, reason));
                CLog.e("plugin: category tid=" + tid + " page=" + want + " failed " + reason);
            }
        });
    }

    private void open(int pos) {
        SourceApi.Video v = items.get(pos);
        Intent i = new Intent(this, VodDetailActivity.class);
        i.putExtra(VodDetailActivity.EXTRA_BASE, base);
        i.putExtra(VodDetailActivity.EXTRA_ID, v.id);
        i.putExtra(VodDetailActivity.EXTRA_NAME, v.name);
        startActivity(i);
    }

    private final class VideoAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) { v = LayoutInflater.from(VodListActivity.this).inflate(R.layout.item_vod_video, parent, false); }
            SourceApi.Video it = items.get(pos);
            ((TextView) v.findViewById(R.id.tvName)).setText(it.name);
            TextView r = (TextView) v.findViewById(R.id.tvRemark);
            if (it.remarks == null || it.remarks.length() == 0) { r.setVisibility(View.GONE); }
            else { r.setVisibility(View.VISIBLE); r.setText(it.remarks); }
            return v;
        }
    }
}
