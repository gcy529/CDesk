package com.c.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** 壁纸设置：缩略图卡片选壁纸 + 日夜自动开关 + 公众号 + 重看须知 */
public class WallpaperSettingsActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.apply(this);
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

        // 缩略图卡片：晨光(默认) / 鲸夜 / 傍晚 + 自定义平时隐藏。遥控器只点真正能用的格
        card(R.id.cardWpMorning, WPManager.WP_MORNING);
        card(R.id.cardWpNight, WPManager.WP_NIGHT);
        card(R.id.cardWpDusk, WPManager.WP_DUSK);

        // 默认壁纸缩略图（晨光 = 4K 原图）：绝不能直接当缩略图（OOM）——inSampleSize 降采样后小图设置
        ImageView morningThumb = (ImageView) findViewById(R.id.thumbMorning);
        if (morningThumb != null) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(getResources(), R.drawable.wall_morning, o);
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = 24;   // 3845x2157 -> 约160x90，正好缩略图大小
                morningThumb.setImageBitmap(BitmapFactory.decodeResource(
                        getResources(), R.drawable.wall_morning, o2));
            } catch (Throwable ignored) { }
        }

        // 傍晚缩略图用 4K 原图降采样
        ImageView duskThumb = (ImageView) findViewById(R.id.thumbDusk);
        if (duskThumb != null) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(getResources(), R.drawable.final_wallpaper_4k, o);
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = 24;
                duskThumb.setImageBitmap(BitmapFactory.decodeResource(
                        getResources(), R.drawable.final_wallpaper_4k, o2));
            } catch (Throwable ignored) { }
        }

        // 自定义图片：平时也占一格（固定 3 个自带 + 1 自定义 = 4 张卡片），有人传图才弹选择器
        View customCard = findViewById(R.id.cardWpCustom);
        if (customCard != null) {
            customCard.setVisibility(View.VISIBLE);
            customCard.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showCustomPicker(); }
            });
        }

        // 使用须知 + 返回
        findViewById(R.id.btnWpBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 41 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            File saved = CustomWallpapers.add(this, data.getData());
            if (saved != null) {
                WPManager.setAuto(this, false);
                WPManager.setCustomPath(this, saved.getAbsolutePath());
                Toast.makeText(this, R.string.wallpaper, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.shell_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 自定义壁纸选择器：最多 3 格。
     *  点已有的图 = 用它   ·   点 ＋ = 从相册加一张   ·   长按已有的图 = 删除
     */
    private void showCustomPicker() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(20), dp(20), dp(20));

        List<File> files = CustomWallpapers.list(this);
        for (int k = 0; k < files.size(); k++) {
            final File f = files.get(k);
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(128), dp(128));
            lp.rightMargin = dp(12);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumbnail(f));
            iv.setBackgroundResource(R.drawable.bg_focus);
            iv.setFocusable(true);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    WPManager.setAuto(WallpaperSettingsActivity.this, false);
                    WPManager.setCustomPath(WallpaperSettingsActivity.this, f.getAbsolutePath());
                    Toast.makeText(WallpaperSettingsActivity.this, R.string.wallpaper, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    CustomWallpapers.remove(WallpaperSettingsActivity.this, f);
                    Toast.makeText(WallpaperSettingsActivity.this, R.string.wp_custom_hint, Toast.LENGTH_SHORT).show();
                    pickDialog.dismiss();
                    showCustomPicker();
                    return true;
                }
            });
            row.addView(iv);
        }

        if (files.size() < CustomWallpapers.MAX) {
            TextView add = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(128), dp(128));
            add.setLayoutParams(lp);
            add.setText("＋");
            add.setTextSize(34f);
            add.setGravity(Gravity.CENTER);
            add.setTextColor(ThemeManager.TEXT_SUB);
            add.setBackgroundResource(R.drawable.bg_focus);
            add.setFocusable(true);
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openImagePicker(); }
            });
            row.addView(add);
        }

        pickDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wp_custom)
                .setView(row)
                .setMessage(R.string.wp_custom_hint)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        pickDialog.show();
    }

    private AlertDialog pickDialog;

    private void openImagePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, getString(R.string.wp_custom_pick)), 41);
        } catch (Exception e) {
            Toast.makeText(this, R.string.shell_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap thumbnail(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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
