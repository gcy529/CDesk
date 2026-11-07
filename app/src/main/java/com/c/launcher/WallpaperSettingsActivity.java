package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** 壁纸设置：缩略图卡片选壁纸 + 日夜自动开关 + 公众号 + 重看须知 */
public class WallpaperSettingsActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper);

        ThemeManager.Palette p = ThemeManager.current(this);
        TextView title = (TextView) findViewById(R.id.tvWpTitle);
        title.setTextColor(p.isLight ? p.fg : p.accent);

        // 自动日夜开关（文字按钮）
        Button btnAuto = (Button) findViewById(R.id.btnWpAuto);
        ThemeManager.styleButton(btnAuto, p);
        btnAuto.setText(R.string.wp_auto);
        btnAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !WPManager.isAuto(WallpaperSettingsActivity.this);
                WPManager.setAuto(WallpaperSettingsActivity.this, next);
                Toast.makeText(WallpaperSettingsActivity.this,
                        next ? R.string.wp_auto : R.string.wp_none, Toast.LENGTH_SHORT).show();
            }
        });

        // 缩略图卡片：点击选壁纸
        card(R.id.cardWpNone, WPManager.WP_NONE);
        card(R.id.cardWpNight, WPManager.WP_NIGHT);
        card(R.id.cardWpDay, WPManager.WP_DAY);
        card(R.id.cardWpSunset, WPManager.WP_SUNSET);

        // 使用须知 + 返回
        findViewById(R.id.btnDisclaimer).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(WallpaperSettingsActivity.this, DisclaimerActivity.class));
            }
        });
        findViewById(R.id.btnWpBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private void card(int id, final int value) {
        View v = findViewById(id);
        if (v == null) return;
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                WPManager.setAuto(WallpaperSettingsActivity.this, false);
                WPManager.setWhich(WallpaperSettingsActivity.this, value);
                Toast.makeText(WallpaperSettingsActivity.this, R.string.wallpaper, Toast.LENGTH_SHORT).show();
                finish();   // 回桌面立刻生效
            }
        });
    }
}
