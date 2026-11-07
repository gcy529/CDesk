package com.c.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
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

    private ImageView ivWallpaper;
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
    private static final int PAGE_APPS = 0;
    private static final int PAGE_RECENT = 1;
    private static final int PAGE_MEDIA = 2;
    private static final String PREFS_RECENT = "cdesk_recent";
    private static final String KEY_RECENT = "pkg_list";
    private static final int RECENT_MAX = 12;
    private int page = PAGE_APPS;
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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        ivWallpaper = (ImageView) findViewById(R.id.ivWallpaper);
        bgTop = findViewById(R.id.bgTop);
        bgBottom = findViewById(R.id.bgBottom);
        dock = findViewById(R.id.dock);
        tvClock = (TextView) findViewById(R.id.tvClock);
        tvDate = (TextView) findViewById(R.id.tvDate);
        tvWeather = (TextView) findViewById(R.id.tvWeather);
        tvBadge = (TextView) findViewById(R.id.tvBadge);
        btnPalette = (Button) findViewById(R.id.btnPalette);
        btnLang = (Button) findViewById(R.id.btnLang);
        btnTransfer = (Button) findViewById(R.id.btnTransfer);
        btnDonate = (Button) findViewById(R.id.btnDonate);
        gridApps = (GridView) findViewById(R.id.gridApps);
        btnPagePrev = (Button) findViewById(R.id.btnPagePrev);
        btnPageNext = (Button) findViewById(R.id.btnPageNext);
        tvPageTitle = (TextView) findViewById(R.id.tvPageTitle);
        tvPageDots = (TextView) findViewById(R.id.tvPageDots);
        tvPageHint = (TextView) findViewById(R.id.tvPageHint);

        adapter = new AppAdapter(this);
        mediaAdapter = new MediaAdapter(this);
        gridApps.setAdapter(adapter);

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
        tvBadge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SetupActivity.class));
            }
        });
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
                    Intent i = new Intent(MainActivity.this, ShellPlaceholderActivity.class);
                    i.putExtra("type", type);
                    startActivity(i);
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

        // 遥控器 / 方向盘：在网格边缘按左右键翻页
        gridApps.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (page == PAGE_MEDIA) {
                    int pos = gridApps.getSelectedItemPosition();
                    if (pos < 0) pos = 0;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && (pos % 2) == 0) { switchPage(page - 1); return true; }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && (pos % 2) == 1) { switchPage(page + 1); return true; }
                    return false;
                }
                int count = adapter.getCount();
                int pos2 = gridApps.getSelectedItemPosition();
                if (pos2 < 0) pos2 = 0;
                if (count == 0) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { switchPage(page - 1); return true; }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { switchPage(page + 1); return true; }
                    return false;
                }
                boolean firstCol = (pos2 % 6) == 0;
                boolean lastCol = (pos2 % 6) == 5 || pos2 == count - 1;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && firstCol) { switchPage(page - 1); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && lastCol) { switchPage(page + 1); return true; }
                return false;
            }
        });

        ui.post(clockTick);

        // 首次启动先弹使用须知（只一次），再进引导页
        if (!DisclaimerActivity.agreed(this)) {
            startActivity(new Intent(this, DisclaimerActivity.class));
        }

        // 首次启动先进引导页：普通应用无法用代码把自己设成默认桌面，
        // 只能把操作步骤讲清楚，所以这一步不能省。
        if (!ThemeManager.isSetupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        }

        ensureOverlayPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        startSpeedService();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(clockTick);
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_H) {
            WPManager.setAuto(this, false);
            WPManager.setWhich(this, (WPManager.which(this) + 1) % 4);
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
        applyPage(page);
        gridApps.setSelection(0);
        gridApps.requestFocus();
    }

    /** 按当前页号切数据源（应用页/最近页共用 adapter，影音页用大卡片） */
    private void applyPage(int p) {
        page = ((p % PAGE_COUNT) + PAGE_COUNT) % PAGE_COUNT;
        boolean media = page == PAGE_MEDIA;
        gridApps.setAdapter(media ? mediaAdapter : adapter);
        gridApps.setNumColumns(media ? 2 : 6);
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
                    : R.string.page_media_empty);
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

    /** 点换肤键：循环 纯色 -> 鲸夜 -> 晨光 -> 金色时刻 -> 纯色。手动选定后关闭日夜自动 */
    private void cycleWallpaper() {
        int next = (WPManager.which(this) + 1) % 4;
        WPManager.setAuto(this, false);
        WPManager.setWhich(this, next);
        applyPalette(ThemeManager.current(this), false);
        int nameRes = next == WPManager.WP_NIGHT ? R.string.wp_night
                : next == WPManager.WP_DAY ? R.string.wp_day
                : next == WPManager.WP_SUNSET ? R.string.wp_sunset : R.string.wp_none;
        Toast.makeText(this, nameRes, Toast.LENGTH_SHORT).show();
    }

    private void nextPalette() {
        ThemeManager.Palette p = ThemeManager.next(this);
        applyPalette(p, true);
        if (SpeedService.overlay() != null) SpeedService.overlay().applyTheme();
        Toast.makeText(this, p.name, Toast.LENGTH_SHORT).show();
    }

    /** 把整套配色刷到所有界面元素（含 Dock、天气行、按钮） */
    private void applyPalette(ThemeManager.Palette p, boolean animate) {
        boolean hasWall = WPManager.apply(this, ivWallpaper, bgTop, bgBottom);
        if (hasWall) {
            // 有壁纸：文字按壁纸明暗反色，跳过主题渐变动画
            int wp = WPManager.effective(this);
            boolean dark = WPManager.isDarkWallpaper(wp);
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
        }
        tvBadge.setText(p.name);
        tvBadge.setTextColor(p.accent);
        int[] ids = new int[] { R.id.btnPalette, R.id.btnLang, R.id.btnDonate, R.id.btnTransfer };
        for (int id : ids) {
            ThemeManager.styleButton((Button) findViewById(id), p);
        }
        btnLang.setText(LangManager.label(this));
        lastLang = LangManager.index(this);
    }

    /** 启动被点中的第三方应用；失败就提示，不让桌面卡住 */
    private void launchApp(AppAdapter.AppItem it) {
        if (it == null) return;

        // 点自己：不是再启动一次，而是唤出系统的「选择桌面」窗口。
        // 系统没有默认桌面时会弹出"始终 / 仅此一次"，
        // 这是被固件锁死 Home 键的设备上唯一能设默认的入口。
        if (getPackageName().equals(it.pkg)) {
            openHomeChooser();
            return;
        }

        try {
            ComponentName cn = new ComponentName(it.pkg, it.cls);
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(cn);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
            pushRecent(it.pkg);
        } catch (Throwable t) {
            Toast.makeText(this, it.label + "  " + getString(R.string.launch_failed),
                    Toast.LENGTH_SHORT).show();
        }
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

    private void startSpeedService() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        Intent svc = new Intent(this, SpeedService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Throwable ignored) { }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
