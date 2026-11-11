package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 直播第二层：频道列表。
 *
 * 聚合源里同一个台会出现好几次（不同线路/不同源），这里同名只显示一行，
 * 其余地址作为备用线一起交给播放器 —— 主线路断了它会自己换下一条，不用手点。
 */
public class LiveListActivity extends Activity {

    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_TITLE = "title";

    private final List<LiveApi.Channel> channels = new ArrayList<LiveApi.Channel>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_vod_list);
        Screen.apply(this);
        int idx = getIntent().getIntExtra(EXTRA_INDEX, -1);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView tvTitle = (TextView) findViewById(R.id.tvVodTitle);
        tvTitle.setText(title == null ? getString(R.string.live_title) : title);
        TextView tvState = (TextView) findViewById(R.id.tvVodState);
        ListView list = (ListView) findViewById(R.id.lvVideos);
        if (idx >= 0 && idx < LiveApi.CACHE.size()) {
            Set<String> seen = new HashSet<String>();
            List<LiveApi.Channel> all = LiveApi.CACHE.get(idx).channels;
            for (int i = 0; i < all.size(); i++) {
                LiveApi.Channel c = all.get(i);
                if (seen.add(c.name)) { channels.add(c); }
            }
        }
        if (channels.isEmpty()) {
            tvState.setVisibility(View.VISIBLE);
            tvState.setText(R.string.vodpg_empty);
        }
        list.setAdapter(new ChannelAdapter());
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                LiveApi.Channel c = channels.get(pos);
                String[] alts = LiveApi.urlsFor(c.name, c.url);
                Intent i = new Intent(LiveListActivity.this, PlayerActivity.class);
                i.putExtra(PlayerActivity.EXTRA_URL, c.url);
                i.putExtra(PlayerActivity.EXTRA_NAME, c.name);
                i.putExtra(PlayerActivity.EXTRA_LIVE, true);
                i.putExtra(PlayerActivity.EXTRA_KIND, "live");
                if (alts.length > 0) { i.putExtra(PlayerActivity.EXTRA_ALT, alts); }
                startActivity(i);
                CLog.i("live: play " + c.name + " alts=" + alts.length);
            }
        });
    }

    private final class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return channels.size(); }
        @Override public Object getItem(int p) { return channels.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) { v = LayoutInflater.from(LiveListActivity.this).inflate(R.layout.item_vod_video, parent, false); }
            ((TextView) v.findViewById(R.id.tvName)).setText(channels.get(pos).name);
            ((TextView) v.findViewById(R.id.tvRemark)).setVisibility(View.GONE);
            return v;
        }
    }
}
