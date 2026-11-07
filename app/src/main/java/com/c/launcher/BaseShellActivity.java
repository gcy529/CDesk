package com.c.launcher;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 壳子基类：负责「导入用户自己的列表 → 解析 → 列出来」这条链路。
 * 三个壳（直播 / 点播 / 音乐）都从这里继承，只实现点击行为。
 *
 * 说明：本类不含任何内置内容源，也不下载除用户输入地址以外的任何东西。
 */
public abstract class BaseShellActivity extends Activity {

    protected EditText etSource;
    protected TextView tvStatus;
    protected ListView lvItems;

    protected final List<PlaylistParser.Item> items = new ArrayList<PlaylistParser.Item>();
    protected ShellAdapter adapter;

    private final Handler ui = new Handler();

    /** 记住上次导入地址的 prefs key */
    protected abstract String prefKey();

    /** 标题文案 */
    protected abstract int titleRes();

    /** 点击某一条 */
    protected abstract void onItemSelected(PlaylistParser.Item it);

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        ThemeManager.Palette p = ThemeManager.current(this);
        findViewById(android.R.id.content).setBackgroundColor(p.bgTop);

        TextView title = (TextView) findViewById(R.id.tvShellTitle);
        title.setText(titleRes());
        title.setTextColor(p.isLight ? p.fg : p.accent);

        etSource = (EditText) findViewById(R.id.etSource);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        lvItems = (ListView) findViewById(R.id.lvItems);

        Button btnLoad = (Button) findViewById(R.id.btnLoad);
        Button btnBack = (Button) findViewById(R.id.btnBack);
        ThemeManager.styleButton(btnLoad, p);
        ThemeManager.styleButton(btnBack, p);
        etSource.setTextColor(p.fg);

        adapter = new ShellAdapter();
        lvItems.setAdapter(adapter);
        lvItems.setItemsCanFocus(true);
        lvItems.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        lvItems.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Object o = adapter.getItem(position);
                if (o instanceof PlaylistParser.Item) onItemSelected((PlaylistParser.Item) o);
            }
        });

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startLoad(); }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        String saved = prefs().getString(prefKey(), "");
        if (saved != null && saved.length() > 0) {
            etSource.setText(saved);
            load(saved);
        }
    }

    protected SharedPreferences prefs() {
        return getSharedPreferences("cdesk_shell", Context.MODE_PRIVATE);
    }

    private void startLoad() {
        String src = etSource.getText() == null ? "" : etSource.getText().toString().trim();
        if (src.length() == 0) {
            // 输入框空着就试剪贴板，省得电视上打字
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasText()) src = cm.getText().toString().trim();
            } catch (Exception ignored) { }
            if (src.length() == 0) {
                Toast.makeText(this, R.string.shell_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            etSource.setText(src);
        }
        prefs().edit().putString(prefKey(), src).commit();
        load(src);
    }

    private void load(final String src) {
        tvStatus.setText(R.string.shell_loading);
        new Thread(new Runnable() {
            @Override public void run() {
                String text = null;
                String err = null;
                try {
                    if (src.startsWith("http://") || src.startsWith("https://")) {
                        text = fetch(src);
                    } else {
                        // 本地路径也能导
                        StringBuilder sb = new StringBuilder();
                        BufferedReader r = new BufferedReader(new java.io.FileReader(src));
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line).append('\n');
                        r.close();
                        text = sb.toString();
                    }
                } catch (Exception e) {
                    err = e.getClass().getSimpleName();
                }
                final String fText = text;
                final String fErr = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (fText == null) {
                            tvStatus.setText(getString(R.string.shell_failed) + (fErr == null ? "" : " (" + fErr + ")"));
                            return;
                        }
                        List<PlaylistParser.Item> parsed = PlaylistParser.parse(fText);
                        items.clear();
                        withGroups(parsed, items);
                        adapter.notifyDataSetChanged();
                        tvStatus.setText(parsed.isEmpty()
                                ? getString(R.string.shell_empty)
                                : getString(R.string.shell_loaded) + " " + parsed.size());
                        if (!parsed.isEmpty()) lvItems.requestFocus();
                    }
                });
            }
        }).start();
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "CDesk/1.0");
        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }

    /** 列表：分组标题 + 条目，两种行样式 */
    protected class ShellAdapter extends BaseAdapter {

        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int position) { return items.get(position); }

        @Override public long getItemId(int position) { return position; }

        @Override public boolean isEnabled(int position) { return items.get(position).url != null; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            PlaylistParser.Item it = items.get(position);
            ThemeManager.Palette p = ThemeManager.current(BaseShellActivity.this);
            TextView tv = new TextView(BaseShellActivity.this);
            tv.setTextSize(16f);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(dp(18), dp(14), dp(18), dp(14));
            if (it.url == null) {
                // 分组头
                tv.setText("— " + it.name + " —");
                tv.setTextColor(p.isLight ? 0xFF8A6A16 : p.accent);
                tv.setTextSize(14f);
            } else {
                tv.setText(it.name);
                tv.setTextColor(p.fg);
            }
            return tv;
        }
    }

    protected int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 把分组插进列表（分组头 url 置 null） */
    protected static void withGroups(List<PlaylistParser.Item> src, List<PlaylistParser.Item> out) {
        String cur = null;
        for (int i = 0; i < src.size(); i++) {
            PlaylistParser.Item it = src.get(i);
            String g = it.group == null ? "" : it.group;
            if (g.length() > 0 && !g.equals(cur)) {
                out.add(new PlaylistParser.Item(g, g, null));
                cur = g;
            }
            out.add(it);
        }
    }
}
