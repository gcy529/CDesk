package com.c.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;

/**
 * 播放器工厂：按系统 API 级别统一选择播放实现。
 *
 * API ≥ 21 → ExoPlayer：HLS / FLV / MKV / HEVC 全格式、直播稳定，功能最全。
 * API < 21 → MediaPlayer：系统自带，老车机（Android 4.x）兜底，基础格式可用。
 *
 * ExoPlayer 2.18.1 虽声明支持 minSdk 16，但部分高版本 API 在低系统上行为不稳，
 * 所以按 API 级别做条件选择：新系统走 ExoPlayer，老系统走 MediaPlayer，互不干扰。
 *
 * 兜底原则：即使 API ≥ 21，若 ExoPlayer 创建抛异常（解码器初始化失败等），
 * 调用方 catch 后回退到 createLegacy()，保证「能播」优先于「播得好」。
 */
public final class PlayerFactory {

    /** ExoPlayer 建议的最低 API：21（Android 5.0） */
    public static final int EXO_MIN_API = 21;

    private PlayerFactory() { }

    /** 当前系统是否支持 ExoPlayer */
    public static boolean isExoAvailable() {
        return Build.VERSION.SDK_INT >= EXO_MIN_API;
    }

    /**
     * 创建 ExoPlayer（API ≥ 21 时使用）。
     * 点播走磁盘缓存（边播边存），直播直接 HTTP；解码器回退打开，认不出的格式尽量硬解。
     */
    public static ExoPlayer createExo(Context ctx, boolean live) {
        DataSource.Factory dsFactory = DiskCache.factory(ctx, live);
        DefaultRenderersFactory rf = new DefaultRenderersFactory(ctx)
                .setEnableDecoderFallback(true);
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 2500, 5000)
                .build();
        return new ExoPlayer.Builder(ctx, rf)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dsFactory))
                .setLoadControl(lc)
                .build();
    }

    /**
     * 创建 MediaPlayer（API < 21 兜底，或 ExoPlayer 创建失败后的回退）。
     * 仅用 Android SDK 自带 API，不引入任何第三方依赖。
     * 唤醒锁 + 音乐流，保证车机息屏后音频继续。
     */
    public static MediaPlayer createLegacy(Context ctx) {
        MediaPlayer mp = new MediaPlayer();
        mp.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        return mp;
    }
}
