package com.c.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** 壁纸设置：选壁纸 + 日夜自动开关 + 公众号 + 重看须知 */
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

        bind(R.id.btnWpAuto, R.string.wp_auto);
        bind(R.id.btnWpNone, R.string.wp_none);
        bind(R.id.btnWpNight, R.string.wp_night);
        bind(R.id.btnWpDay, R.string.wp_day);
        bind(R.id.btnWpSunset, R.string.wp_sunset);
        bind(R.id.btnDisclaimer, R.string.disclaimer_title);
        bind(R.id.btnWpBack, R.string.transfer_back);

        findViewById(R.id.btnWpAuto).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !WPManager.isAuto(WallpaperSettingsActivity.this);
                WPManager.setAuto(WallpaperSettingsActivity.this, next);
                Toast.makeText(WallpaperSettingsActivity.this,
                        next ? R.string.wp_auto : R.string.wp_none, Toast.LENGTH_SHORT).show();
            }
        });
        pick(R.id.btnWpNone, WPManager.WP_NONE);
        pick(R.id.btnWpNight, WPManager.WP_NIGHT);
        pick(R.id.btnWpDay, WPManager.WP_DAY);
        pick(R.id.btnWpSunset, WPManager.WP_SUNSET);

        findViewById(R.id.btnDisclaimer).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(WallpaperSettingsActivity.this, DisclaimerActivity.class));
            }
        });
        findViewById(R.id.btnWpBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private void bind(int id, int textRes) {
        Button b = (Button) findViewById(id);
        ThemeManager.styleButton(b, ThemeManager.current(this));
        b.setText(textRes);
    }

    private void pick(int id, final int value) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                WPManager.setAuto(WallpaperSettingsActivity.this, false);
                WPManager.setWhich(WallpaperSettingsActivity.this, value);
                Toast.makeText(WallpaperSettingsActivity.this, R.string.wallpaper, Toast.LENGTH_SHORT).show();
                finish();   // 回桌面立刻生效
            }
        });
    }
}
