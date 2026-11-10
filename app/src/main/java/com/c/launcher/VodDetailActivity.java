package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 点播第三层：详情 + 线路切换 + 选集。
 * 线路来自 vod_play_from / vod_play_url 的 $$$ 分段：一个源常常有好几条线路，
 * 之前只取第一条，所以「换了线路还是同一个」。
 */
public class VodDetailActivity extends Activity {

    public static final String EXTRA_BASE = "base";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_NAME = "name";

    private GridView gridEps;
    private TextView tvState, tvName, tvMeta, tvDesc;
    private ImageView ivCover;
    private LinearLayout lineBar;
    private EpAdapter adapter;
    private final List<String> epNames = new ArrayList<String>();
    private final List<String> epUrls = new ArrayList<String>();
    private String base, vodId;
    private SourceApi.Detail detail;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_vod_detail);
        Screen.apply(this);
        base = getIntent().getStringExtra(EXTRA_BASE);
        vodId = getIntent().getStringExtra(EXTRA_ID);
        tvState = (TextView) findViewById(R.id.tvVodState);
        tvName = (TextView) findViewById(R.id.tvDetailName);
        tvMeta = (TextView) findViewById(R.id.tvDetailMeta);
        tvDesc = (TextView) findViewById(R.id.tvDetailDesc);
        ivCover = (ImageView) findViewById(R.id.ivCover);
        lineBar = (LinearLayout) findViewById(R.id.lineBar);
        gridEps = (GridView) findViewById(R.id.gridEps);
        tvName.setText(getIntent().getStringExtra(EXTRA_NAME));
        adapter = new EpAdapter();
        gridEps.setAdapter(adapter);
        gridEps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { play(pos); }
        });
        load();
    }

    private void load() {
        tvState.setText(R.string.vodpg_loading);
        VodAsync.go(this, new VodAsync.Job<SourceApi.Detail>() {
            @Override public SourceApi.Detail run() throws Exception {
                return SourceApi.detail(base, vodId, VodDetailActivity.this);
            }
        }, new VodAsync.Done<SourceApi.Detail>() {
            @Override public void ok(SourceApi.Detail d) {
                detail = d;
                if (d == null || d.lines.isEmpty()) {
                    tvState.setVisibility(View.VISIBLE);
                    tvState.setText(R.string.vodpg_empty);
                    CLog.e("plugin: detail id=" + vodId + " no playable lines");
                    return;
                }
                tvState.setVisibility(View.GONE);
                if (d.name != null) { tvName.setText(d.name); }
                StringBuilder meta = new StringBuilder();
                if (d.year != null) { meta.append(d.year).append("  "); }
                if (d.area != null) { meta.append(d.area); }
                tvMeta.setText(meta.toString().trim());
                tvDesc.setText(d.desc == null ? "" : d.desc.replaceAll("<[^>]+>", " ").trim());
                buildLines();
                showLine(0);
                CLog.i("plugin: detail id=" + vodId + " lines=" + d.lines.size()
                        + " eps=" + d.lines.get(0).eps.size());
                loadCover(d.pic);
            }
            @Override public void fail(String reason) {
                tvState.setVisibility(View.VISIBLE);
                tvState.setText(getString(R.string.vodpg_failed, reason));
                CLog.e("plugin: detail id=" + vodId + " failed " + reason);
            }
        });
    }

    private void buildLines() {
        lineBar.removeAllViews();
        for (int i = 0; i < detail.lines.size(); i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setText(detail.lines.get(i).name);
            btn.setTextSize(13f);
            btn.setFocusable(true);
            btn.setBackgroundResource(R.drawable.bg_dock_item);
            btn.setTextColor(getResources().getColor(R.color.text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
            lp.rightMargin = dp(8);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showLine(idx); }
            });
            lineBar.addView(btn);
        }
    }

    private void showLine(int idx) {
        epNames.clear();
        epUrls.clear();
        if (detail != null && idx < detail.lines.size()) {
            for (SourceApi.Ep e : detail.lines.get(idx).eps) {
                epNames.add(e.name);
                epUrls.add(e.url);
            }
        }
        adapter.notifyDataSetChanged();
        for (int i = 0; i < lineBar.getChildCount(); i++) {
            lineBar.getChildAt(i).setAlpha(i == idx ? 1f : 0.55f);
        }
        CLog.i("plugin: line=" + (detail != null && idx < detail.lines.size()
                ? detail.lines.get(idx).name : "?") + " eps=" + epNames.size());
    }

    private void play(int pos) {
        if (pos < 0 || pos >= epUrls.size()) { return; }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, epUrls.get(pos));
        i.putExtra(PlayerActivity.EXTRA_NAME, epNames.get(pos));
        startActivity(i);
        CLog.i("plugin: play ep=" + epNames.get(pos));
    }

    /** 封面失败不影响选集，纯粹是好看 */
    private void loadCover(final String pic) {
        if (pic == null || pic.length() < 8) { return; }
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(pic).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("User-Agent", "CDesk/1.0");
                    InputStream in = c.getInputStream();
                    bmp = BitmapFactory.decodeStream(in);
                    in.close();
                    c.disconnect();
                } catch (Throwable t) {
                    CLog.e("cover failed: " + t);
                }
                final Bitmap b2 = bmp;
                if (b2 == null) { return; }
                runOnUiThread(new Runnable() {
                    @Override public void run() { ivCover.setImageBitmap(b2); }
                });
            }
        }).start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class EpAdapter extends BaseAdapter {
        @Override public int getCount() { return epNames.size(); }
        @Override public Object getItem(int p) { return epNames.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            TextView tv;
            if (cv == null) {
                tv = new TextView(VodDetailActivity.this);
                tv.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
                tv.setBackgroundResource(R.drawable.bg_media_card);
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(getResources().getColor(R.color.text_primary));
                tv.setTextSize(14f);
                tv.setSingleLine(true);
                tv.setFocusable(true);
                tv.setClickable(true);
            } else {
                tv = (TextView) cv;
            }
            tv.setText(epNames.get(pos));
            return tv;
        }
    }
}
