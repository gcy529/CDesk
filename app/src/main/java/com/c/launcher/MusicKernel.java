package com.c.launcher;

import android.content.Context;
import android.media.MediaPlayer;
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
 *   - 播放实现按 API 级别分支：API≥21 走 ExoPlayer，API<21 走 MediaPlayer
 *     软解兜底，与 PlayerFactory 策略一致，老车机不因缺少 ExoPlayer 而放不了歌
 */
public final class MusicKernel {

    public interface Listener {
        void onStateChanged(boolean playing, String title, String url, boolean live);
    }

    private static MusicKernel instance;

    private final Context ctx;
    private ExoPlayer player;
    private MediaPlayer legacyMp;
    private boolean legacy = false;
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

    /** 让 PlayerActivity 把现成 ExoPlayer 转交给内核（仅 API≥21 时会调用） */
    public synchronized void handoff(ExoPlayer p, String u, String t, boolean isLive) {
        if (p == null) return;
        if (player == p) return;
        releaseInternal();
        legacy = false;
        player = p;
        player.addListener(playerListener);
        url = u == null ? "" : u;
        title = t == null ? "" : t;
        live = isLive;
        paused = false;
        notifyListeners();
        CLog.i("kernel handoff url=" + url + " live=" + live);
    }

    /** 起播/切歌：按 API 级别选播放实现，ExoPlayer 不可用或构建失败则回退 MediaPlayer */
    public void play(String u, String t, boolean isLive) {
        synchronized (this) {
            url = u == null ? "" : u;
            title = t == null ? "" : t;
            live = isLive;

            if (PlayerFactory.isExoAvailable()) {
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
                        CLog.e("kernel exo build failed, fallback to legacy: " + e);
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
                    return;
                }
                // ExoPlayer 构建失败，回退到 MediaPlayer
            }

            // 老设备（API<21）或 ExoPlayer 不可用：走 MediaPlayer 软解
            legacy = true;
            legacyPlay(u, t, isLive);
        }
    }

    /** MediaPlayer 软解路径：建连、设数据源、准备、起播 */
    private void legacyPlay(String u, String t, boolean isLive) {
        try {
            if (legacyMp == null) {
                legacyMp = PlayerFactory.createLegacy(ctx);
                legacyMp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer mp) {
                        final Runnable cb = onCompletedCb;
                        if (cb != null) ui.post(new Runnable() {
                            @Override public void run() { cb.run(); }
                        });
                    }
                });
                legacyMp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                        CLog.e("kernel legacy error what=" + what + " extra=" + extra);
                        final Runnable cb = onErrorCb;
                        if (cb != null) ui.post(new Runnable() {
                            @Override public void run() { cb.run(); }
                        });
                        return true;
                    }
                });
            }
            legacyMp.reset();
            legacyMp.setDataSource(u);
            legacyMp.prepare();
            legacyMp.start();
            paused = false;
            notifyListeners();
            CLog.i("kernel legacy play " + url);
        } catch (Throwable e) {
            CLog.e("kernel legacy play failed: " + e);
        }
    }

    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
            paused = true;
            notifyListeners();
        } else if (legacyMp != null) {
            legacyMp.pause();
            paused = true;
            notifyListeners();
        }
    }

    public void resume() {
        if (player != null) {
            player.setPlayWhenReady(true);
            paused = false;
            notifyListeners();
        } else if (legacyMp != null) {
            legacyMp.start();
            paused = false;
            notifyListeners();
        }
    }

    public void toggle() {
        if (player != null) {
            if (player.getPlayWhenReady()) pause(); else resume();
        } else if (legacyMp != null) {
            if (legacyMp.isPlaying()) pause(); else resume();
        }
    }

    public void stop() {
        releaseInternal();
        url = "";
        title = "";
        paused = false;
        live = false;
        notifyListeners();
    }

    public boolean isPlaying() {
        if (player != null) return player.getPlayWhenReady();
        if (legacyMp != null) return legacyMp.isPlaying();
        return false;
    }

    public boolean isPaused() {
        if (player != null) return paused && player != null;
        if (legacyMp != null) return paused && legacyMp != null;
        return false;
    }

    public String title() { return title; }
    public String url() { return url; }
    public boolean live() { return live; }

    public void seekBy(long ms) {
        if (player != null) {
            long target = player.getCurrentPosition() + ms;
            if (target < 0) target = 0;
            long dur = player.getDuration();
            if (dur > 0 && target > dur) target = dur;
            player.seekTo(target);
        } else if (legacyMp != null) {
            long target = legacyMp.getCurrentPosition() + ms;
            if (target < 0) target = 0;
            int dur = legacyMp.getDuration();
            if (dur > 0 && target > dur) target = dur;
            legacyMp.seekTo((int) target);
        }
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
        if (legacyMp != null) {
            try {
                legacyMp.stop();
                legacyMp.release();
            } catch (Throwable ignored) { }
            legacyMp = null;
        }
        legacy = false;
    }

    private void notifyListeners() {
        ui.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) {
                    try { l.onStateChanged(isPlaying(), title, url, live); }
                    catch (Throwable ignored) { }
                }
            }
        });
    }
}
