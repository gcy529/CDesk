package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.video.VideoSize;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 播放器：ExoPlayer 2.18.1（minSdk 16 能跑的最后一版）+ 官方 PlayerView 控制条。
 *
 * 为什么换 ExoPlayer：系统 VideoView 是 MediaPlayer 内核，
 * HLS 变体支持残缺、FLV/MKV/HEVC 完全不认，直播源基本全废。
 *
 * 控制条用 exoplayer-ui 的 PlayerView，不自己画：
 * 进度条 / 播放暂停 / 快退快进 / 缓冲圈 / 剩余时间 都是官方实现，
 * 遥控器与触摸都能用，比自己手搓的稳。
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LIVE = "live";
    public static final String EXTRA_ALT = "alt_urls";

    private static final long SEEK_STEP_MS = 10000L;

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvTitle, tvClock;
    private final Handler ui = new Handler();

    private String url;
    private String[] alts = new String[0];
    private boolean live = false;
    private boolean prepared = false;
    private int retries = 0;
    private String playKey = "";
    private long resumeAt = -1;
    private String titleBackup = "";
    private boolean oriented = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    private SharedPreferences sp() {
        return getSharedPreferences("cdesk_player", Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        CLog.i("PlayerActivity onCreate");
        Screen.apply(this);
        setContentView(R.layout.activity_player);

        url = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        String name = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_NAME);
        live = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LIVE, false);
        alts = getIntent() == null ? null : getIntent().getStringArrayExtra(EXTRA_ALT);
        if (alts == null) alts = new String[0];
        playKey = "pos_" + String.valueOf(url).hashCode();
        titleBackup = name == null ? "" : name;

        playerView = (PlayerView) findViewById(R.id.playerView);
        tvTitle = (TextView) findViewById(R.id.tvPlayerTitle);
        tvClock = (TextView) findViewById(R.id.tvClockP);

        if (tvTitle != null && titleBackup.length() > 0) tvTitle.setText(titleBackup);
        // 直播不给进度条：拖了也没意义
        if (playerView != null) {
            playerView.setUseController(true);
            try {
                playerView.getControllerShowTimeoutMs();
            } catch (Throwable ignored) { }
        }

        resumeAt = sp().getLong(playKey, -1);
        startPlay(url);
        ui.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (tvClock != null) tvClock.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            if (player != null && prepared && !live) {
                saveProgress(player.getCurrentPosition());
            }
            ui.postDelayed(this, 1000);
        }
    };

    private void startPlay(String u) {
        if (u == null || u.length() < 5) {
            if (tvTitle != null) tvTitle.setText(R.string.shell_play_failed);
            return;
        }
        prepared = false;
        try {
            releasePlayer();
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent("CDesk/1.0")
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(20000)
                    .setAllowCrossProtocolRedirects(true);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                    .build();
            if (playerView != null) playerView.setPlayer(player);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(listener);
            player.setMediaItem(MediaItem.fromUri(u));
            player.setPlayWhenReady(true);
            player.prepare();
        } catch (Throwable t) {
            if (tvTitle != null) tvTitle.setText(R.string.shell_play_failed);
            CLog.e("player create failed", t);
        }
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                prepared = true;
                if (tvTitle != null && titleBackup.length() > 0) tvTitle.setText(titleBackup);
                if (playerView != null) playerView.setKeepScreenOn(true);
                long r = resumeAt;
                resumeAt = -1;
                if (r > 0 && player != null) player.seekTo(r);
            } else if (state == Player.STATE_ENDED) {
                if (!live) {
                    sp().edit().remove(playKey).commit();
                    finish();
                }
            }
        }

        @Override public void onPlayerError(PlaybackException e) {
            prepared = false;
            CLog.e("player error", e);
            retryWithNextLine();
        }

        @Override public void onVideoSizeChanged(VideoSize size) {
            if (size != null) autoOrient(size.width, size.height);
        }
    };

    /** 出错时换下一条线路；没有备用线路就提示失败 */
    private void retryWithNextLine() {
        if (retries < alts.length) {
            String next = alts[retries];
            retries++;
            Toast.makeText(this, R.string.player_switch_line, Toast.LENGTH_SHORT).show();
            startPlay(next);
            return;
        }
        if (tvTitle != null) tvTitle.setText(R.string.shell_play_failed);
    }

    /** 视频 1920x1080 → 横屏；1080x1920 → 竖屏。只切一次 */
    private void autoOrient(int w, int h) {
        if (oriented || w <= 0 || h <= 0) return;
        oriented = true;
        try {
            setRequestedOrientation(w < h
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } catch (Throwable ignored) { }
    }

    private void saveProgress(long ms) {
        if (playKey.length() == 0) return;
        sp().edit().putLong(playKey, ms).commit();
        History.push(this, tvTitle == null ? "" : tvTitle.getText().toString(), url,
                live ? "live" : "vod", ms);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player != null) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - SEEK_STEP_MS));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                long dur = player.getDuration();
                long t = player.getCurrentPosition() + SEEK_STEP_MS;
                if (dur > 0 && t > dur) t = dur;
                player.seekTo(t);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.setPlayWhenReady(false);
                player.release();
            } catch (Throwable ignored) { }
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (playerView != null) playerView.setPlayer(null);
        releasePlayer();
        super.onDestroy();
    }
}
