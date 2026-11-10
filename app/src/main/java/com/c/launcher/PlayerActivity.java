package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.video.VideoSize;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 播放器：ExoPlayer 2.18.1（minSdk 16 能跑的最后一版）+ 自绘控制条。
 *
 * 内核为什么换：系统 VideoView 是 MediaPlayer，HLS 变体残缺、FLV/MKV/HEVC 不认，直播基本全废。
 * 控制条为什么自己画：PlayerView 自带的那套太素、还压画面中间；这里用底部渐隐玻璃 + 64dp 大按钮，
 * 遥控器焦点明显，手指也好点。
 *
 * 另一件重要的事：播放失败不再只显示一句「播放失败」，而是把 ExoPlayer 的错误码和原因
 * 直接打在画面正中 —— 截图就能定位是源的问题、网络的问题还是解码的问题。
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LIVE = "live";
    public static final String EXTRA_ALT = "alt_urls";

    private static final long SEEK_STEP_MS = 10000L;
    private static final int[] RESIZE_MODES = new int[] {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
    };
    private static final String[] RESIZE_NAMES = new String[] { "FIT", "ZOOM", "FILL" };

    private ExoPlayer player;
    private PlayerView playerView;
    private View ctrlBar, loading;
    private TextView tvTitle, tvClock, tvPos, tvDur, tvError, btnPlayPause, btnResize;
    private SeekBar seek;
    private final Handler ui = new Handler();

    private String url;
    private String[] alts = new String[0];
    private boolean live = false;
    private boolean prepared = false;
    private boolean dragging = false;
    private int retries = 0;
    private int resizeIdx = 0;
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
        ctrlBar = findViewById(R.id.ctrlBar);
        loading = findViewById(R.id.pbLoading);
        tvTitle = (TextView) findViewById(R.id.tvPlayerTitle);
        tvClock = (TextView) findViewById(R.id.tvClockP);
        tvPos = (TextView) findViewById(R.id.tvPos);
        tvDur = (TextView) findViewById(R.id.tvDur);
        tvError = (TextView) findViewById(R.id.tvPlayerError);
        btnPlayPause = (TextView) findViewById(R.id.btnPlayPause);
        btnResize = (TextView) findViewById(R.id.btnResize);
        seek = (SeekBar) findViewById(R.id.sbProgress);

        if (tvTitle != null && titleBackup.length() > 0) tvTitle.setText(titleBackup);
        // 直播没有总时长，进度条藏起来
        if (live && seek != null) seek.setVisibility(View.GONE);

        watcher();
        resumeAt = sp().getLong(playKey, -1);
        startPlay(url);
        ui.post(tick);
    }

    private void watcher() {
        if (seek != null) {
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                    if (fromUser && tvPos != null) tvPos.setText(fmt(v));
                }
                @Override public void onStartTrackingTouch(SeekBar s) { dragging = true; }
                @Override public void onStopTrackingTouch(SeekBar s) {
                    dragging = false;
                    if (player != null) {
                        player.seekTo(s.getProgress());
                        saveProgress(s.getProgress());
                    }
                }
            });
        }
        if (btnPlayPause != null) btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        View rew = findViewById(R.id.btnRew);
        if (rew != null) rew.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekBy(-SEEK_STEP_MS); }
        });
        View fwd = findViewById(R.id.btnFwd);
        if (fwd != null) fwd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekBy(SEEK_STEP_MS); }
        });
        if (btnResize != null) btnResize.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleResize(); }
        });
        View close = findViewById(R.id.btnClose);
        if (close != null) close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        if (playerView != null) {
            playerView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { toggleCtrl(); }
            });
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (tvClock != null) tvClock.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            if (player != null && prepared) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (!dragging && seek != null && dur > 0) seek.setProgress((int) Math.min(pos, Integer.MAX_VALUE));
                if (tvPos != null) tvPos.setText(fmt(pos));
                if (tvDur != null && dur > 0) tvDur.setText(fmt(dur));
                if (!live) saveProgress(pos);
            }
            ui.postDelayed(this, 1000);
        }
    };

    private final Runnable hideCtrl = new Runnable() {
        @Override public void run() { if (ctrlBar != null) ctrlBar.setVisibility(View.GONE); }
    };

    private void toggleCtrl() {
        if (ctrlBar == null) return;
        if (ctrlBar.getVisibility() == View.VISIBLE) {
            ctrlBar.setVisibility(View.GONE);
        } else {
            ctrlBar.setVisibility(View.VISIBLE);
            ui.removeCallbacks(hideCtrl);
            ui.postDelayed(hideCtrl, 5000);
        }
    }

    private void togglePlay() {
        if (player == null) return;
        boolean wasPlaying = player.getPlayWhenReady();
        player.setPlayWhenReady(!wasPlaying);
        if (btnPlayPause != null) btnPlayPause.setText(wasPlaying ? "\u25B6" : "\u2759\u2759");
    }

    private void seekBy(long delta) {
        if (player == null) return;
        long target = player.getCurrentPosition() + delta;
        if (target < 0) target = 0;
        long dur = player.getDuration();
        if (dur > 0 && target > dur) target = dur;
        player.seekTo(target);
        if (tvPos != null) tvPos.setText(fmt(target));
    }

    private void cycleResize() {
        resizeIdx = (resizeIdx + 1) % RESIZE_MODES.length;
        if (playerView != null) playerView.setResizeMode(RESIZE_MODES[resizeIdx]);
        if (btnResize != null) btnResize.setText(RESIZE_NAMES[resizeIdx]);
        CLog.i("player resize -> " + RESIZE_NAMES[resizeIdx]);
    }

    private void startPlay(String u) {
        if (tvError != null) tvError.setVisibility(View.GONE);
        if (u == null || u.length() < 5) {
            showError("URL 为空");
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
            if (playerView != null) playerView.setPlayer(player);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(listener);
            player.setMediaItem(MediaItem.fromUri(u));
            player.setPlayWhenReady(true);
            player.prepare();
            CLog.i("player start: " + u);
        } catch (Throwable t) {
            if (loading != null) loading.setVisibility(View.GONE);
            showError(t.getClass().getSimpleName() + ": " + t.getMessage());
            CLog.e("player create failed", t);
        }
    }

    /** 把失败原因摊在画面正中间，方便截图定位 */
    private void showError(String detail) {
        if (tvError == null) return;
        tvError.setVisibility(View.VISIBLE);
        String head = getString(R.string.shell_play_failed);
        String host = hostOf(url);
        String body = detail == null ? "" : detail;
        if (body.length() > 220) body = body.substring(0, 220) + "...";
        tvError.setText(head + "\n" + body + (host.length() > 0 ? "\n" + host : ""));
        CLog.i("player showError: " + head + " | " + body + " | " + host);
    }

    private static String hostOf(String u) {
        try {
            if (u == null) return "";
            int i = u.indexOf("://");
            if (i < 0) return "";
            int j = u.indexOf('/', i + 3);
            return j < 0 ? u.substring(i + 3) : u.substring(i + 3, j);
        } catch (Throwable t) {
            return "";
        }
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                prepared = true;
                if (loading != null) loading.setVisibility(View.GONE);
                if (tvError != null) tvError.setVisibility(View.GONE);
                if (tvTitle != null && titleBackup.length() > 0) tvTitle.setText(titleBackup);
                if (playerView != null) playerView.setKeepScreenOn(true);
                long r = resumeAt;
                resumeAt = -1;
                if (r > 0 && player != null) player.seekTo(r);
                long d = player == null ? 0 : player.getDuration();
                if (seek != null && d > 0 && d < Integer.MAX_VALUE) seek.setMax((int) d);
                ui.removeCallbacks(hideCtrl);
                ui.postDelayed(hideCtrl, 4000);
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
            if (retries < alts.length) {
                String next = alts[retries];
                retries++;
                CLog.i("player retry line " + retries + ": " + next);
                startPlay(next);
                return;
            }
            String code = "";
            try { code = e.getErrorCodeName(); } catch (Throwable ignored) { }
            String msg = e.getMessage();
            String cause = e.getCause() == null ? "" : e.getCause().getClass().getSimpleName();
            showError(code + (cause.length() > 0 ? " / " + cause : "") + (msg == null ? "" : "\n" + msg));
        }

        @Override public void onVideoSizeChanged(VideoSize size) {
            if (size != null) autoOrient(size.width, size.height);
        }
    };

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
            seekBy(-SEEK_STEP_MS);
            showCtrl();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBy(SEEK_STEP_MS);
            showCtrl();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleCtrl();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            togglePlay();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showCtrl() {
        if (ctrlBar == null) return;
        ctrlBar.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideCtrl);
        ui.postDelayed(hideCtrl, 5000);
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
