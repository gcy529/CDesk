package com.c.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 更新提示 + 应用内下载 + 调系统安装器。
 *
 * 为什么不跳浏览器：车机上通常没有浏览器，或者浏览器下完也没有文件管理器去点安装。
 * 下到自己的目录再交给系统安装器，是唯一走得通的路。
 *
 * 强更新也不留死路：下载失败自动换下一条线路；所有线路都失败时明确告诉用户，
 * 而不是静默把人卡在对话框里。
 */
public final class UpdatePrompt {

    private UpdatePrompt() { }

    public static void show(final Activity a, String version, String url, final boolean force) {
        final String[] urls = UpdateChecker.mirrorUrls(url);
        if (urls.length == 0) { return; }
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle(a.getString(force ? R.string.update_force_title : R.string.update_found, version));
        b.setMessage(a.getString(force ? R.string.update_force_msg : R.string.update_soft_msg));
        // 强制更新时返回键和点外面都关不掉：只能更新，或者换线路重试
        b.setCancelable(!force);
        b.setPositiveButton(R.string.update_now, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { download(a, urls, 0); }
        });
        if (urls.length > 1) {
            b.setNeutralButton(R.string.update_switch, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { download(a, urls, 1); }
            });
        }
        if (!force) { b.setNegativeButton(R.string.update_later, null); }
        try { b.show(); } catch (Throwable t) { CLog.e("update prompt failed: " + t); }
    }

    private static void download(final Activity a, final String[] urls, final int idx) {
        if (idx >= urls.length) {
            Toast.makeText(a, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        final ProgressDialog pd = new ProgressDialog(a);
        pd.setMessage(a.getString(R.string.update_downloading, (idx + 1) + "/" + urls.length));
        pd.setCancelable(true);
        try { pd.show(); } catch (Throwable ignored) { }

        new Thread(new Runnable() {
            @Override public void run() {
                File out = null;
                try {
                    File dir = a.getExternalFilesDir(null);
                    if (dir == null) { dir = a.getCacheDir(); }
                    out = new File(dir, "CDesk-update.apk");
                    if (out.exists()) { out.delete(); }
                    HttpURLConnection c = (HttpURLConnection) new URL(urls[idx]).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(15000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "CDesk-updater");
                    int code = c.getResponseCode();
                    if (code != 200) { throw new Exception("http " + code); }
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        total += n;
                        if (total > 80L * 1024L * 1024L) { break; }
                    }
                    fo.close();
                    in.close();
                    c.disconnect();
                    // 小于 100KB 基本是错误页或空文件，按失败处理
                    if (total < 100L * 1024L) { throw new Exception("too small " + total); }
                    CLog.i("update downloaded " + total + "B via mirror " + (idx + 1));
                } catch (Throwable t) {
                    CLog.e("update download failed (mirror " + (idx + 1) + "): " + t);
                    out = null;
                }
                final File apk = out;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try { pd.dismiss(); } catch (Throwable ignored) { }
                        if (apk == null) { download(a, urls, idx + 1); return; }
                        install(a, apk);
                    }
                });
            }
        }).start();
    }

    private static void install(Activity a, File apk) {
        // 装之前读一眼这个包里的真实版本号：不比当前新就说明下错了包，宁可报错也不让人装回旧版
        try {
            android.content.pm.PackageInfo pi = a.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            String cur = a.getString(R.string.app_version);
            if (pi != null && pi.versionName != null && !UpdateChecker.isNewer(pi.versionName, cur)) {
                CLog.e("update apk stale: " + pi.versionName + " <= " + cur);
                Toast.makeText(a, R.string.update_apk_stale, Toast.LENGTH_LONG).show();
                apk.delete();
                return;
            }
        } catch (Throwable t) {
            CLog.e("update verify failed " + t);
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = androidx.core.content.FileProvider.getUriForFile(
                        a, a.getPackageName() + ".updates", apk);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(apk);
            }
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable t) {
            CLog.e("install failed: " + t);
            Toast.makeText(a, R.string.update_download_failed, Toast.LENGTH_LONG).show();
        }
    }
}
