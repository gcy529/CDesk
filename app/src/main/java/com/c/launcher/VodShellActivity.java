package com.c.launcher;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 影视壳：源 → 分类 → 影片 → 剧集 → 播放。
 *
 * 三种地址都能填：
 *  1. TVBox 配置（带 sites 的 json）—— 先列源，选一个再进分类；
 *  2. 苹果CMS 接口（api.php/provide/vod）—— 直接进分类；
 *  3. 普通播放列表（M3U / TXT / JSON）—— 直接列成可播条目。
 *
 * 接口与内容全部由使用者自行提供，本应用不内置、不推荐、不分发。
 */
public class VodShellActivity extends Activity {

    private static final int LEVEL_SITES = 0;
    private static final int LEVEL_CATS = 1;
    private static final int LEVEL_VIDEOS = 2;
    private static final int LEVEL_EPS = 3;

    private final Handler ui = new Handler();
    private EditText etSource;
    private TextView tvStatus;
    private TextView tvTitle;
    private ListView lvItems;
    private Button btnBack;
    private View boxEmpty;

    private String base = "";
    private String apiBase = "";
    private int level = LEVEL_CATS;
    private boolean configMode = false;
    private boolean flatMode = false;

    private List<TvBoxConfig.Site> sites = new ArrayList<TvBoxConfig.Site>();
    private List<SourceApi.Cat> cats = new ArrayList<SourceApi.Cat>();
    private List<SourceApi.Video> videos = new ArrayList<SourceApi.Video>();
    private List<SourceApi.Ep> eps = new ArrayList<SourceApi.Ep>();
    private List<PlaylistParser.Item> flat = new ArrayList<PlaylistParser.Item>();

    private String catName = "";
    private String videoName = "";

    private final List<String> rows = new ArrayList<String>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void attachBaseContext(Context b) {
        super.attachBaseContext(LangManager.wrap(b));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        CLog.i("VodShellActivity onCreate");
        Screen.apply(this);
        setContentView(R.layout.activity_playlist);

        ThemeManager.Palette p = ThemeManager.current(this);
        findViewById(android.R.id.content).setBackgroundColor(p.bgTop);
        View root = findViewById(R.id.rootShell);
        if (root != null) root.setBackgroundColor(p.bgTop);

        tvTitle = (TextView) findViewById(R.id.tvShellTitle);
        etSource = (EditText) findViewById(R.id.etSource);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        lvItems = (ListView) findViewById(R.id.lvItems);
        boxEmpty = findViewById(R.id.boxEmpty);
        btnBack = (Button) findViewById(R.id.btnBack);
        Button btnLoad = (Button) findViewById(R.id.btnLoad);
        Button btnPaste = (Button) findViewById(R.id.btnPaste);

        tvTitle.setTextColor(p.isLight ? p.fg : p.accent);
        etSource.setTextColor(p.fg);
        tvStatus.setTextColor(p.dim);
        ThemeManager.styleButton(btnLoad, p);
        ThemeManager.styleButton(btnBack, p);
        if (btnPaste != null) ThemeManager.styleButton(btnPaste, p);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView tv = new TextView(VodShellActivity.this);
                tv.setText(rows.get(pos));
                tv.setTextSize(17f);
                tv.setTextColor(ThemeManager.current(VodShellActivity.this).fg);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setPadding(dp(20), dp(15), dp(20), dp(15));
                tv.setBackgroundResource(R.drawable.bg_row);
                tv.setFocusable(true);
                return tv;
            }
        };
        lvItems.setAdapter(adapter);
        lvItems.setItemsCanFocus(true);
        lvItems.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        lvItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) { open(pos); }
        });
        lvItems.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                // 影片层：长按收藏影片名（记忆 apiBase + 影片，点开直接进这部剧）
                if (level == LEVEL_VIDEOS && !flatMode && pos >= 0 && pos < videos.size()) {
                    SourceApi.Video vid = videos.get(pos);
                    String key = "vod:" + apiBase + ":" + vid.id;
                    SharedPreferences p = getSharedPreferences("cdesk_fav", Context.MODE_PRIVATE);
                    if (p.contains(key)) {
                        p.edit().remove(key).commit();
                        Toast.makeText(VodShellActivity.this, R.string.fav_removed, Toast.LENGTH_SHORT).show();
                    } else {
                        try {
                            org.json.JSONObject o = new org.json.JSONObject();
                            o.put("name", vid.name);
                            o.put("api", apiBase);
                            o.put("vod", vid.id);
                            p.edit().putString(key, o.toString()).commit();
                            Toast.makeText(VodShellActivity.this, R.string.fav_added, Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) { }
                    }
                    return true;
                }
                return false;
            }
        });

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startFromInput(); }
        });
        if (btnPaste != null) {
            btnPaste.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String c = clip();
                    if (c == null || c.length() == 0) {
                        Toast.makeText(VodShellActivity.this, R.string.shell_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    etSource.setText(c);
                    startFromInput();
                }
            });
        }
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { up(); }
        });

        String savedBase = prefs().getString("vod_api", "");
        if (savedBase != null && savedBase.length() > 0) {
            base = savedBase;
            etSource.setText(base);
            loadRoot();
        } else {
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String want = prefs().getString("pending_target", "");
        if (want.length() > 0 && !"vod".equals(want)) return;
        String pending = prefs().getString("pending_src", null);
        if (pending == null || pending.length() == 0) return;
        long at = prefs().getLong("pending_at", 0L);
        prefs().edit().remove("pending_src").remove("pending_at").commit();
        if (System.currentTimeMillis() - at > 10L * 60L * 1000L) return;
        etSource.setText(pending);
        startFromInput();
        Toast.makeText(this, R.string.shell_recv_from_phone, Toast.LENGTH_SHORT).show();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("cdesk_shell", Context.MODE_PRIVATE);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String clip() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasText()) return cm.getText().toString().trim();
        } catch (Throwable ignored) { }
        return null;
    }

    private void startFromInput() {
        String s = etSource.getText() == null ? "" : etSource.getText().toString().trim();
        if (s.length() == 0) {
            String c = clip();
            if (c == null || c.length() == 0) {
                Toast.makeText(this, R.string.shell_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            s = c;
            etSource.setText(s);
        }
        base = s;
        prefs().edit().putString("vod_api", base).commit();
        loadRoot();
    }

    /** 取一次文本，判断是 TVBox 配置、单接口，还是普通播放列表 */
    private void loadRoot() {
        level = LEVEL_CATS;
        configMode = false;
        flatMode = false;
        apiBase = base;
        sites.clear(); cats.clear(); videos.clear(); eps.clear(); flat.clear();
        refresh();
        tvStatus.setText(R.string.shell_loading);

        new Thread(new Runnable() {
            @Override public void run() {
                String text = null;
                String err = null;
                try {
                    text = SourceApi.get(base);
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName();
                }

                final List<TvBoxConfig.Site> fs = new ArrayList<TvBoxConfig.Site>();
                final List<SourceApi.Cat> fc = new ArrayList<SourceApi.Cat>();
                final List<PlaylistParser.Item> ff = new ArrayList<PlaylistParser.Item>();
                boolean isConfig = false;

                if (text != null) {
                    if (TvBoxConfig.looks(text)) {
                        isConfig = true;
                        fs.addAll(TvBoxConfig.sites(text));
                    } else {
                        fc.addAll(SourceApi.catsFrom(text));
                        if (fc.isEmpty()) ff.addAll(PlaylistParser.parse(text));
                    }
                }
                final boolean config = isConfig;
                final String fe = err;

                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (config && !fs.isEmpty()) {
                            configMode = true;
                            sites.addAll(fs);
                            // 上次选的源如果还在这份配置里，就直接进分类：
                            // 一个订阅能挂一百多个源，每次重新在长列表里找一遍太难受。
                            String lastApi = prefs().getString("vod_site_api", "");
                            boolean hit = false;
                            for (int i = 0; i < sites.size(); i++) {
                                if (sites.get(i).api.equals(lastApi)) { hit = true; break; }
                            }
                            if (hit) {
                                apiBase = lastApi;
                                level = LEVEL_CATS;
                                tvStatus.setText(getString(R.string.shell_loaded) + " " + sites.size());
                                refresh();
                                loadCats();
                                return;
                            }
                            level = LEVEL_SITES;
                            tvStatus.setText(getString(R.string.shell_loaded) + " " + fs.size());
                        } else if (!fc.isEmpty()) {
                            cats.addAll(fc);
                            level = LEVEL_CATS;
                            tvStatus.setText(getString(R.string.shell_loaded) + " " + fc.size());
                        } else if (!ff.isEmpty()) {
                            flatMode = true;
                            for (int i = 0; i < ff.size(); i++) {
                                if (ContentFilter.blocked(VodShellActivity.this, ff.get(i).name)) continue;
                                flat.add(ff.get(i));
                            }
                            level = LEVEL_VIDEOS;
                            tvStatus.setText(getString(R.string.shell_loaded) + " " + ff.size());
                        } else {
                            level = LEVEL_CATS;
                            tvStatus.setText(getString(R.string.shell_failed) + (fe == null ? "" : " (" + fe + ")"));
                        }
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void loadCats() {
        tvStatus.setText(R.string.shell_loading);
        new Thread(new Runnable() {
            @Override public void run() {
                List<SourceApi.Cat> got = null;
                String err = null;
                try { got = SourceApi.categories(apiBase); }
                catch (Throwable t) { err = t.getClass().getSimpleName(); }
                final List<SourceApi.Cat> f = got;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (f == null || f.isEmpty()) {
                            tvStatus.setText(getString(R.string.shell_failed) + (fe == null ? "" : " (" + fe + ")"));
                            return;
                        }
                        cats.clear(); cats.addAll(f);
                        level = LEVEL_CATS;
                        tvStatus.setText(getString(R.string.shell_loaded) + " " + f.size());
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void loadVideos(final String typeId) {
        tvStatus.setText(R.string.shell_loading);
        new Thread(new Runnable() {
            @Override public void run() {
                List<SourceApi.Video> got = null;
                String err = null;
                try { got = SourceApi.videos(apiBase, typeId, 1); }
                catch (Throwable t) { err = t.getClass().getSimpleName(); }
                final List<SourceApi.Video> f = got;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (f == null) {
                            tvStatus.setText(getString(R.string.shell_failed) + (fe == null ? "" : " (" + fe + ")"));
                            return;
                        }
                        videos.clear();
                        for (int i = 0; i < f.size(); i++) {
                            if (ContentFilter.blocked(VodShellActivity.this, f.get(i).name)) continue;
                            videos.add(f.get(i));
                        }
                        level = LEVEL_VIDEOS;
                        tvStatus.setText(getString(R.string.shell_loaded) + " " + f.size());
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void loadEps(final String vodId, final String name) {
        tvStatus.setText(R.string.shell_loading);
        new Thread(new Runnable() {
            @Override public void run() {
                List<SourceApi.Ep> got = null;
                String err = null;
                try { got = SourceApi.episodes(apiBase, vodId, VodShellActivity.this); }
                catch (Throwable t) { err = t.getClass().getSimpleName(); }
                final List<SourceApi.Ep> f = got;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (f == null || f.isEmpty()) {
                            tvStatus.setText(getString(R.string.shell_play_failed) + (fe == null ? "" : " (" + fe + ")"));
                            return;
                        }
                        eps.clear(); eps.addAll(f);
                        videoName = name;
                        level = LEVEL_EPS;
                        tvStatus.setText(getString(R.string.shell_loaded) + " " + f.size());
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void open(int pos) {
        if (pos < 0) return;
        if (level == LEVEL_SITES) {
            if (pos >= sites.size()) return;
            apiBase = sites.get(pos).api;
            // 记住这次选的源，下次从同一个订阅进来直接进分类
            prefs().edit().putString("vod_site_api", apiBase).commit();
            level = LEVEL_CATS;
            loadCats();
        } else if (level == LEVEL_CATS) {
            if (pos >= cats.size()) return;
            catName = cats.get(pos).name;
            loadVideos(cats.get(pos).id);
        } else if (level == LEVEL_VIDEOS) {
            if (flatMode) {
                if (pos >= flat.size()) return;
                PlaylistParser.Item it = flat.get(pos);
                play(it.url, it.name);
            } else {
                if (pos >= videos.size()) return;
                SourceApi.Video v = videos.get(pos);
                loadEps(v.id, v.name);
            }
        } else {
            if (pos >= eps.size()) return;
            SourceApi.Ep e = eps.get(pos);
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_URL, e.url);
            i.putExtra(PlayerActivity.EXTRA_NAME, videoName + " " + e.name);
            i.putExtra(PlayerActivity.EXTRA_LIVE, false);
            i.putExtra(PlayerActivity.EXTRA_ALT, allLines(eps, pos));
            startActivity(i);
        }
    }

    private void up() {
        if (level == LEVEL_EPS) {
            level = LEVEL_VIDEOS;
            refresh();
        } else if (level == LEVEL_VIDEOS) {
            if (flatMode) finish();
            else { level = LEVEL_CATS; refresh(); }
        } else if (level == LEVEL_CATS) {
            if (configMode && !sites.isEmpty()) { level = LEVEL_SITES; refresh(); }
            else finish();
        } else {
            finish();
        }
    }

    private void play(String url, String name) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, url);
        i.putExtra(PlayerActivity.EXTRA_NAME, name);
        i.putExtra(PlayerActivity.EXTRA_LIVE, false);
        startActivity(i);
    }

    /** 影片所有线路的播放地址，供播放器失败时自动切换 */
    static String[] allLines(List<SourceApi.Ep> eps, int from) {
        List<String> out = new ArrayList<String>();
        for (int i = from + 1; i < eps.size(); i++) {
            String u = eps.get(i).url;
            if (u != null && u.length() > 4) out.add(u);
        }
        return out.toArray(new String[out.size()]);
    }

    private void refresh() {
        rows.clear();
        if (level == LEVEL_SITES) {
            for (int i = 0; i < sites.size(); i++) rows.add(sites.get(i).name);
        } else if (level == LEVEL_CATS) {
            for (int i = 0; i < cats.size(); i++) rows.add(cats.get(i).name);
        } else if (level == LEVEL_VIDEOS) {
            if (flatMode) {
                for (int i = 0; i < flat.size(); i++) rows.add(flat.get(i).name);
            } else {
                for (int i = 0; i < videos.size(); i++) {
                    SourceApi.Video v = videos.get(i);
                    rows.add(v.remarks == null || v.remarks.length() == 0
                            ? v.name : v.name + "  ·  " + v.remarks);
                }
            }
        } else {
            for (int i = 0; i < eps.size(); i++) rows.add(eps.get(i).name);
        }
        adapter.notifyDataSetChanged();

        boolean empty = rows.isEmpty();
        if (boxEmpty != null) {
            boxEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                // 影视壳的空态要说清「待接入」：内容全靠用户自己导入，不内置
                TextView et = (TextView) boxEmpty.findViewWithTag("emptyTitle");
                TextView eb = (TextView) boxEmpty.findViewWithTag("emptyBody");
                if (et != null) et.setText(R.string.vod_empty_title);
                if (eb != null) eb.setText(R.string.vod_empty_body);
            }
        }
        lvItems.setVisibility(empty ? View.GONE : View.VISIBLE);

        boolean root = (level == LEVEL_SITES) || (level == LEVEL_CATS && !configMode)
                || (level == LEVEL_VIDEOS && flatMode);
        btnBack.setText(root ? R.string.transfer_back : R.string.shell_back_up);

        String suffix = "";
        if (level == LEVEL_CATS && configMode) suffix = "";
        if (level == LEVEL_VIDEOS && !flatMode) suffix = " · " + catName;
        if (level == LEVEL_EPS) suffix = " · " + videoName;
        tvTitle.setText(getString(R.string.vod_title) + suffix);

        if (!empty) lvItems.requestFocus();
    }
}
