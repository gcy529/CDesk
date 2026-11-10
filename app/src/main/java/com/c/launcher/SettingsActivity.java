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
 * 设置页：独立进入（和打赏 / 传输一样的整页），不塞在影音页里。
 *
 * 内容：内容过滤、壁纸、语言、检查更新、使用须知、版本号。
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
        View root = findViewById(R.id.rootSettings);
        WPManager.applyToRoot(this, root);

        TextView title = (TextView) findViewById(R.id.tvSetTitle);
        title.setTextColor(p.isLight ? p.fg : p.accent);
        TextView ver = (TextView) findViewById(R.id.tvSetVersion);
        ver.setText("v" + getString(R.string.app_version));
        ver.setTextColor(p.dim);

        final Button btnFilter = (Button) findViewById(R.id.btnSetFilter);
        Button btnWall = (Button) findViewById(R.id.btnSetWallpaper);
        Button btnLang = (Button) findViewById(R.id.btnSetLang);
        Button btnPlayer = (Button) findViewById(R.id.btnSetPlayer);
        Button btnUpdate = (Button) findViewById(R.id.btnSetUpdate);
        Button btnDisclaimer = (Button) findViewById(R.id.btnSetDisclaimer);
        Button btnCore = (Button) findViewById(R.id.btnSetCore);
        final Button btnLog = (Button) findViewById(R.id.btnSetLog);
        Button btnBack = (Button) findViewById(R.id.btnSetBack);

        Button[] all = new Button[] { btnFilter, btnCore, btnWall, btnLang, btnPlayer, btnUpdate, btnDisclaimer, btnLog, btnBack };
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

        btnLang.setText(LangManager.label(this));
        btnLang.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LangManager.cycle(SettingsActivity.this); }
        });

        btnPlayer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPlayerSettings(); }
        });

        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(SettingsActivity.this, R.string.update_checking, Toast.LENGTH_SHORT).show();
                UpdateChecker.checkNow(SettingsActivity.this, new UpdateChecker.Callback() {
                    @Override public void onUpdate(String verName, String url) {
                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.update_found, verName), Toast.LENGTH_LONG).show();
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                        } catch (Throwable ignored) { }
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

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private SharedPreferences cfg() {
        return getSharedPreferences("cdesk_settings", Context.MODE_PRIVATE);
    }

    /** 播放器设置：进度条 / 跳片头 / 跳片尾 / 快进快退步长，全部即时保存 */
    private void showPlayerSettings() {
        SharedPreferences c = cfg();
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this).create();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        final Button barBtn = mkToggle(box,
                c.getBoolean("player_show_bar", true)
                        ? R.string.player_bar_on : R.string.player_bar_off);
        barBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !cfg().getBoolean("player_show_bar", true);
                cfg().edit().putBoolean("player_show_bar", next).commit();
                barBtn.setText(next ? R.string.player_bar_on : R.string.player_bar_off);
            }
        });

        final Button headBtn = mkToggle(box,
                c.getBoolean("player_skip_head", true)
                        ? R.string.player_head_on : R.string.player_head_off);
        headBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !cfg().getBoolean("player_skip_head", true);
                cfg().edit().putBoolean("player_skip_head", next).commit();
                headBtn.setText(next ? R.string.player_head_on : R.string.player_head_off);
            }
        });

        final Button tailBtn = mkToggle(box,
                c.getBoolean("player_skip_tail", false)
                        ? R.string.player_tail_on : R.string.player_tail_off);
        tailBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !cfg().getBoolean("player_skip_tail", false);
                cfg().edit().putBoolean("player_skip_tail", next).commit();
                tailBtn.setText(next ? R.string.player_tail_on : R.string.player_tail_off);
            }
        });

        final Button stepBtn = mkToggle(box, stepLabel(cfg().getInt("player_seek_step", 30)));
        stepBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = cfg().getInt("player_seek_step", 30);
                int next = cur == 30 ? 60 : (cur == 60 ? 10 : 30);
                cfg().edit().putInt("player_seek_step", next).commit();
                stepBtn.setText(stepLabel(next));
            }
        });

        final Button porBtn = mkToggle(box,
                c.getBoolean("player_portrait", false)
                        ? R.string.player_por_on : R.string.player_por_off);
        porBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !cfg().getBoolean("player_portrait", false);
                cfg().edit().putBoolean("player_portrait", next).commit();
                porBtn.setText(next ? R.string.player_por_on : R.string.player_por_off);
            }
        });

        dlg.setView(box);
        dlg.show();
    }

    private String stepLabel(int s) {
        return getString(s == 30 ? R.string.player_step_30 : s == 60 ? R.string.player_step_60 : R.string.player_step_10);
    }

    private Button mkToggle(LinearLayout box, String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        box.addView(b, lp);
        return b;
    }
    private Button mkToggle(LinearLayout box, int label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        box.addView(b, lp);
        return b;
    }
}
