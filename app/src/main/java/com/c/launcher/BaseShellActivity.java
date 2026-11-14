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
import android.widget.AdapterView;
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
    protected View boxEmpty;
    protected Button btnPaste;
    protected Button btnLoad;
    protected Button btnBack;

    protected final List<PlaylistParser.Item> items = new ArrayList<PlaylistParser.Item>();
    protected ShellAdapter adapter;

    private final Handler ui = new Handler();

    /** 记住上次导入地址的 prefs key */
    protected abstract String prefKey();

    /** 标题文案 */
    protected abstract int titleRes();

    /** 空态标题文案 */
    protected abstract int emptyTitleRes();

    /** 点击某一条 */
    protected abstract void onItemSelected(PlaylistParser.Item it);

    /** 手机推送的目标类别：live / music（影视壳自己处理，是 vod） */
    protected abstract String targetKind();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CLog.i("BaseShellActivity onCreate");
        Screen.apply(this);
        setContentView(R.layout.activity_playlist);
        ThemeManager.Palette p = ThemeManager.current(this);
        applyShellTheme(p);
        onShellReady(p);
    }

    /** 视觉外壳：背景、标题色、输入框色、按钮样式。三个壳共用，只做纯视觉，不含行为。 */
    protected void applyShellTheme(ThemeManager.Palette p) {
        findViewById(android.R.id.content).setBackgroundColor(p.bgTop);
        View rootShell = findViewById(R.id.rootShell);
        if (rootShell != null) rootShell.setBackgroundColor(p.bgTop);

        TextView title = (TextView) findViewById(R.id.tvShellTitle);
        title.setText(titleRes());
        title.setTextColor(p.isLight ? p.fg : p.accent);

        etSource = (EditText) findViewById(R.id.etSource);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvStatus.setTextColor(p.dim);
        lvItems = (ListView) findViewById(R.id.lvItems);

        btnLoad = (Button) findViewById(R.id.btnLoad);
        btnBack = (Button) findViewById(R.id.btnBack);
        btnPaste = (Button) findViewById(R.id.btnPaste);
        boxEmpty = findViewById(R.id.boxEmpty);
        ThemeManager.styleButton(btnLoad, p);
        ThemeManager.styleButton(btnBack, p);
        if (btnPaste != null) ThemeManager.styleButton(btnPaste, p);
        etSource.setTextColor(p.fg);
    }

    /** 壳层就绪后的行为绑定：列表、点击、加载、返回、空态、恢复上次源。
     *  直播/音乐壳用默认实现；影视壳覆盖此方法实现多级导航。 */
    protected void onShellReady(ThemeManager.Palette p) {
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

        lvItems.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                if (pos < 0 || pos >= items.size()) return false;
                return toggleFavorite(items.get(pos));
            }
        });

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startLoad(); }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        if (btnPaste != null) {
            btnPaste.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // 电视上打字太痛苦，优先用剪贴板
                    String clip = readClipboard();
                    if (clip == null || clip.length() == 0) {
                        Toast.makeText(BaseShellActivity.this, R.string.shell_empty,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    etSource.setText(clip);
                    startLoad();
                }
            });
        }

        refreshEmpty();

        String saved = prefs().getString(prefKey(), "");
        if (saved != null && saved.length() > 0) {
            etSource.setText(saved);
            load(saved);
        }
    }

    protected String readClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasText()) return cm.getText().toString().trim();
        } catch (Throwable ignored) { }
        return null;
    }

    /**
     * 长按收藏 / 取消收藏。
     * 行自己接住点击之后，ListView 的长按回调不再触发，所以两处共用这份逻辑。
     */
    private boolean toggleFavorite(PlaylistParser.Item it) {
        if (it == null || it.url == null) return false;   // 分组头不收藏
        if (Favorites.has(this, it.url)) {
            Favorites.remove(this, it.url);
            Toast.makeText(this, R.string.fav_removed, Toast.LENGTH_SHORT).show();
        } else {
            Favorites.add(this, it.name, it.url, targetKind());
            Toast.makeText(this, R.string.fav_added, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** 有内容就藏起空状态，没内容就显示（文案按壳区分：直播/音乐共用通用导入说明） */
    private void refreshEmpty() {
        if (boxEmpty == null) return;
        boolean empty = items.isEmpty();
        boxEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        lvItems.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            TextView et = (TextView) boxEmpty.findViewWithTag("emptyTitle");
            if (et != null) et.setText(emptyTitleRes());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        consumePendingSource();
    }

    /** 手机在传输页粘的地址：回到本页自动填入并加载，省得用遥控器打字 */
    private void consumePendingSource() {
        try {
            SharedPreferences sp = prefs();
            String pending = sp.getString("pending_src", null);
            if (pending == null || pending.length() == 0) return;
            String want = sp.getString("pending_target", "");
            if (want.length() > 0 && !want.equals(targetKind())) return;
            long at = sp.getLong("pending_at", 0L);
            sp.edit().remove("pending_src").remove("pending_at").commit();
            if (System.currentTimeMillis() - at > 10L * 60L * 1000L) return;

            etSource.setText(pending);
            prefs().edit().putString(prefKey(), pending).commit();
            loadPending(pending);
            Toast.makeText(this, R.string.shell_recv_from_phone, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
    }

    /** 消费手机推送的源地址后的加载入口，默认用通用 load；影视壳覆盖为多级加载 */
    protected void loadPending(String src) { load(src); }

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
                List<PlaylistParser.Item> expanded = null;
                if (text != null && TvBoxConfig.looks(text)) {
                    expanded = new ArrayList<PlaylistParser.Item>();
                    List<TvBoxConfig.Live> lives = TvBoxConfig.lives(text, BaseShellActivity.this);
                    // 一份订阅里常挂十几个直播列表，里面还有纯 IPv6、根本连不通的；
                    // 一条条串行拉会让人对着"加载中"等好几分钟，这里给整体 25 秒预算，超了就带着已有结果先显示。
                    long deadline = System.currentTimeMillis() + 25000L;
                    for (int i = 0; i < lives.size(); i++) {
                        if (System.currentTimeMillis() > deadline) { break; }
                        TvBoxConfig.Live lv = lives.get(i);
                        try {
                            List<PlaylistParser.Item> one = PlaylistParser.parse(fetchQuick(lv.url, 6000));
                            for (int j = 0; j < one.size(); j++) {
                                PlaylistParser.Item it = one.get(j);
                                expanded.add(new PlaylistParser.Item(it.name, lv.name, it.url));
                            }
                        } catch (Throwable ignored) { }
                    }
                }
                final String fText = text;
                final String fErr = err;
                final List<PlaylistParser.Item> fExpanded = expanded;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (fText == null) {
                            tvStatus.setText(getString(R.string.shell_failed) + (fErr == null ? "" : " (" + fErr + ")"));
                            items.clear();
                            adapter.notifyDataSetChanged();
                            refreshEmpty();
                            return;
                        }
                        List<PlaylistParser.Item> raw = fExpanded;
                        if (raw == null) {
                            raw = PlaylistParser.parseMusicJson(fText);
                            if (raw.isEmpty()) raw = PlaylistParser.parse(fText);
                        }
                        List<PlaylistParser.Item> parsed = new ArrayList<PlaylistParser.Item>();
                        for (int i = 0; i < raw.size(); i++) {
                            if (ContentFilter.blocked(BaseShellActivity.this, raw.get(i).name)) continue;
                            parsed.add(raw.get(i));
                        }
                        items.clear();
                        withGroups(parsed, items);
                        adapter.notifyDataSetChanged();
                        tvStatus.setText(parsed.isEmpty()
                                ? getString(R.string.shell_empty)
                                : getString(R.string.shell_loaded) + " " + parsed.size());
                        refreshEmpty();
                        if (!parsed.isEmpty()) lvItems.requestFocus();
                    }
                });
            }
        }).start();
    }

    /** 拉订阅里那些直播列表用的快通道：连不通就赶紧跳过，不要拖住整页 */
    private String fetchQuick(String url, int connMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connMs);
        c.setReadTimeout(connMs + 4000);
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
            final PlaylistParser.Item it = items.get(position);
            ThemeManager.Palette p = ThemeManager.current(BaseShellActivity.this);
            TextView tv = convertView instanceof TextView
                    ? (TextView) convertView : new TextView(BaseShellActivity.this);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            if (it.url == null) {
                // 分组头：不可点、不可聚焦，只作分隔
                tv.setText(it.name);
                tv.setTextColor(p.isLight ? 0xFF8A6A16 : p.accent);
                tv.setTextSize(14f);
                tv.setPadding(dp(20), dp(18), dp(20), dp(8));
                tv.setBackgroundColor(0x00000000);
                tv.setOnClickListener(null);
                tv.setOnLongClickListener(null);
                tv.setClickable(false);
                tv.setFocusable(false);
            } else {
                tv.setText(it.name);
                tv.setTextColor(p.fg);
                tv.setTextSize(17f);
                tv.setPadding(dp(20), dp(16), dp(20), dp(16));
                tv.setBackgroundResource(R.drawable.bg_row);
                tv.setFocusable(true);
                // setItemsCanFocus(true) 时触摸和 DPAD_CENTER 都落在行自己身上，
                // ListView 的 OnItemClickListener 不会再触发。bg_row 早就画好了
                // state_focused 焦点框，缺的就是这一下点击接线。
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { onItemSelected(it); }
                });
                tv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { return toggleFavorite(it); }
                });
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
