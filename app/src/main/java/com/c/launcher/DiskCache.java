package com.c.launcher;

import android.content.Context;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;

/**
 * 磁盘缓存：边播边存到 cacheDir，断网/弱网时已缓存的部分秒开。
 * 上限可配（PlayerSettings），默认 256M；老车机存储小的选 128M。
 * 只存当前流，不预下。缓存满了按 LRU 淘汰旧文件。直播不缓存。
 */
public final class DiskCache {

    private static final String PREFS = "cdesk_player";
    private static final String KEY_SIZE = "cache_size_mb";
    private static final int DEFAULT_MB = 256;
    private static Cache instance;
    private static long currentSize = -1;

    private DiskCache() { }

    /** 目标缓存上限（字节），从设置读，默认 256M */
    public static long targetBytes(Context c) {
        int mb = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SIZE, DEFAULT_MB);
        return (long) mb * 1024 * 1024;
    }

    public static void setTargetMB(Context c, int mb) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SIZE, mb).commit();
    }

    /** 当前缓存占了多少（字节） */
    public static long usedBytes(Context c) {
        if (instance == null) return 0;
        File dir = cacheDir(c);
        long sum = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) sum += f.length();
        }
        return sum;
    }

    public static File cacheDir(Context c) {
        File base = new File(c.getCacheDir(), "exo_cache");
        if (!base.exists()) base.mkdirs();
        return base;
    }

    /** 创建（或复用）Cache 实例，目标大小变化时重建 */
    public static synchronized Cache get(Context c) {
        long target = targetBytes(c);
        if (instance != null && currentSize == target) return instance;
        if (instance != null) {
            try { instance.release(); } catch (Throwable ignored) { }
        }
        currentSize = target;
        instance = new SimpleCache(cacheDir(c), new LeastRecentlyUsedCacheEvictor(target));
        return instance;
    }

    /**
     * 给 ExoPlayer 的 DataSource 套上缓存层。
     * 直播直接走 http（不缓存）；点播/音乐走 CacheDataSource，边播边存。
     */
    public static DataSource.Factory factory(Context c, boolean live) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("CDesk/1.0")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true);
        if (live) return http;
        final Cache cache = get(c);
        return new DataSource.Factory() {
            @Override
            public DataSource createDataSource() {
                return new CacheDataSource(cache, http.createDataSource());
            }
        };
    }

    /** 释放缓存（设置里"清空缓存"按钮用） */
    public static synchronized void clear(Context c) {
        if (instance != null) {
            try { instance.release(); } catch (Throwable ignored) { }
            instance = null;
        }
        currentSize = -1;
        File dir = cacheDir(c);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        CLog.i("disk cache cleared");
    }
}
