package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
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
    /** 来自哪个壳：music / live / vod。播放器据此区分控制条（上一首/下一首 vs 切频道 vs 选集），
     *  进度续播的 key 也带上 kind，点播和音乐撞到同一条不串 */
    public static final String EXTRA_KIND = "kind";

    private static final long SEEK_STEP_MS = 10000L;
    private static final int[] RESIZE_MODES = new int[] {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
    };
    private static final String[] RESIZE_NAMES = new String[] { "FIT", "ZOOM", "FILL" };
    private TextView tvBuf;
    private int seekStepMs = 10000;

    private ExoPlayer player;
    private PlayerView playerView;
    private View ctrlBar, loading;
    private TextView tvTitle, tvClock, tvPos, tvDur, tvError, btnPlayPause, btnResize;
    private SeekBar seek;
    private final Handler ui = new Handler();

    private String url;
    private String[] alts = new String[0];
    private boolean live = false;
    private String kind = "vod";
    private boolean prepared = false;
    private PlayStats stats;
    private android.widget.TextView tvStats;
    private boolean statsShown = false;
    private final Runnable hideStats = new Runnable() {
        @Override public void run() {
            statsShown = false;
            if (tvStats != null) { tvStats.setVisibility(View.GONE); }
        }
    };
    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            try {
                if (player != null && stats != null && tvStats != null) {
                    tvStats.setText(stats.line(player));
                    if (statsShown) { tvStats.setVisibility(View.VISIBLE); }
                }
            } catch (Throwable ignored) { }
            ui.postDelayed(this, 2000);
        }
    };
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
        kind = getIntent() == null ? "vod" : getIntent().getStringExtra(EXTRA_KIND);
        if (kind == null || kind.length() == 0) kind = live ? "live" : "vod";
        alts = getIntent() == null ? null : getIntent().getStringArrayExtra(EXTRA_ALT);
        if (alts == null) alts = new String[0];
        // 进度 key 带上 kind：同一条 url 在点播和音乐里不串
        playKey = "pos_" + kind + "_" + String.valueOf(url).hashCode();
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
        tvBuf = (TextView) findViewById(R.id.tvBuf);
        // 快进快退步长读设置（10/30/60s），不写死 10s
        int step = getSharedPreferences("cdesk_settings", Context.MODE_PRIVATE).getInt("player_seek_step", 30);
        seekStepMs = step == 10 ? 10000 : (step == 60 ? 60000 : 30000);
        wireLockOrientButton();

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
        if (btnLockOrient != null) btnLockOrient.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleLockOrient(); }
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
                if (tvBuf != null) {
                    long buffered = player.getBufferedPosition();
                    int pct = dur > 0 ? (int) (buffered * 100 / dur) : 0;
                    tvBuf.setText("缓 " + Math.max(0, pct) + "%");
                }
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
        // 快进按钮的标签也按步长显示
        TextView rew = (TextView) findViewById(R.id.btnRew);
        TextView fwd = (TextView) findViewById(R.id.btnFwd);
        int s = seekStepMs / 1000;
        if (rew != null) rew.setText("-" + s + "s");
        if (fwd != null) fwd.setText("+" + s + "s");
    }

    private void cycleResize() {
        resizeIdx = (resizeIdx + 1) % RESIZE_MODES.length;
        if (playerView != null) playerView.setResizeMode(RESIZE_MODES[resizeIdx]);
        if (btnResize != null) btnResize.setText(RESIZE_NAMES[resizeIdx]);
        CLog.i("player resize -> " + RESIZE_NAMES[resizeIdx]);
    }

    /** 锁方向：点一下锁住当前方向（车机开车场景），再点解锁跟传感器。
     *  车机没锁定的能自由转，锁了的用户想竖着看停车时手动切。 */
    private TextView btnLockOrient;
    private boolean orientLocked = false;
    private static final String LOCK_ICON = "\uD83D\uDD12";
    private static final String UNLOCK_ICON = "\uD83D\uDD13";

    private void wireLockOrientButton() {
        btnLockOrient = (TextView) findViewById(R.id.btnLockOrient);
        if (btnLockOrient == null) return;
        btnLockOrient.setText(UNLOCK_ICON);
        btnLockOrient.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleLockOrient(); }
        });
    }

    private void toggleLockOrient() {
        orientLocked = !orientLocked;
        if (orientLocked) {
            // 锁住当前方向，音频继续播（车机开车场景：别转了，但声音不停）
            int cur = getRequestedOrientation();
            boolean isLandscape = cur == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    || cur == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            setRequestedOrientation(isLandscape
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if (btnLockOrient != null) btnLockOrient.setText(LOCK_ICON);
            CLog.i("player orientation locked, audio keeps playing");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            if (btnLockOrient != null) btnLockOrient.setText(UNLOCK_ICON);
            CLog.i("player orientation unlocked");
        }
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
            // 点播/音乐走磁盘缓存（边播边存），直播直接 http
            com.google.android.exoplayer2.upstream.DataSource.Factory dsFactory =
                    DiskCache.factory(this, live);
            DefaultRenderersFactory rf = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);
            DefaultLoadControl lc = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15000, 60000, 2500, 5000)
                    .build();
            player = new ExoPlayer.Builder(this, rf)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dsFactory))
                    .setLoadControl(lc)
                    .build();
            if (playerView != null) playerView.setPlayer(player);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(listener);
            stats = new PlayStats();
            player.addAnalyticsListener(stats);
            player.setMediaItem(MediaItem.fromUri(u));
            player.setPlayWhenReady(true);
            player.prepare();
            CLog.i("player start: " + u);
            showStatsForAWhile();
        } catch (Throwable t) {
            if (loading != null) loading.setVisibility(View.GONE);
            showError(t.getClass().getSimpleName() + ": " + t.getMessage());
            CLog.e("player create failed", t);
        }
    }

    /**
     * 播放开始后把统计显示十来秒：卡不卡、机器解不解得动，一眼就看得出来。
     * 之后如果出现丢帧会自动再弹一次。
     */
    private void showStatsForAWhile() {
        if (tvStats == null) {
            tvStats = new android.widget.TextView(this);
            tvStats.setTextSize(11f);
            tvStats.setTextColor(0xCCFFFFFF);
            tvStats.setBackgroundColor(0x88000000);
            tvStats.setPadding(10, 6, 10, 6);
            try {
                ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.LEFT;
                lp.leftMargin = 24;
                lp.bottomMargin = 24;
                root.addView(tvStats, lp);
            } catch (Throwable t) {
                CLog.e("stats overlay failed: " + t);
                return;
            }
        }
        statsShown = true;
        tvStats.setVisibility(View.VISIBLE);
        ui.removeCallbacks(statsTick);
        ui.removeCallbacks(hideStats);
        ui.postDelayed(statsTick, 1200);
        ui.postDelayed(hideStats, 14000);
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

        // 出错时顺手把「为什么连不上」测一遍，直接摊在画面上，省得靠猜
        if (tvError != null) {
            NetDiag.diag(url, new NetDiag.Sink() {
                @Override public void line(final String s) {
                    CLog.i("diag: " + s);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (tvError != null) { tvError.append("\n" + s); }
                        }
                    });
                }
            });
        }
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
            seekBy(-seekStepMs);
            showCtrl();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBy(seekStepMs);
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
        // 音量键在车机方向盘上很常见：长按锁屏方向
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
        // 音乐/电台：把 ExoPlayer 转交给 MusicKernel，桌面迷你条继续驱动，切页不断
        // 视频点播：切走就停，直接 release
        boolean isMusic = !live && url != null
                && (url.startsWith("file") || url.startsWith("/storage") || url.startsWith("/sdcard"));
        if (isMusic && player != null) {
            MusicKernel.get(this).handoff(player, url, titleBackup, live);
            player = null;   // 内核接管，这里不再 release
        } else {
            if (playerView != null) playerView.setPlayer(null);
            releasePlayer();
        }
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
