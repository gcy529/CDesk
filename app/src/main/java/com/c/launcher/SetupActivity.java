package com.c.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 首次设置引导。
 *
 * 为什么需要这个页面：
 *  - 普通应用拿不到 SET_PREFERRED_APPLICATIONS（signature 级），
 *    所以「用代码直接把默认桌面设成自己」在所有 Android 版本上都做不到。
 *  - 唯一可行的路径是检测 + 引导用户自己操作，所以这个页面存在的意义
 *    就是把"用户自己操作"这件事说得足够清楚。
 *
 * 三个状态：
 *  1. 已经是默认桌面  -> 直接放行
 *  2. 不是，但系统有桌面设置页 -> 跳过去
 *  3. 不是，且固件锁死 -> 如实告知，不假装能解决
 */
public class SetupActivity extends Activity {

    private TextView tvState;
    private TextView tvHint;
    private TextView tvOverlayState;
    private Button btnOverlay;

    @Override
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        tvState = (TextView) findViewById(R.id.tvState);
        tvHint = (TextView) findViewById(R.id.tvHint);
        tvOverlayState = (TextView) findViewById(R.id.tvOverlayState);
        btnOverlay = (Button) findViewById(R.id.btnOverlay);

        TextView title = (TextView) findViewById(R.id.tvSetupTitle);
        if (title != null) title.setTextColor(ThemeManager.current(this).accent);

        findViewById(R.id.btnHomeSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openHomeSettings(); }
        });

        findViewById(R.id.btnRecheck).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });

        findViewById(R.id.btnEnter).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ThemeManager.markSetupDone(SetupActivity.this);
                finish();
            }
        });

        btnOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** 当前 App 是不是系统认可的默认桌面 */
    private boolean isDefaultLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return false;
            return getPackageName().equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    private void refresh() {
        boolean isDefault = isDefaultLauncher();
        if (tvState != null) {
            tvState.setText(isDefault ? R.string.setup_state_on : R.string.setup_state_off);
            tvState.setTextColor(isDefault ? 0xFF4ADE80 : 0xFFF87171);
        }
        if (tvHint != null) {
            if (isDefault) {
                tvHint.setText(R.string.setup_hint_already);
            } else if (canOpenHomeSettings()) {
                tvHint.setText(getString(R.string.setup_hint_press_home, getString(R.string.app_name)));
            } else {
                tvHint.setText(R.string.setup_hint_settings);
            }
        }
        refreshOverlay();
    }

    private boolean canOpenHomeSettings() {
        if (Build.VERSION.SDK_INT < 21) return false;
        try {
            Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
            return getPackageManager().resolveActivity(i, 0) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private void refreshOverlay() {
        if (tvOverlayState == null) return;
        if (Build.VERSION.SDK_INT < 23) {
            tvOverlayState.setText(R.string.setup_overlay_na);
            tvOverlayState.setTextColor(0xFF94A3B8);
            if (btnOverlay != null) btnOverlay.setVisibility(View.GONE);
            return;
        }
        boolean granted;
        try {
            granted = Settings.canDrawOverlays(this);
        } catch (Throwable t) {
            granted = false;
        }
        tvOverlayState.setText(granted ? R.string.setup_overlay_on : R.string.setup_overlay_off);
        tvOverlayState.setTextColor(granted ? 0xFF4ADE80 : 0xFFF87171);
        if (btnOverlay != null) btnOverlay.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void openHomeSettings() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
                if (getPackageManager().resolveActivity(i, 0) != null) intent = i;
            } catch (Throwable ignored) { }
        }
        if (intent == null) {
            try {
                intent = new Intent(Settings.ACTION_SETTINGS);
            } catch (Throwable ignored) { }
        }
        if (intent == null) {
            Toast.makeText(this, R.string.setup_settings_missing, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.setup_settings_missing, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) { }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.setup_settings_missing, Toast.LENGTH_LONG).show();
        }
    }
}
