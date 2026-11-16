package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：卡片化布局，分桌面/系统/关于三组。
 * 稳定版 0.3 去掉播放器设置（无影音页）。
 */
public class SettingsActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        CLog.i("SettingsActivity onCreate");
        Screen.apply(this);
        setContentView(R.layout.activity_settings);

        ThemeManager.Palette p = ThemeManager.current(this);
        View root = findViewById(R.id.scrollSettings);
        WPManager.applyToRoot(this, root);

        TextView title = (TextView) findViewById(R.id.tvSetTitle);
        title.setTextColor(p.fg);
        TextView ver = (TextView) findViewById(R.id.tvSetVersion);
        ver.setText("v" + getString(R.string.app_version));
        ver.setTextColor(p.dim);

        final Button btnFilter = (Button) findViewById(R.id.btnSetFilter);
        Button btnWall = (Button) findViewById(R.id.btnSetWallpaper);
        final Button btnOrient = (Button) findViewById(R.id.btnSetOrient);
        Button btnLang = (Button) findViewById(R.id.btnSetLang);
        final Button btnDevice = (Button) findViewById(R.id.btnSetDevice);
        Button btnTransfer = (Button) findViewById(R.id.btnSetTransfer);
        Button btnUpdate = (Button) findViewById(R.id.btnSetUpdate);
        Button btnDisclaimer = (Button) findViewById(R.id.btnSetDisclaimer);
        Button btnCore = (Button) findViewById(R.id.btnSetCore);
        final Button btnLog = (Button) findViewById(R.id.btnSetLog);
        final Button btnCleanup = (Button) findViewById(R.id.btnSetCleanup);
        final Button btnBack = (Button) findViewById(R.id.btnSetBack);

        Button[] all = new Button[] { btnFilter, btnCore, btnWall, btnOrient, btnLang, btnDevice, btnTransfer, btnUpdate, btnDisclaimer, btnLog, btnCleanup, btnBack };
        for (int i = 0; i < all.length; i++) {
            if (all[i] != null) ThemeManager.styleButton(all[i], p);
        }

        btnFilter.setText(ContentFilter.enabled(this)
                ? R.string.settings_filter_on : R.string.settings_filter_off);
        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !ContentFilter.enabled(SettingsActivity.this);
                ContentFilter.setEnabled(SettingsActivity.this, next);
                btnFilter.setText(next
                        ? R.string.settings_filter_on : R.string.settings_filter_off);
            }
        });

        btnWall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, WallpaperSettingsActivity.class));
            }
        });

        // 桌面朝向：自动 / 横 / 竖 循环。默认横（车机天生横放），手动定过才切
        btnOrient.setText(orientLabel(Screen.desktopOrient(this)));
        btnOrient.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = Screen.desktopOrient(SettingsActivity.this);
                int next = cur == Screen.ORIENT_AUTO ? Screen.ORIENT_LANDSCAPE
                        : (cur == Screen.ORIENT_LANDSCAPE ? Screen.ORIENT_PORTRAIT : Screen.ORIENT_AUTO);
                Screen.setDesktopOrient(SettingsActivity.this, next);
                Screen.applyOrientation(SettingsActivity.this);
                btnOrient.setText(orientLabel(next));
                CLog.i("desktop orient -> " + next);
            }
        });

        btnLang.setText(LangManager.label(this));
        btnLang.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LangManager.cycle(SettingsActivity.this); }
        });

        // 设备类型：自动 / TV / 车机 / Pad / 手机 循环，手动覆盖优先生效
        btnDevice.setText(DeviceType.label(this, DeviceType.effective(this)));
        btnDevice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = DeviceType.getManual(SettingsActivity.this);
                int next;
                if (cur == DeviceType.AUTO) next = DeviceType.TV;
                else if (cur == DeviceType.TV) next = DeviceType.CAR;
                else if (cur == DeviceType.CAR) next = DeviceType.PAD;
                else if (cur == DeviceType.PAD) next = DeviceType.PHONE;
                else next = DeviceType.AUTO;
                DeviceType.setManual(SettingsActivity.this, next);
                btnDevice.setText(DeviceType.label(SettingsActivity.this,
                        DeviceType.effective(SettingsActivity.this)));
                CLog.i("device type manual -> " + next);
            }
        });

        btnTransfer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, TransferActivity.class));
            }
        });

        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(SettingsActivity.this, R.string.update_checking, Toast.LENGTH_SHORT).show();
                UpdateChecker.checkNow(SettingsActivity.this, new UpdateChecker.Callback() {
                    @Override public void onUpdate(String verName, String url, String notes) {
                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.update_found, verName), Toast.LENGTH_LONG).show();
                        // 车机上没有浏览器，跳浏览器等于更新不了：改成应用内下载 + 调安装器
                        UpdatePrompt.show(SettingsActivity.this, verName, url, notes, false);
                    }
                    @Override public void onLatest(String remoteVer) {
                        // 带上远端版本号：能弹出版本号 = 真的连上服务器了，不是网络失败被当成最新
                        String msg = getString(R.string.update_latest);
                        if (remoteVer != null && remoteVer.length() > 0) msg = msg + " (" + remoteVer + ")";
                        Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String reason) {
                        // 网络不通必须说清楚，不能继续冒充「已是最新」
                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.update_failed, reason), Toast.LENGTH_LONG).show();
                        CLog.i("update check failed: " + reason);
                    }
                });
            }
        });

        btnCore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, GuardianActivity.class));
            }
        });
        btnDisclaimer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, DisclaimerActivity.class));
            }
        });

        if (btnLog != null) {
            // 改成打开整页日志：能慢慢滚、能截图、能复制。
            // 原来的「复制 + 2 秒 Toast」根本来不及看。
            btnLog.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, LogViewActivity.class));
                }
            });
        }

        if (btnCleanup != null) {
            btnCleanup.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, CleanupActivity.class));
                }
            });
        }

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private SharedPreferences cfg() {
        return getSharedPreferences("cdesk_settings", Context.MODE_PRIVATE);
    }

    /** 播放器设置已移除（0.3 稳定版无影音页） */

    private String orientLabel(int mode) {
        return getString(mode == Screen.ORIENT_AUTO ? R.string.settings_orient_auto
                : mode == Screen.ORIENT_PORTRAIT ? R.string.settings_orient_portrait
                : R.string.settings_orient_landscape);
    }
}
