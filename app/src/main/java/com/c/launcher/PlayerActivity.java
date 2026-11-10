package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.video.VideoSize;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 播放器：ExoPlayer 2.18.1（minSdk 16 能跑的最后一版）+ 自绘控制层。
 *
 * 为什么换 ExoPlayer：系统 VideoView 是 MediaPlayer 内核，
 * 对 HLS 变体支持残缺、FLV/MKV/HEVC 完全不认，直播源基本全废。
 * ExoPlayer 自带 HLS/DASH/TS 解析，同样的源就能出画面。
 *
 * 交互：
 *   单击画面       显隐控制条
 *   左右键         -10s / +10s
 *   确认键         显隐控制条
 *   长按画面       2.5 倍速，松手恢复（沿用旧交互）
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LIVE = "live";
    public static final String EXTRA_ALT = "alt_urls";

    private static final String[] SPEEDS = new String[] { "0.5x", "1.0x", "1.25x", "1.5x", "2.0x", "2.5x", "3.0x" };
    private static final float[] SPEEDV = new float[] { 0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f };

    private ExoPlayer player;
    private SurfaceView surface;
    private View ctrlBar;
    private View loading;
    private TextView tvTitle, tvPos, tvDur, tvSpeed, tvClock, btnPlayPause;
    private SeekBar seek;
    private final Handler ui = new Handler();

    private String url;
    private String[] alts = new String[0];
    private boolean live = false;
    private boolean prepared = false;
    private boolean dragging = false;
    private int speedIdx = 1;
    private int retries = 0;
    private String playKey = "";
    private long resumeAt = -1;
    private long lastTap = 0;
    private boolean speedBoost = false;
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

        surface = (SurfaceView) findViewById(R.id.surfacePlayer);
        loading = findViewById(R.id.pbLoading);
        ctrlBar = findViewById(R.id.playerCtrl);
        tvTitle = (TextView) findViewById(R.id.tvPlayerTitle);
        tvPos = (TextView) findViewById(R.id.tvPos);
        tvDur = (TextView) findViewById(R.id.tvDur);
        tvSpeed = (TextView) findViewById(R.id.btnSpeed);
        tvClock = (TextView) findViewById(R.id.tvClockP);
        btnPlayPause = (TextView) findViewById(R.id.btnPlayPause);
        seek = (SeekBar) findViewById(R.id.sbProgress);

        if (tvTitle != null && titleBackup.length() > 0) tvTitle.setText(titleBackup);
        if (live && seek != null) seek.setVisibility(View.GONE);

        SharedPreferences cfg = getSharedPreferences("cdesk_settings", Context.MODE_PRIVATE);
        final int seekStep = cfg.getInt("player_seek_step", 30) * 1000;
        boolean showBar = cfg.getBoolean("player_show_bar", true);
        ctrlBar.setVisibility(View.GONE);
        if (!showBar && seek != null) seek.setVisibility(View.GONE);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (fromUser && tvPos != null) tvPos.setText(fmt(v));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                if (player != null) { player.seekTo(s.getProgress()); saveProgress(s.getProgress()); }
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        tvSpeed.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setSpeed((speedIdx + 1) % SPEEDS.length); }
        });
        View b10 = findViewById(R.id.btnBack10);
        if (b10 != null) b10.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekBy(-10000); }
        });
        View f10 = findViewById(R.id.btnFwd10);
        if (f10 != null) f10.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekBy(10000); }
        });
        View kern = findViewById(R.id.btnKernel);
        if (kern != null) kern.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { retryWithNextLine(); }
        });

        surface.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        speedBoost = false;
                        ui.postDelayed(longPressRun, 600);
                        return true;
                    case MotionEvent.ACTION_UP:
                        ui.removeCallbacks(longPressRun);
                        if (speedBoost) { setSpeed(1); speedBoost = false; return true; }
                        long now = System.currentTimeMillis();
                        if (now - lastTap < 280) {
                            togglePlay();
                            lastTap = 0;
                        } else {
                            lastTap = now;
                            ui.postDelayed(singleTapRun, 290);
                        }
                        return true;
                }
                return false;
            }
        });

        resumeAt = sp().getLong(playKey, -1);
        startPlay(url);
        ui.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (tvClock != null) tvClock.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            if (player != null && prepared) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (!dragging && seek != null && dur > 0) seek.setProgress((int) pos);
                if (tvPos != null) tvPos.setText(fmt(pos));
                if (tvDur != null && dur > 0) tvDur.setText(fmt(dur));
                if (!live) saveProgress(pos);
            }
            ui.postDelayed(this, 1000);
        }
    };

    private final Runnable longPressRun = new Runnable() {
        @Override public void run() {
            speedBoost = true;
            if (player != null) player.setPlaybackSpeed(2.5f);
            Toast.makeText(PlayerActivity.this, R.string.player_speed_boost, Toast.LENGTH_SHORT).show();
        }
    };

    private final Runnable singleTapRun = new Runnable() {
        @Override public void run() { toggleCtrl(); }
    };

    private final Runnable hideCtrl = new Runnable() {
        @Override public void run() { if (ctrlBar != null) ctrlBar.setVisibility(View.GONE); }
    };

    private void toggleCtrl() {
        if (ctrlBar == null) return;
        if (ctrlBar.getVisibility() == View.VISIBLE) ctrlBar.setVisibility(View.GONE);
        else {
            ctrlBar.setVisibility(View.VISIBLE);
            ui.removeCallbacks(hideCtrl);
            ui.postDelayed(hideCtrl, 4000);
        }
    }

    private void togglePlay() {
        if (player == null) return;
        boolean playing = player.getPlayWhenReady();
        player.setPlayWhenReady(!playing);
        if (btnPlayPause != null) btnPlayPause.setText(playing ? "\u25B6" : "\u2759\u2759");
    }

    private void seekBy(long delta) {
        if (player == null) return;
        long target = player.getCurrentPosition() + delta;
        if (target < 0) target = 0;
        long dur = player.getDuration();
        if (dur > 0 && target > dur) target = dur;
        player.seekTo(target);
    }

    private void setSpeed(int i) {
        speedIdx = i;
        if (tvSpeed != null) tvSpeed.setText(SPEEDS[i]);
        if (player != null) player.setPlaybackSpeed(SPEEDV[i]);
    }

    private void startPlay(String u) {
        if (u == null || u.length() < 5) {
            if (tvTitle != null) tvTitle.setText(R.string.shell_play_failed);
            return;
        }
        prepared = false;
        if (loading != null) loading.setVisibility(View.VISIBLE);
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
            player.setVideoSurfaceView(surface);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(listener);
            player.setMediaItem(MediaItem.fromUri(u));
            player.setPlayWhenReady(true);
            player.prepare();
            setSpeed(speedIdx);
        } catch (Throwable t) {
            if (loading != null) loading.setVisibility(View.GONE);
            if (tvTitle != null) tvTitle.setText(R.string.shell_play_failed);
            CLog.e("player create failed", t);
        }
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                prepared = true;
                if (loading != null) loading.setVisibility(View.GONE);
                if (tvTitle != null && titleBackup.length() > 0) tvTitle.setText(titleBackup);
                surface.setKeepScreenOn(true);
                long r = resumeAt;
                resumeAt = -1;
                if (r > 0 && player != null) player.seekTo(r);
                if (seek != null) {
                    long d = player == null ? 0 : player.getDuration();
                    if (d > 0 && d < Integer.MAX_VALUE) { seek.setMax((int) d); }
                }
                ui.postDelayed(hideCtrl, 3500);
            } else if (state == Player.STATE_BUFFERING) {
                if (loading != null) loading.setVisibility(View.VISIBLE);
            } else if (state == Player.STATE_ENDED) {
                if (!live) {
                    sp().edit().remove(playKey).commit();
                    finish();
                }
            }
        }

        @Override public void onPlayerError(PlaybackException e) {
            prepared = false;
            if (loading != null) loading.setVisibility(View.GONE);
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

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            seekBy(-10000);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBy(10000);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleCtrl();
            return true;
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
        releasePlayer();
        super.onDestroy();
    }
}
