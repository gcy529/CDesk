package com.c.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

    private TextView tvCleanupFreed, tvCleanupRemain, tvCacheUsed, tvCacheTarget;
    private Button btnGo, btnBack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 方向已在 Manifest 锁横屏，与桌面一致
        Screen.apply(this);
        setContentView(R.layout.activity_cleanup);

        tvCleanupFreed = (TextView) findViewById(R.id.tvCleanupFreed);
        tvCleanupRemain = (TextView) findViewById(R.id.tvCleanupRemain);
        tvCacheUsed = (TextView) findViewById(R.id.tvCleanupCacheUsed);
        tvCacheTarget = (TextView) findViewById(R.id.tvCleanupCacheTarget);
        btnGo = (Button) findViewById(R.id.btnCleanupGo);
        btnBack = (Button) findViewById(R.id.btnCleanupBack);

        refreshInfo();
        CLog.i("cleanup page open");

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long before = dirSize(getCacheDir());
                // 1) ExoPlayer 磁盘缓存
                DiskCache.clear(CleanupActivity.this);
                // 2) 整个 cache/ 目录（兜底，含 exo 之外的临时文件）
                deleteDir(getCacheDir());
                long freed = Math.max(0, before - dirSize(getCacheDir()));
                double freedGb = freed / 1024.0 / 1024.0 / 1024.0;
                tvCleanupFreed.setText(getString(R.string.cleanup_done, freedGb));
                refreshInfo();
                CLog.i("cleanup freed " + freedGb + " GB");
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(CleanupActivity.this, SettingsActivity.class));
                finish();
            }
        });

        final TextView[] info = new TextView[] { tvCleanupFreed, tvCleanupRemain, tvCacheUsed, tvCacheTarget };
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
        double totalGb = total / 1024.0 / 1024.0 / 1024.0;
        double availGb = avail / 1024.0 / 1024.0 / 1024.0;
        int pct = total > 0 ? (int) (avail * 100 / total) : 0;
        // 位置挪出来：清理了多少、剩余多少一眼可见
        tvCleanupRemain.setText(getString(R.string.cleanup_remain, availGb, totalGb, pct));

        long used = DiskCache.usedBytes(this);
        tvCacheUsed.setText(getString(R.string.cleanup_used, used / 1024.0 / 1024.0 / 1024.0));
        double targetGb = DiskCache.targetBytes(this) / 1024.0 / 1024.0 / 1024.0;
        tvCacheTarget.setText(getString(R.string.cleanup_target, targetGb));
        // 还没清理过就提示点按钮，避免大片空白
        if (tvCleanupFreed.getText() == null || tvCleanupFreed.getText().toString().isEmpty()) {
            tvCleanupFreed.setText(R.string.cleanup_idle);
        }
    }

    /** 递归删目录下的文件（保留目录本身，只清内容） */
    private static void deleteDir(File d) {
        if (d == null || !d.exists()) return;
        if (d.isFile()) { d.delete(); return; }
        File[] children = d.listFiles();
        if (children == null) return;
        for (File f : children) deleteDir(f);
    }

    /** 目录总大小（字节），目录不存在或读不到返回 0 */
    private static long dirSize(File d) {
        if (d == null || !d.exists()) return 0L;
        if (d.isFile()) return d.length();
        long sum = 0L;
        File[] children = d.listFiles();
        if (children == null) return 0L;
        for (File f : children) sum += dirSize(f);
        return sum;
    }
}
