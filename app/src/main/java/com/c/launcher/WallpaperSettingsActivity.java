package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

        // 自定义图片：选系统相册里的照片；也支持局域网传入的图片
        View customCard = findViewById(R.id.cardWpCustom);
        if (customCard != null) {
            customCard.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    try {
                        startActivityForResult(Intent.createChooser(i, getString(R.string.wp_custom_pick)), 41);
                    } catch (Exception e) {
                        Toast.makeText(WallpaperSettingsActivity.this, R.string.shell_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 41 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            String path = copyToFiles(data.getData());
            if (path != null) {
                WPManager.setAuto(this, false);
                WPManager.setCustomPath(this, path);
                Toast.makeText(this, R.string.wallpaper, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.shell_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** 把选中的图片复制到内部存储，避免重启后 URI 失效 */
    private String copyToFiles(Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.File dir = new java.io.File(getFilesDir(), "wallpaper");
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, "custom.jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            in.close();
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
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
