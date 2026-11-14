package com.c.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;

/**
 * 常驻播放内核：把 ExoPlayer 从 PlayerActivity 解耦，让主页迷你播放条也能驱动。
 *
 * 用法：
 *   MusicKernel.play(context, url, title, live)  -- 启动/切歌
 *   MusicKernel.pause() / resume() / stop()       -- 控制
 *   MusicKernel.addListener(l)                    -- 状态变化回调
 *
 * 设计取舍：
 *   - 单例，进程级生命周期（车机开机不杀，切页不断）
 *   - 音乐类 URL（file:// 或本地路径）切页保持；直播切页保持；点播切走停
 *   - 不自己建 ExoPlayer 实例，PlayerActivity 把现成的 player 转交过来，
 *     避免重复 build 一份 ExoPlayer（车机内存吃紧）
 */
public final class MusicKernel {

    public interface Listener {
        void onStateChanged(boolean playing, String title, String url, boolean live);
    }

    private static MusicKernel instance;

    private final Context ctx;
    private ExoPlayer player;
    private String url = "";
    private String title = "";
    private boolean live = false;
    private boolean paused = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final java.util.List<Listener> listeners = new java.util.ArrayList<Listener>();

    private Runnable onCompletedCb;
    private Runnable onErrorCb;
    private final ExoPlayer.Listener playerListener = new ExoPlayer.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
            if (state == ExoPlayer.STATE_ENDED) {
                final Runnable cb = onCompletedCb;
                if (cb != null) ui.post(new Runnable() {
                    @Override public void run() { cb.run(); }
                });
            }
        }
        @Override public void onPlayerError(PlaybackException e) {
            final Runnable cb = onErrorCb;
            if (cb != null) ui.post(new Runnable() {
                @Override public void run() { cb.run(); }
            });
        }
    };

    private MusicKernel(Context c) {
        this.ctx = c.getApplicationContext();
    }

    public static synchronized MusicKernel get(Context c) {
        if (instance == null) instance = new MusicKernel(c);
        return instance;
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    /** 播放完成回调（主线程），音乐壳用它实现自动跳下一首 */
    public void setOnCompleted(Runnable r) { onCompletedCb = r; }

    /** 播放出错回调（主线程），音乐壳用它实现失败跳下一首 */
    public void setOnError(Runnable r) { onErrorCb = r; }

    /** 让 PlayerActivity 把现成 ExoPlayer 转交给内核 */
    public synchronized void handoff(ExoPlayer p, String u, String t, boolean isLive) {
        if (player == p) return;
        releaseInternal();
        player = p;
        player.addListener(playerListener);
        url = u == null ? "" : u;
        title = t == null ? "" : t;
        live = isLive;
        paused = false;
        notifyListeners();
        CLog.i("kernel handoff url=" + url + " live=" + live);
    }

    /** 起播/切歌：没有 player 就自己 build 一份 */
    public void play(String u, String t, boolean isLive) {
        synchronized (this) {
            url = u == null ? "" : u;
            title = t == null ? "" : t;
            live = isLive;
            if (player == null) {
                try {
                    com.google.android.exoplayer2.source.DefaultMediaSourceFactory f =
                            new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(
                                    new com.google.android.exoplayer2.upstream.DefaultHttpDataSource.Factory()
                                            .setUserAgent("CDesk/1.0")
                                            .setAllowCrossProtocolRedirects(true));
                    player = new ExoPlayer.Builder(ctx, new com.google.android.exoplayer2.DefaultRenderersFactory(ctx))
                            .setMediaSourceFactory(f)
                            .build();
                    player.addListener(playerListener);
                } catch (Throwable e) {
                    CLog.e("kernel build failed " + e);
                    player = null;
                }
            }
            if (player != null) {
                player.setMediaItem(MediaItem.fromUri(url));
                player.setPlayWhenReady(true);
                player.prepare();
                paused = false;
                notifyListeners();
                CLog.i("kernel play " + url);
            }
        }
    }

    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
            paused = true;
            notifyListeners();
        }
    }

    public void resume() {
        if (player != null) {
            player.setPlayWhenReady(true);
            paused = false;
            notifyListeners();
        }
    }

    public void toggle() {
        if (player == null) return;
        if (player.getPlayWhenReady()) pause(); else resume();
    }

    public void stop() {
        releaseInternal();
        url = "";
        title = "";
        paused = false;
        live = false;
        notifyListeners();
    }

    public boolean isPlaying() { return player != null && player.getPlayWhenReady(); }
    public boolean isPaused() { return paused && player != null; }
    public String title() { return title; }
    public String url() { return url; }
    public boolean live() { return live; }

    public void seekBy(long ms) {
        if (player == null) return;
        long target = player.getCurrentPosition() + ms;
        if (target < 0) target = 0;
        long dur = player.getDuration();
        if (dur > 0 && target > dur) target = dur;
        player.seekTo(target);
    }

    private void releaseInternal() {
        if (player != null) {
            try {
                player.removeListener(playerListener);
                player.setPlayWhenReady(false);
                player.release();
            } catch (Throwable ignored) { }
            player = null;
        }
    }

    private void notifyListeners() {
        ui.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) {
                    try { l.onStateChanged(player != null && player.getPlayWhenReady(), title, url, live); }
                    catch (Throwable ignored) { }
                }
            }
        });
    }
}
