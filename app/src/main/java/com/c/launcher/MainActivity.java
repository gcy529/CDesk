package com.c.launcher;

import android.app.Activity;
import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * C桌面 主界面（测试版）。
 *
 * 视觉体系（按设计批评落地）：
 *  - 背景 = 大面积底色，内容全部坐在统一的半透明"玻璃"Dock/底座上
 *  - accent 只给小元素（焦点描边、圆点、按钮描边），不做大面积色块
 *  - 浅色主题大字用 fg（深灰），accent 只描边 —— 紫色大字在白底上很突兀
 *  - 焦点态 = 3dp 白描边 + 放大 1.06（Aerial 的做法，遥控器场景必须看清）
 *
 * 页面：应用 / 最近 / 影音。影音页是内置壳（CPlay / C听）的入口，
 * 不是用户装的播放器列表 —— 界面上永远只有「影视」「音乐」四个字。
 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 2001;
    private static final int REQ_NOTIFICATION = 2002;

    private ImageView ivWallpaper;
    private ImageView ivStatusDown;
    private ImageView ivStatusUp;
    private View bgTop;
    private View bgBottom;
    private View dock;
    private TextView tvClock;
    private TextView tvDate;
    private TextView tvWeather;
    private TextView tvBadge;
    private Button btnPalette;
    private Button btnLang;
    private Button btnTransfer;
    private Button btnDonate;
    private GridView gridApps;
    private AppAdapter adapter;
    private MediaAdapter mediaAdapter;
    private Button btnPagePrev;
    private Button btnPageNext;
    private TextView tvPageTitle;
    private TextView tvPageDots;
    private TextView tvPageHint;

    private static final int PAGE_COUNT = 3;
    // 页序与首页重设计 v2 对齐：最近 / 应用 / 影音，默认落在「最近」
    private static final int PAGE_RECENT = 0;
    private static final int PAGE_APPS = 1;
    private static final int PAGE_MEDIA = 2;
    private static final String PREFS_RECENT = "cdesk_recent";
    private static final String KEY_RECENT = "pkg_list";
    private static final int RECENT_MAX = 12;
    /** 应用网格列数：DPAD 边界判断和 setNumColumns 必须用同一个值，否则右键翻页位置会错 */
    private static final int APP_COLS = 6;
    /** 实际生效的应用列数：fitContentLayout() 按屏宽算，小屏 3 列 / 车机大屏 4~6 列 */
    private int appCols = APP_COLS;
    private int page = PAGE_RECENT;
    private int lastLang = -99;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private int curBgTop, curBgBottom;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            ui.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CLog.init(this);
        CLog.i("onCreate start, sdk=" + android.os.Build.VERSION.SDK_INT);
        PerfDiag.markStart(this);
        // 崩溃 handler 已移到 App（进程入口），这里不再重复安装
        Screen.apply(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        // 按屏宽自适应：右侧留白 + 应用列数，小屏不再把应用挤没
        fitContentLayout();
        // 切语言会把 Activity 重建一遍，之前每次都掉回「最近」那一页。
        // 记住上次停在哪一页，重建后回到原处。
        page = getSharedPreferences("cdesk_core", MODE_PRIVATE).getInt("page_last", PAGE_RECENT);
        if (page < 0 || page >= PAGE_COUNT) { page = PAGE_RECENT; }

        ivWallpaper = (ImageView) findViewById(R.id.ivWallpaper);
        ivStatusDown = (ImageView) findViewById(R.id.ivStatusDown);
        ivStatusUp = (ImageView) findViewById(R.id.ivStatusUp);
        if (ivStatusDown != null) ivStatusDown.setVisibility(View.GONE);
        if (ivStatusUp != null) ivStatusUp.setVisibility(View.GONE);
        TextView tvVersion = (TextView) findViewById(R.id.tvVersion);
        if (tvVersion != null) {
            try {
                tvVersion.setText("b" + getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName);
            } catch (Exception ignored) { }
        }
        bgTop = findViewById(R.id.bgTop);
        bgBottom = findViewById(R.id.bgBottom);
        dock = findViewById(R.id.dock);
        tvClock = (TextView) findViewById(R.id.tvClock);
        tvDate = (TextView) findViewById(R.id.tvDate);
        tvWeather = (TextView) findViewById(R.id.tvWeather);
        tvBadge = (TextView) findViewById(R.id.tvBadge);
        TextView tvTicker = (TextView) findViewById(R.id.tvTicker);
        if (tvTicker != null) {
            tvTicker.setSelected(true);   // 跑马灯必须 selected 才动
            tvTicker.setFocusable(false);
            tvTicker.setFocusableInTouchMode(false);
        }
        btnPalette = (Button) findViewById(R.id.btnPalette);
        btnLang = (Button) findViewById(R.id.btnLang);
        btnTransfer = (Button) findViewById(R.id.btnTransfer);
        btnDonate = (Button) findViewById(R.id.btnDonate);
        gridApps = (GridView) findViewById(R.id.gridApps);

        // 迷你播放条：有音乐/电台在播时显示，切页不断
        wireMiniPlaybar();

        // 更新提醒：每天最多查一次，静默失败
        UpdateChecker.checkIfNeeded(this, new UpdateChecker.Callback() {
            @Override public void onLatest(String v) { }
            @Override public void onUpdate(String ver, String url) {
                if (tvBadge != null) {
                    tvBadge.setText(getString(R.string.update_found, ver));
                    tvBadge.setTextColor(ThemeManager.ACCENT2);
                    tvBadge.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (Exception ignored) { }
                        }
                    });
                }
            }

            @Override public void onForceUpdate(String ver, String url) {
                // 服务端 version.json 标了 "force":true：这次必须更新，对话框关不掉
                if (tvBadge != null) {
                    tvBadge.setText(getString(R.string.update_found, ver));
                    tvBadge.setTextColor(ThemeManager.ACCENT2);
                }
                CLog.i("force update required: " + ver);
                UpdatePrompt.show(MainActivity.this, ver, url, true);
            }
        });

        // 启动时确认这个包是不是我们发的：别人重签过的话签名对不上
        if (!SelfCheck.isOfficial(this)) {
            // 日志会显示在日志页上，一律用英文：中文会被「英文界面不许出现中文」的测试抓出来
            CLog.e("unofficial signature: package was modified or re-signed");
        }

        btnPagePrev = (Button) findViewById(R.id.btnPagePrev);
        btnPageNext = (Button) findViewById(R.id.btnPageNext);
        tvPageTitle = (TextView) findViewById(R.id.tvPageTitle);
        tvPageDots = (TextView) findViewById(R.id.tvPageDots);
        tvPageHint = (TextView) findViewById(R.id.tvPageHint);

        adapter = new AppAdapter(this);
        mediaAdapter = new MediaAdapter(this);
        mediaAdapter.setOnCardClick(new MediaAdapter.OnCardClick() {
            @Override public void onCardClick(int pos, String type) {
                Class<?> t = "settings".equals(type) ? SettingsActivity.class
                        : "live".equals(type) ? LiveHomeActivity.class
                        : "audio".equals(type) ? MusicHomeActivity.class
                        : "library".equals(type) ? LibraryActivity.class
                        : VodHomeActivity.class;
                CLog.i("open shell " + t.getSimpleName());
                startActivity(new Intent(MainActivity.this, t));
            }
        });
        gridApps.setAdapter(adapter);
        // 触摸点击走 item 自己的监听：item 是 focusable，GridView.onItemClick 在触摸下收不到
        adapter.setOnCardClick(new AppAdapter.OnCardClick() {
            @Override public void onAppClick(int position) {
                if (page == PAGE_APPS) launchApp(adapter.getItem(position));
            }
        });

        ThemeManager.Palette p = ThemeManager.current(this);
        curBgTop = p.bgTop;
        curBgBottom = p.bgBottom;
        applyPalette(p, false);

        btnPalette.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleWallpaper(); }
        });
        btnPalette.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                startActivity(new Intent(MainActivity.this, WallpaperSettingsActivity.class));
                return true;
            }
        });
        btnLang.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LangManager.cycle(MainActivity.this); }
        });
        Button btnSettings = (Button) findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                }
            });
        }
        btnTransfer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openTransfer(); }
        });
        btnDonate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DonateActivity.class));
            }
        });
        gridApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (page == PAGE_MEDIA) {
                    String type = mediaAdapter.typeAt(position);
                    CLog.i("media click type=" + type + " pos=" + position);
                    Class<?> target = "settings".equals(type) ? SettingsActivity.class
                            : "live".equals(type) ? LiveHomeActivity.class
                            : "audio".equals(type) ? MusicHomeActivity.class
                            : "library".equals(type) ? LibraryActivity.class
                            : VodHomeActivity.class;
                    startActivity(new Intent(MainActivity.this, target));
                } else {
                    launchApp(adapter.getItem(position));
                }
            }
        });

        btnPagePrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(page - 1); }
        });
        btnPageNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(page + 1); }
        });
        btnPagePrev.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                switchPage(PAGE_COUNT - 1);
                return true;
            }
        });

        // 遥控器 / 方向盘：在网格边缘按左右键翻页，↓ 到底进 Dock，↑ 在 Dock 回网格
        gridApps.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                boolean media = page == PAGE_MEDIA;
                int cols = media ? mediaAdapter.columns() : appCols;
                int count = media ? mediaAdapter.getCount() : adapter.getCount();
                int pos = gridApps.getSelectedItemPosition();
                if (pos < 0) pos = 0;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && (count == 0 || (pos % cols) == 0)) { switchPage(page - 1); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && (count == 0 || (pos % cols) == cols - 1 || pos == count - 1)) { switchPage(page + 1); return true; }
                // 网格按 ↓：已在最后一行（或空页）→ 焦点交给 Dock 第一个按钮
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int rows = (count + cols - 1) / cols;
                    int curRow = count == 0 ? -1 : pos / cols;
                    if (count == 0 || curRow >= rows - 1) {
                        Button first = (Button) findViewById(R.id.btnPalette);
                        if (first != null) { first.requestFocus(); return true; }
                    }
                }
                return false;
            }
        });

        ui.post(clockTick);

        // 首启零打断：引导/须知全删（入口在设置页），悬浮窗权限只在第一次问一次
        if (!getSharedPreferences("cdesk_core", MODE_PRIVATE).getBoolean("overlay_asked", false)) {
            getSharedPreferences("cdesk_core", MODE_PRIVATE).edit().putBoolean("overlay_asked", true).commit();
            ensureOverlayPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PerfDiag.recordStartup(this);
        fitContentLayout();
        if (adapter != null) {
            adapter.reload();
            applyPage(page);
        }
        // 遥控器开机第一下就该能走图标：焦点必须落在网格上，不能落在左上角按钮
        gridApps.post(new Runnable() {
            @Override public void run() {
                gridApps.setSelection(0);
                gridApps.requestFocus();
            }
        });
        ensureNotificationPermission();
        startSpeedService();
        // 角标状态恢复：任何页面写过的状态在这里落回桌面
        android.content.SharedPreferences sp = getSharedPreferences("cdesk_status", 0);
        if (ivStatusDown != null) ivStatusDown.setVisibility(sp.getInt("down", 0) == 1 ? View.VISIBLE : View.GONE);
        if (ivStatusUp != null) ivStatusUp.setVisibility(sp.getInt("up", 0) == 1 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(clockTick);
        super.onDestroy();
    }

    /**
     * 触摸/遥控双模的收口：
     * 手指抬起来之后，Android 会进入"触摸模式"，此时 GridView 上的焦点被系统清空，
     * 遥控器下一方向盘键可能落到任意控件。这里在每次触摸结束后把焦点拉回主网格，
     * 保证"摸完一下，遥控器马上就能接着用"。
     * 兼容性：dispatchTouchEvent 是 Activity API 1+ 方法 ✓
     */
    /** 左下角下载角标：true=显示（下载进行中）。静态方法，任何页面都能开关桌面角标 */
    public static void showDownloadBadge(android.content.Context c, boolean on) {
        android.content.SharedPreferences p = c.getSharedPreferences("cdesk_status", 0);
        p.edit().putInt("down", on ? 1 : 0).commit();
    }

    /** 右下角上传角标：true=显示（上传进行中） */
    public static void showUploadBadge(android.content.Context c, boolean on) {
        android.content.SharedPreferences p = c.getSharedPreferences("cdesk_status", 0);
        p.edit().putInt("up", on ? 1 : 0).commit();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = super.dispatchTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // 只在主桌面（非弹窗/子页）时收焦点；网格已在焦中则不动
            if (gridApps != null && page == PAGE_APPS && !gridApps.hasFocus()) {
                gridApps.post(new Runnable() {
                    @Override public void run() {
                        if (gridApps != null && !gridApps.hasFocus()) {
                            gridApps.setSelection(gridApps.getSelectedItemPosition() < 0 ? 0 : gridApps.getSelectedItemPosition());
                            gridApps.requestFocus();
                        }
                    }
                });
            }
        }
        return handled;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_H) {
            WPManager.setAuto(this, false);
            WPManager.setWhich(this, (WPManager.which(this) + 1) % 6);
            applyPalette(ThemeManager.current(this), false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ---------- 分页 ----------

    private void switchPage(int target) {
        int next = ((target % PAGE_COUNT) + PAGE_COUNT) % PAGE_COUNT;
        if (next == page) return;
        page = next;
        CLog.i("switchPage -> " + page);
        applyPage(page);
        gridApps.setSelection(0);
        gridApps.requestFocus();
    }

    /** 按当前屏宽自适应布局：
     *  - 右侧壁纸人物留白从固定 300dp 改为屏宽的 26%（小屏不再把应用挤没）；
     *  - 应用网格列数随剩余宽度算 3~6 列。车机/电视屏宽够大，自动回到 5~6 列。 */
    private void fitContentLayout() {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int wPx = dm.widthPixels;
            int reservePx = (int) (wPx * 0.26f);
            View cm = findViewById(R.id.contentMain);
            if (cm != null) {
                cm.setPadding(cm.getPaddingLeft(), cm.getPaddingTop(), reservePx, cm.getPaddingBottom());
            }
            int usablePx = wPx - (cm != null ? cm.getPaddingLeft() : 0) - reservePx;
            int usableDp = (int) (usablePx / dm.density);
            int cols = (int) Math.round(usableDp / 104.0);
            if (cols < 3) cols = 3;
            if (cols > 6) cols = 6;
            appCols = cols;
        } catch (Throwable ignored) { }
    }

    /** 按当前页号切数据源（应用页/最近页共用 adapter，影音页用大卡片） */
    private void applyPage(int p) {
        page = ((p % PAGE_COUNT) + PAGE_COUNT) % PAGE_COUNT;
        boolean media = page == PAGE_MEDIA;
        gridApps.setAdapter(media ? mediaAdapter : adapter);
        gridApps.setNumColumns(media ? mediaAdapter.columns() : appCols);
        // 影音页右侧留给壁纸人物：4 张卡片只占左边四分之三
        int gapRight = media ? (int) (getResources().getDisplayMetrics().widthPixels * 0.26f) : 0;
        gridApps.setPadding(0, gridApps.getPaddingTop(), gapRight, gridApps.getPaddingBottom());
        gridApps.setSelection(0);

        List<AppAdapter.AppItem> all = adapter.allItems();
        List<AppAdapter.AppItem> list = new ArrayList<AppAdapter.AppItem>();
        if (page == PAGE_APPS) {
            list.addAll(all);
        } else if (page == PAGE_RECENT) {
            list.addAll(recentItems(all));
        }
        adapter.setItems(list);

        updatePageBar();

        if (!media && list.isEmpty()) {
            tvPageHint.setVisibility(View.VISIBLE);
            tvPageHint.setText(page == PAGE_RECENT
                    ? R.string.page_recent_empty
                    : R.string.page_apps_empty);
        } else {
            tvPageHint.setVisibility(View.GONE);
        }
    }

    private void updatePageBar() {
        String title = page == PAGE_APPS ? getString(R.string.page_apps)
                : page == PAGE_RECENT ? getString(R.string.page_recent)
                : getString(R.string.page_media);
        tvPageTitle.setText(title);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < PAGE_COUNT; i++) {
            if (i > 0) dots.append("  ");
            dots.append(i == page ? "\u25CF" : "\u25CB");
        }
        tvPageDots.setText(dots.toString());
        tvPageDots.setTextColor(ThemeManager.current(this).accent);
    }

    /** 最近打开过的应用，最近的在最前 */
    private List<AppAdapter.AppItem> recentItems(List<AppAdapter.AppItem> all) {
        List<AppAdapter.AppItem> out = new ArrayList<AppAdapter.AppItem>();
        String raw = getSharedPreferences(PREFS_RECENT, MODE_PRIVATE).getString(KEY_RECENT, "");
        if (raw == null || raw.length() == 0) return out;
        String[] pkgs = raw.split(",");
        for (String pkg : pkgs) {
            if (pkg == null || pkg.length() == 0) continue;
            for (AppAdapter.AppItem it : all) {
                if (it.pkg.equals(pkg)) { out.add(it); break; }
            }
        }
        return out;
    }

    private void pushRecent(String pkg) {
        if (pkg == null || pkg.length() == 0) return;
        String raw = getSharedPreferences(PREFS_RECENT, MODE_PRIVATE).getString(KEY_RECENT, "");
        List<String> list = new ArrayList<String>();
        if (raw != null && raw.length() > 0) {
        for (String x : raw.split(",")) {
                if (x != null && x.length() > 0 && !x.equals(pkg)) list.add(x);
            }
        }
        list.add(0, pkg);
        while (list.size() > RECENT_MAX) list.remove(list.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        getSharedPreferences(PREFS_RECENT, MODE_PRIVATE).edit().putString(KEY_RECENT, sb.toString()).commit();
    }

    private void updateClock() {
        Date now = new Date();
        tvClock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        tvDate.setText(new SimpleDateFormat("yyyy-MM-dd  EEEE", Locale.getDefault()).format(now));
    }

    /** 点换肤键：循环 晨光 -> 纯色 -> 鲸夜 -> 傍晚 -> 薄荷 -> 自定义 -> 晨光。手动选定后关闭日夜自动 */
    private void cycleWallpaper() {
        int next = (WPManager.which(this) + 1) % 6;
        if (next == WPManager.WP_CUSTOM && WPManager.customPath(this).length() == 0) next = WPManager.WP_MORNING;
        WPManager.setAuto(this, false);
        WPManager.setWhich(this, next);
        applyPalette(ThemeManager.current(this), false);
        int nameRes = next == WPManager.WP_MORNING ? R.string.wp_morning
                : next == WPManager.WP_NIGHT ? R.string.wp_night
                : next == WPManager.WP_DUSK ? R.string.wp_dusk
                : next == WPManager.WP_MINT ? R.string.wp_mint
                : next == WPManager.WP_CUSTOM ? R.string.wp_custom : R.string.wp_none;
        Toast.makeText(this, nameRes, Toast.LENGTH_SHORT).show();
    }



    /** 把整套配色刷到所有界面元素（含 Dock、天气行、按钮） */
    private void applyPalette(ThemeManager.Palette p, boolean animate) {
        boolean hasWall = WPManager.apply(this, ivWallpaper, bgTop, bgBottom);
        if (hasWall) {
            // 有壁纸：文字按壁纸明暗反色，跳过主题渐变动画
            int wp = WPManager.effective(this);
            boolean dark = WPManager.isDarkWallpaper(wp);
            // 图标名颜色跟随壁纸明暗（海盐灰在奶油底上看不清）
            adapter.setLightBg(!dark);
            int big = dark ? 0xFFF2F3F8 : 0xFF232B3A;
            int dim = dark ? 0xFFB9BECB : 0xFF5A6474;
            tvClock.setTextColor(big);
            tvDate.setTextColor(dim);
            if (tvWeather != null) tvWeather.setTextColor(dim);
            tvBadge.setTextColor(dark ? 0xFFD9C2FF : 0xFF5A3FB0);
            int[] ids = new int[] { R.id.btnPalette, R.id.btnLang, R.id.btnDonate, R.id.btnTransfer };
            for (int id : ids) {
                Button b = (Button) findViewById(id);
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(dp(999));
                gd.setColor(dark ? 0x33000000 : 0x26FFFFFF);
                gd.setStroke(dp(1), dark ? 0x66FFFFFF : 0x59232B3A);
                b.setBackgroundDrawable(gd);
                b.setTextColor(dark ? 0xFFF2F3F8 : 0xFF232B3A);
            }
            GradientDrawable dg = new GradientDrawable();
            dg.setCornerRadius(dp(22));
            dg.setColor(dark ? 0x40000000 : 0x33FFFFFF);
            dg.setStroke(dp(1), dark ? 0x59FFFFFF : 0x4D232B3A);
            dock.setBackgroundDrawable(dg);
            btnLang.setText(LangManager.label(this));
            lastLang = LangManager.index(this);
            updatePageBar();
            return;
        }
        int oldTop = curBgTop;
        int oldBottom = curBgBottom;
        curBgTop = p.bgTop;
        curBgBottom = p.bgBottom;
        if (animate) {
            ThemeManager.animateTextColor(tvClock, tvClock.getCurrentTextColor(), p.isLight ? p.fg : p.accent);
            ThemeManager.animateBackground(bgTop, bgBottom, oldTop, oldBottom, p.bgTop, p.bgBottom);
        } else {
            bgTop.setBackgroundColor(p.bgTop);
            bgBottom.setBackgroundColor(p.bgBottom);
            tvClock.setTextColor(p.isLight ? p.fg : p.accent);
        }
        // Dock:统一"玻璃"底座
        GradientDrawable dg = new GradientDrawable();
        dg.setCornerRadius(dp(22));
        dg.setColor(p.isLight ? 0x0D000000 : 0x14FFFFFF);
        dg.setStroke(dp(1), p.isLight ? 0x14000000 : 0x22FFFFFF);
        dock.setBackgroundDrawable(dg);

        tvDate.setTextColor(p.dim);
        if (tvWeather != null) {
            tvWeather.setTextColor(p.dim);
            tvWeather.setText("");
            WeatherManager.fetch(this, new WeatherManager.Callback() {
                @Override public void onResult(final String line) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            if (tvWeather == null) return;
                            tvWeather.setText(line == null ? "" : line);
                        }
                    });
                }
            });
            // 点天气 → 手动城市（IP 不准时的后路）
            tvWeather.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showWeatherCityDialog(); }
            });
        }
        tvBadge.setText(p.isLight ? R.string.theme_light : R.string.theme_dark);
        tvBadge.setTextColor(p.accent);
        int[] ids = new int[] { R.id.btnPalette, R.id.btnLang, R.id.btnDonate, R.id.btnTransfer };
        for (int id : ids) {
            ThemeManager.styleButton((Button) findViewById(id), p);
        }
        btnLang.setText(LangManager.label(this));
        lastLang = LangManager.index(this);
    }

    /** 启动被点中的第三方应用；失败就提示，不让桌面卡住 */
    /** 手动城市：城市名 + 纬度 + 经度，三行输入。留空城市名 = 回到自动 */
    private void showWeatherCityDialog() {
        final android.widget.EditText etCity = new android.widget.EditText(this);
        etCity.setHint(R.string.weather_hint_city);
        if (WeatherManager.manualCityName(this) != null) etCity.setText(WeatherManager.manualCityName(this));
        final android.widget.EditText etLat = new android.widget.EditText(this);
        etLat.setHint("lat (22.55)");
        final android.widget.EditText etLon = new android.widget.EditText(this);
        etLon.setHint("lon (114.05)");
        if (WeatherManager.manualLatLng(this) != null) {
            double[] ll = WeatherManager.manualLatLng(this);
            etLat.setText(String.valueOf(ll[0]));
            etLon.setText(String.valueOf(ll[1]));
        }
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, 0);
        etCity.setSingleLine(true);
        etLat.setSingleLine(true);
        etLon.setSingleLine(true);
        box.addView(etCity);
        box.addView(etLat);
        box.addView(etLon);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.weather_manual)
                .setView(box)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String city = etCity.getText() == null ? "" : etCity.getText().toString().trim();
                        if (city.length() == 0) {
                            WeatherManager.setAutoCity(MainActivity.this);
                            WeatherManager.fetch(MainActivity.this, weatherRefresh());
                            return;
                        }
                        try {
                            double lat = Double.parseDouble(etLat.getText().toString().trim());
                            double lon = Double.parseDouble(etLon.getText().toString().trim());
                            WeatherManager.setManualCity(MainActivity.this, city, lat, lon);
                            WeatherManager.fetch(MainActivity.this, weatherRefresh());
                        } catch (NumberFormatException e) {
                            android.widget.Toast.makeText(MainActivity.this,
                                    R.string.shell_failed, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton(R.string.weather_auto, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        WeatherManager.setAutoCity(MainActivity.this);
                        WeatherManager.fetch(MainActivity.this, weatherRefresh());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private WeatherManager.Callback weatherRefresh() {
        return new WeatherManager.Callback() {
            @Override public void onResult(final String line) {
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || tvWeather == null) return;
                        tvWeather.setText(line == null ? "" : line);
                    }
                });
            }
        };
    }

    /**
     * 启动应用。
     *
     * 旧实现把 doLaunch 挂在 ViewPropertyAnimator 的 withEndAction 上，
     * 只有那 160ms 动画正常收尾才会真正启动 —— 窗口失焦、系统关掉动画、
     * view 被回收，回调就不会来，图标点了毫无反应（作为桌面时最容易复现）。
     * 现在改成：先启动，动效只做视觉反馈，不影响启动。
     */
    private void launchApp(final AppAdapter.AppItem it) {
        if (it == null) return;
        doLaunch(it);
        try {
            View focused = getCurrentFocus();
            if (focused != null) {
                focused.animate().scaleX(0.94f).scaleY(0.94f).setDuration(60).start();
            }
        } catch (Throwable ignored) { }
    }

    /**
     * 三级兜底启动，任何一级成功就返回：
     *   1. 显式 ComponentName（最快最准）
     *   2. PackageManager 给的启动 Intent（适配包可见性过滤）
     *   3. 隐式 ACTION_MAIN + CATEGORY_LAUNCHER + setPackage
     * 全失败才提示，并把原因一起显示出来，便于截图定位。
     */
    private void doLaunch(AppAdapter.AppItem it) {
        if (it == null) return;

        // 点自己：唤出系统「选择桌面」窗口，而不是再启动一次
        if (getPackageName().equals(it.pkg)) {
            openHomeChooser();
            return;
        }

        Throwable last = null;

        // 1) 显式组件
        if (it.cls != null) {
            try {
                ComponentName cn = new ComponentName(it.pkg, it.cls);
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setComponent(cn);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
                CLog.i("launch ok(component) " + it.pkg + "/" + it.cls);
                pushRecent(it.pkg);
                return;
            } catch (Throwable t) {
                last = t;
                CLog.e("launch component failed " + it.pkg, t);
            }
        }

        // 2) 系统启动 Intent
        try {
            Intent li = getPackageManager().getLaunchIntentForPackage(it.pkg);
            if (li != null) {
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(li);
                CLog.i("launch ok(intent) " + it.pkg);
                pushRecent(it.pkg);
                return;
            }
        } catch (Throwable t) {
            last = t;
            CLog.e("launch intent failed " + it.pkg, t);
        }

        // 3) 隐式
        try {
            Intent im = new Intent(Intent.ACTION_MAIN);
            im.addCategory(Intent.CATEGORY_LAUNCHER);
            im.setPackage(it.pkg);
            im.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(im);
            CLog.i("launch ok(implicit) " + it.pkg);
            pushRecent(it.pkg);
            return;
        } catch (Throwable t) {
            last = t;
            CLog.e("launch implicit failed " + it.pkg, t);
        }

        String why = last == null ? "" : last.getClass().getSimpleName()
                + (last.getMessage() == null ? "" : ": " + last.getMessage());
        if (why.length() > 90) why = why.substring(0, 90);
        Toast.makeText(this, it.label + "  " + getString(R.string.launch_failed)
                + (why.length() > 0 ? "\n" + why : ""), Toast.LENGTH_LONG).show();
    }

    /** 桌面不该被返回键退出 */
    @Override
    public void onBackPressed() {
    }

    /** 唤出系统桌面选择器；设不了默认时退回引导页 */
    private void openHomeChooser() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            Toast.makeText(this, R.string.choose_home_hint, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            try {
                startActivity(new Intent(this, SetupActivity.class));
            } catch (Throwable ignored) { }
        }
    }

    private void openTransfer() {
        Intent i = new Intent(this, TransferActivity.class);
        startActivity(i);
    }

    private void ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
            } catch (Throwable ignored) { }
        }
    }

    private void ensureNotificationPermission() {
        // Android 13+ 通知需运行时授权；前台服务通知本身豁免，此处为面向未来普通通知补全
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences sp = getSharedPreferences("cdesk_core", MODE_PRIVATE);
        if (sp.getBoolean("notif_asked", false)) return;
        sp.edit().putBoolean("notif_asked", true).commit();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION) {
            // 授权与否都不阻断：前台服务通知豁免，更新弹窗走 AlertDialog/Toast 不受影响
            startSpeedService();
        }
    }

    private void startSpeedService() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        Intent svc = new Intent(this, SpeedService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Throwable t) {
            // 厂商 ROM（澎湃/Flyme/ColorOS 等）常限制后台服务启动，静默失败会让用户误以为功能坏了
            Toast.makeText(this, R.string.guardian_service_failed, Toast.LENGTH_LONG).show();
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------- 迷你播放条：音乐/电台在桌面继续播 ----------
    private void wireMiniPlaybar() {
        final View bar = findViewById(R.id.miniPlaybar);
        if (bar == null) return;
        final TextView tvTitle = (TextView) findViewById(R.id.tvMiniTitle);
        final TextView btnPause = (TextView) findViewById(R.id.btnMiniPause);
        final TextView btnStop = (TextView) findViewById(R.id.btnMiniStop);

        MusicKernel.get(this).addListener(new MusicKernel.Listener() {
            @Override public void onStateChanged(boolean playing, String t, String u, boolean isLive) {
                if (isFinishing()) return;
                if (tvTitle == null || btnPause == null || btnStop == null) return;
                if (t == null || t.length() == 0 || u == null || u.length() == 0) {
                    bar.setVisibility(View.GONE);
                    return;
                }
                bar.setVisibility(View.VISIBLE);
                tvTitle.setText(t);
                btnPause.setText(playing ? "\u2759\u2759" : "\u25B6");
            }
        });
        if (btnPause != null) btnPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { MusicKernel.get(MainActivity.this).toggle(); }
        });
        if (btnStop != null) btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { MusicKernel.get(MainActivity.this).stop(); }
        });
    }

    /** 记住当前页号，切语言/重建后回到这一页 */
    private void savePage() {
        try {
            getSharedPreferences("cdesk_core", MODE_PRIVATE).edit().putInt("page_last", page).commit();
        } catch (Throwable ignored) {
        }
    }
}
