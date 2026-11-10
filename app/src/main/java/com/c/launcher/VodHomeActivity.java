package com.c.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 点播第一层：分类宫格。
 * 以前是一个 ListView 塞三层、靠 up() 回退，进去就出不来。
 * 现在按影视仓的结构拆开：分类 → 列表 → 详情选集 → 播放。
 */
public class VodHomeActivity extends Activity {

    private GridView grid;
    private TextView tvState;
    private final List<SourceApi.Cat> cats = new ArrayList<SourceApi.Cat>();
    private CatAdapter adapter;
    private String base;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_vod_home);
        Screen.apply(this);
        base = getSharedPreferences("cdesk_core", MODE_PRIVATE).getString("vod_api", "");
        grid = (GridView) findViewById(R.id.gridCats);
        tvState = (TextView) findViewById(R.id.tvVodState);
        adapter = new CatAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { open(pos); }
        });
        findViewById(R.id.btnVodSource).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSourceSettings(); }
        });
        findViewById(R.id.btnVodSearch).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSearch(); }
        });
        load();
    }

    private void load() {
        if (base == null || base.trim().length() == 0) {
            tvState.setVisibility(View.VISIBLE);
            tvState.setText(R.string.vodpg_no_source);
            CLog.i("plugin: home skipped, no source");
            return;
        }
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<List<SourceApi.Cat>>() {
            @Override public List<SourceApi.Cat> run() throws Exception { return SourceApi.categories(base); }
        }, new VodAsync.Done<List<SourceApi.Cat>>() {
            @Override public void ok(List<SourceApi.Cat> v) {
                cats.clear();
                if (v != null) { cats.addAll(v); }
                adapter.notifyDataSetChanged();
                if (cats.isEmpty()) {
                    tvState.setVisibility(View.VISIBLE);
                    tvState.setText(R.string.vodpg_empty);
                } else {
                    tvState.setVisibility(View.GONE);
                }
                CLog.i("plugin: home ok cats=" + cats.size());
            }
            @Override public void fail(String reason) {
                tvState.setVisibility(View.VISIBLE);
                tvState.setText(getString(R.string.vodpg_failed, reason));
                CLog.e("plugin: home failed " + reason);
            }
        });
    }

    private void open(int pos) {
        SourceApi.Cat c = cats.get(pos);
        Intent i = new Intent(this, VodListActivity.class);
        i.putExtra(VodListActivity.EXTRA_BASE, base);
        i.putExtra(VodListActivity.EXTRA_TID, c.id);
        i.putExtra(VodListActivity.EXTRA_TITLE, c.name);
        startActivity(i);
    }

    /**
     * 换源 = 直接在这填接口地址。
     * 以前点「换源」只是跳设置页，可设置页里根本没有这一项，等于让人无处可填。
     */
    private void openSourceSettings() {
        final EditText et = new EditText(this);
        et.setHint(R.string.vodpg_api_hint);
        et.setSingleLine(true);
        if (base != null) { et.setText(base); }
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.vodpg_api_title)
                .setMessage(R.string.vodpg_api_msg)
                .setView(et)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String v = et.getText().toString().trim();
                        getSharedPreferences("cdesk_core", MODE_PRIVATE)
                                .edit().putString("vod_api", v).commit();
                        base = v;
                        CLog.i("plugin: api saved " + v);
                        load();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openSearch() {
        final EditText et = new EditText(this);
        et.setHint(R.string.vodpg_search_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.vodpg_search)
                .setView(et)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String k = et.getText().toString().trim();
                        if (k.length() == 0) { return; }
                        Intent i = new Intent(VodHomeActivity.this, VodListActivity.class);
                        i.putExtra(VodListActivity.EXTRA_BASE, base);
                        i.putExtra(VodListActivity.EXTRA_KEY, k);
                        i.putExtra(VodListActivity.EXTRA_TITLE, k);
                        startActivity(i);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final class CatAdapter extends BaseAdapter {
        @Override public int getCount() { return cats.size(); }
        @Override public Object getItem(int p) { return cats.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) { v = LayoutInflater.from(VodHomeActivity.this).inflate(R.layout.item_vod_cat, parent, false); }
            ((TextView) v).setText(cats.get(pos).name);
            return v;
        }
    }
}
