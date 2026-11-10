package com.c.launcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐：直接扫本机歌曲（MediaStore），不需要网络也不需要配源。
 * 车机/电视上放的歌基本都存在 U 盘或本机，扫出来列表就能播。
 */
public class MusicHomeActivity extends Activity {

    private final List<String> titles = new ArrayList<String>();
    private final List<String> artists = new ArrayList<String>();
    private final List<String> paths = new ArrayList<String>();
    private TextView tvState;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_vod_list);
        Screen.apply(this);
        ((TextView) findViewById(R.id.tvVodTitle)).setText(R.string.music_title);
        tvState = (TextView) findViewById(R.id.tvVodState);
        ListView list = (ListView) findViewById(R.id.lvVideos);
        scan();
        list.setAdapter(new SongAdapter());
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Intent i = new Intent(MusicHomeActivity.this, PlayerActivity.class);
                i.putExtra(PlayerActivity.EXTRA_URL, paths.get(pos));
                i.putExtra(PlayerActivity.EXTRA_NAME, titles.get(pos));
                startActivity(i);
                CLog.i("music: play " + titles.get(pos));
            }
        });
    }

    private void scan() {
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
                artists.add(a == null ? "" : a);
                paths.add(d);
            }
        } catch (Throwable t) {
            CLog.e("music: scan failed " + t);
        } finally {
            if (c != null) { try { c.close(); } catch (Throwable ignored) { } }
        }
        CLog.i("music: songs=" + titles.size());
        tvState.setVisibility(titles.isEmpty() ? View.VISIBLE : View.GONE);
        if (titles.isEmpty()) { tvState.setText(R.string.musicpg_empty); }
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
            String a = artists.get(pos);
            if (a == null || a.length() == 0 || "<unknown>".equals(a)) { r.setVisibility(View.GONE); }
            else { r.setVisibility(View.VISIBLE); r.setText(a); }
            return v;
        }
    }
}
