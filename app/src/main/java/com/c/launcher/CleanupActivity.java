package com.c.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

/**
 * 清理中心（管家级，但只在第三方 app 权限内做能做的）：
 *   - 清 CDesk 的 ExoPlayer 磁盘缓存（DiskCache，默认 256M）
 *   - 清 CDesk 的 cache/ 与 files/ 临时文件
 *   - 显示系统剩余内存（只读，不做杀进程）
 * 第三方 launcher 没有 root/system 权限，杀别的 app / 清系统运存做不了，
 * 这里如实展示能清理的范围，数字说话。
 */
public class CleanupActivity extends Activity {

    private TextView tvFree, tvCacheUsed, tvCacheTarget, tvResult;
    private Button btnGo, btnBack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_cleanup);

        tvFree = (TextView) findViewById(R.id.tvCleanupFree);
        tvCacheUsed = (TextView) findViewById(R.id.tvCleanupCacheUsed);
        tvCacheTarget = (TextView) findViewById(R.id.tvCleanupCacheTarget);
        tvResult = (TextView) findViewById(R.id.tvCleanupResult);
        btnGo = (Button) findViewById(R.id.btnCleanupGo);
        btnBack = (Button) findViewById(R.id.btnCleanupBack);

        refreshInfo();
        CLog.i("cleanup page open");

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long before = DiskCache.usedBytes(CleanupActivity.this);
                // 1) ExoPlayer 磁盘缓存
                DiskCache.clear(CleanupActivity.this);
                // 2) 整个 cache/ 目录（兜底，含 exo 之外的临时文件）
                deleteDir(getCacheDir());
                long freed = Math.max(0, before - DiskCache.usedBytes(CleanupActivity.this));
                tvResult.setText(getString(R.string.cleanup_done, freed / 1024 / 1024));
                refreshInfo();
                CLog.i("cleanup freed " + (freed / 1024 / 1024) + " MB");
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(CleanupActivity.this, SettingsActivity.class));
                finish();
            }
        });

        final TextView[] info = new TextView[] { tvFree, tvCacheUsed, tvCacheTarget, tvResult };
        Button[] all = new Button[] { btnGo, btnBack };
        TvNav.dockable(this, btnBack, all, info);
    }

    @Override public void finish() {
        CLog.i("cleanup page back");
        super.finish();
    }

    private void refreshInfo() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long total = mi.totalMem;
        long avail = mi.availMem;
        int totalMb = (int) (total / 1024 / 1024);
        int availMb = (int) (avail / 1024 / 1024);
        int pct = total > 0 ? (int) (avail * 100 / total) : 0;
        tvFree.setText(getString(R.string.cleanup_free, totalMb, availMb, pct));

        long used = DiskCache.usedBytes(this);
        tvCacheUsed.setText(getString(R.string.cleanup_used, (int) (used / 1024 / 1024)));
        int targetMb = (int) (DiskCache.targetBytes(this) / 1024 / 1024);
        tvCacheTarget.setText(getString(R.string.cleanup_target, targetMb));
    }

    /** 递归删目录下的文件（保留目录本身，只清内容） */
    private static void deleteDir(File d) {
        if (d == null || !d.exists()) return;
        if (d.isFile()) { d.delete(); return; }
        File[] children = d.listFiles();
        if (children == null) return;
        for (File f : children) deleteDir(f);
    }
}
