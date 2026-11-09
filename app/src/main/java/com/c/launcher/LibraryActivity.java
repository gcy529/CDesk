package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 收藏 + 最近播放：影音里进来，两个标签，遥控器左右切。 */
public class LibraryActivity extends Activity {

    private int tab = 0;   // 0=收藏 1=最近
    private TextView tvTabFav, tvTabHis;
    private ListView lv;
    private TextView tvEmpty;
    private final List<String> rows = new ArrayList<String>();
    private final List<Object> data = new ArrayList<Object>();   // Favorites.Item / History.Entry
    private ArrayAdapter<String> adapter;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Screen.apply(this);
        setContentView(R.layout.activity_library);

        ThemeManager.Palette p = ThemeManager.current(this);
        View root = findViewById(R.id.rootLibrary);
        WPManager.applyToRoot(this, root);
        TextView title = (TextView) findViewById(R.id.tvLibTitle);
        title.setTextColor(p.isLight ? p.fg : p.accent);
        tvTabFav = (TextView) findViewById(R.id.tabFav);
        tvTabHis = (TextView) findViewById(R.id.tabHis);
        lv = (ListView) findViewById(R.id.lvLib);
        tvEmpty = (TextView) findViewById(R.id.tvLibEmpty);

        View.OnClickListener tabClick = new View.OnClickListener() {
            @Override public void onClick(View v) { tab = (v.getId() == R.id.tabFav) ? 0 : 1; refresh(); }
        };
        tvTabFav.setOnClickListener(tabClick);
        tvTabHis.setOnClickListener(tabClick);
        findViewById(R.id.btnLibBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView tv = new TextView(LibraryActivity.this);
                tv.setText(rows.get(pos));
                tv.setTextSize(16f);
                tv.setTextColor(ThemeManager.current(LibraryActivity.this).fg);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(dp(18), dp(14), dp(18), dp(14));
                tv.setBackgroundResource(R.drawable.bg_row);
                return tv;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) { open(pos); }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                // 长按删除
                if (pos < 0 || pos >= data.size()) return false;
                if (tab == 0) {
                    Favorites.Item it = (Favorites.Item) data.get(pos);
                    Favorites.remove(LibraryActivity.this, it.url);
                } else {
                    History.Entry e = (History.Entry) data.get(pos);
                    History.remove(LibraryActivity.this, e.url);
                }
                Toast.makeText(LibraryActivity.this, R.string.fav_removed, Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            }
        });

        refresh();
    }

    private void refresh() {
        rows.clear();
        data.clear();
        if (tab == 0) {
            tvTabFav.setTextColor(0xFF4D6BFE);
            tvTabHis.setTextColor(0xFF8E9BAE);
            for (Favorites.Item it : Favorites.all(this)) {
                String tag = "vod".equals(it.kind) ? "[影视] " : "live".equals(it.kind) ? "[直播] " : "[音乐] ";
                rows.add(tag + it.name);
                data.add(it);
            }
        } else {
            tvTabFav.setTextColor(0xFF8E9BAE);
            tvTabHis.setTextColor(0xFF4D6BFE);
            List<History.Entry> hs = History.all(this);
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
            for (History.Entry e : hs) {
                String tag = "vod".equals(e.kind) ? "[影视] " : "live".equals(e.kind) ? "[直播] " : "[音乐] ";
                String prog = e.pos > 0 ? "  ·  " + fmtShort(e.pos) : "";
                rows.add(tag + e.name + prog + "  ·  " + f.format(new Date(e.at)));
                data.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        boolean empty = rows.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        lv.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void open(int pos) {
        if (pos < 0 || pos >= data.size()) return;
        String url;
        String name;
        String kind;
        if (tab == 0) {
            Favorites.Item it = (Favorites.Item) data.get(pos);
            url = it.url; name = it.name; kind = it.kind;
        } else {
            History.Entry e = (History.Entry) data.get(pos);
            url = e.url; name = e.name; kind = e.kind;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, url);
        i.putExtra(PlayerActivity.EXTRA_NAME, name);
        i.putExtra(PlayerActivity.EXTRA_LIVE, "live".equals(kind));
        startActivity(i);
    }

    private static String fmtShort(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return (s / 3600) + "h" + ((s % 3600) / 60) + "m";
        if (s >= 60) return (s / 60) + "m" + (s % 60) + "s";
        return s + "s";
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
