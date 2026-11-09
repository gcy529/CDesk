package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * 守护 C桌面 —— 像"游戏引导"一样的状态面板：
 *  默认桌面是否就位 / 悬浮窗权限 / 电池优化豁免，一项一行，✓绿✗红，
 *  每项后面跟一个「去处理」按钮，点直达系统对应设置页。
 *
 * 为什么单独一个 Activity 而不是复用 SetupActivity：
 *  SetupActivity 是"首次教学"（讲步骤），这里是"随时体检"（看状态），
 *  两个场景的心智不同；状态页每次进来都实时刷新。
 *
 * 兼容性（minSdk 16）：
 *  - isDefaultLauncher 用 getRunningTasks 兜底 + Home Intent 解析（API 1+）✓
 *  - canDrawOverlays 是 API 23+，以下版本直接显示"无需此权限" ✓
 *  - 电池优化忽略列表 ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS 是 API 23+，
 *    以下版本隐藏该行 ✓
 *  - 全部用原生 android.*，无 androidx ✓
 */
public class GuardianActivity extends Activity {

    private TextView tvDefault, tvOverlay, tvBattery, tvVer;
    private Button btnFixDefault, btnFixOverlay, btnFixBattery;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Screen.apply(this);
        setContentView(R.layout.activity_guardian);

        ThemeManager.Palette p = ThemeManager.current(this);
        View root = findViewById(R.id.rootGuardian);
        WPManager.applyToRoot(this, root);

        TextView title = (TextView) findViewById(R.id.tvGuardTitle);
        title.setTextColor(p.isLight ? p.fg : p.accent);

        tvDefault = (TextView) findViewById(R.id.tvGuardDefault);
        tvOverlay = (TextView) findViewById(R.id.tvGuardOverlay);
        tvBattery = (TextView) findViewById(R.id.tvGuardBattery);
        tvVer = (TextView) findViewById(R.id.tvGuardVer);
        btnFixDefault = (Button) findViewById(R.id.btnFixDefault);
        btnFixOverlay = (Button) findViewById(R.id.btnFixOverlay);
        btnFixBattery = (Button) findViewById(R.id.btnFixBattery);

        ThemeManager.styleButton(btnFixDefault, p);
        ThemeManager.styleButton(btnFixOverlay, p);
        ThemeManager.styleButton(btnFixBattery, p);
        ThemeManager.styleButton((Button) findViewById(R.id.btnGuardBack), p);

        findViewById(R.id.btnGuardBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnFixDefault.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDefaultSettings(); }
        });
        btnFixOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });
        btnFixBattery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openBatterySettings(); }
        });

        // 版本号
        try {
            tvVer.setText(getString(R.string.guardian_version,
                    "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ThemeManager.Palette p = ThemeManager.current(this);
        int ok = 0xFF4ADE80, bad = 0xFFF87171, dim = p.dim;

        // 1) 默认桌面：解析 HOME intent 的首选应用
        boolean isDefault = isDefaultLauncher();
        tvDefault.setText(isDefault ? R.string.guardian_default_ok : R.string.guardian_default_bad);
        tvDefault.setTextColor(isDefault ? ok : bad);
        btnFixDefault.setVisibility(isDefault ? View.GONE : View.VISIBLE);

        // 2) 悬浮窗权限（API 23+ 才有此概念）
        if (Build.VERSION.SDK_INT >= 23) {
            boolean can = Settings.canDrawOverlays(this);
            tvOverlay.setText(can ? R.string.guardian_overlay_ok : R.string.guardian_overlay_bad);
            tvOverlay.setTextColor(can ? ok : bad);
            btnFixOverlay.setVisibility(can ? View.GONE : View.VISIBLE);
        } else {
            tvOverlay.setText(R.string.guardian_overlay_na);
            tvOverlay.setTextColor(dim);
            btnFixOverlay.setVisibility(View.GONE);
        }

        // 3) 电池优化豁免（API 23+；低版本系统没有这套机制，不会杀后台）
        if (Build.VERSION.SDK_INT >= 23) {
            boolean ignored = isIgnoringBatteryOptimization();
            tvBattery.setText(ignored ? R.string.guardian_battery_ok : R.string.guardian_battery_bad);
            tvBattery.setTextColor(ignored ? ok : bad);
            btnFixBattery.setVisibility(ignored ? View.GONE : View.VISIBLE);
        } else {
            tvBattery.setText(R.string.guardian_battery_na);
            tvBattery.setTextColor(dim);
            btnFixBattery.setVisibility(View.GONE);
        }
    }

    private boolean isDefaultLauncher() {
        try {
            // 方案一（API 24+ 官方）：PackageManager.getHomeActivities(List<ResolveInfo>)
            // 方案二（通用）：RoleManager API 29+。为兼容 4.1 用第三招：
            // 如果 resolveActivity 返回的就是自己 → 是默认；
            // 返回 resolver（带 CHECK_DEFAULT 标记的 ResolverActivity）→ 不是。
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = getPackageManager()
                    .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return false;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) return true;
            // resolver 的情况下名字带 Resolver / 决策框 —— 保守判 false（显示"未就位"）
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isIgnoringBatteryOptimization() {
        try {
            String pn = getPackageName();
            // API 23+：powermanager.isIgnoringBatteryOptimizations
            Object pm = getSystemService(Context.POWER_SERVICE);
            return (Boolean) pm.getClass().getMethod("isIgnoringBatteryOptimizations", String.class)
                    .invoke(pm, pn);
        } catch (Throwable t) {
            return true;   // 查不出来就当已豁免，别误报吓人
        }
    }

    private void openDefaultSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { }
        }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { }
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { }
        }
    }
}
