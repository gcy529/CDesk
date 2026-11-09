package com.c.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * 播放器：系统 VideoView + 自绘控制层。
 *
 * 交互：
 *   单击 / OK键   显隐控制条
 *   双击          暂停 / 继续
 *   长按          2.5 倍速，松手恢复
 *   左右键        快退 / 快进（步长在设置里调，默认 30 秒）
 *
 * 控制条：进度条 / 倍速循环 / 跳片头片尾 / 内核切换（系统 ijk exo 标签轮换）。
 * 右上角常显当前时间；进度条显隐、跳片头片尾、 seek 步长都在设置里可配。
 * 内核说明：ijk / exo 需要额外 10MB+ 依赖，当前包没有打进来；
 * 系统内核失败时自动换 UA 与解绑参数重试，等效「兼容内核」。
 * 标签先挂上，后续接入真 ijk/exo 时同一按钮直接生效。
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LIVE = "live";
    public static final String EXTRA_ALT = "alt_urls";

    private static final String[] SPEEDS = new String[] { "0.5x", "1.0x", "1.25x", "1.5x", "2.0x", "2.5x", "3.0x" };
    private static final float[] SPEEDV = new float[] { 0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f };
    private static final String[] KERNELS = new String[] { "系统", "ijk", "exo" };

    private VideoView vv;
    private View ctrlBar;
    private TextView tvTitle, tvPos, tvDur, tvSpeed, tvKernel, tvClockP;
    private TextView btnSkipHead, btnSkipTail;
    private SeekBar seek;
    private View bar;
    private final Handler ui = new Handler();

    private String url;
    private String[] alts = new String[0];
    private boolean live = false;
    private boolean prepared = false;
    private int speedIdx = 1;
    private int kernelIdx = 0;
    private int retries = 0;
    private String playKey = "";
    private long resumeAt = -1;
    private long lastTap = 0;
    private boolean speedBoost = false;
    private long bufferingStart = 0;
    private String titleBackup = "";
    private android.media.MediaPlayer.OnErrorListener errListenerRef = new android.media.MediaPlayer.OnErrorListener() {
        @Override public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
            return PlayerActivity.this.playerError(mp, what, extra);
        }
    };
    private long startPos = 0;      // 记忆起始位（跳片头用）

    private boolean oriented = false;

    /** 视频 1920x1080 → 横屏；1080x1920 → 竖屏。只切一次 */
    void autoOrient(int w, int h) {
        if (oriented || w <= 0 || h <= 0) return;
        oriented = true;
        setRequestedOrientation(w < h
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            tvClockP.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            if (!oriented && prepared) {
                try {
                    java.lang.reflect.Field f = VideoView.class.getDeclaredField("mMediaPlayer");
                    f.setAccessible(true);
                    Object mp = f.get(vv);
                    if (mp instanceof MediaPlayer) {
                        autoOrient(((MediaPlayer) mp).getVideoWidth(), ((MediaPlayer) mp).getVideoHeight());
                    }
                } catch (Throwable ignored) { }
            }
            // 缓冲超过 90 秒还没起播 → 判定失败，走换线路
            if (bufferingStart > 0 && System.currentTimeMillis() - bufferingStart > 90000) {
                bufferingStart = 0;
                vv.pause();
                vv.setOnErrorListener(errListenerRef);
                vv.start();   // 触发一次真错误，复用现有换线逻辑
            }
            if (vv != null && prepared && vv.isPlaying()) {
                long p = vv.getCurrentPosition();
                seek.setProgress((int) p);
                tvPos.setText(fmt(p));
                if (!live) saveProgress(p);
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    private SharedPreferences sp() {
        return getSharedPreferences("cdesk_player", Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Screen.apply(this);
        setContentView(R.layout.activity_player);

        url = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        String name = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_NAME);
        live = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LIVE, false);
        alts = getIntent().getStringArrayExtra(EXTRA_ALT);
        if (alts == null) alts = new String[0];
        playKey = "pos_" + String.valueOf(url).hashCode();

        vv = (VideoView) findViewById(R.id.vvPlayer);
        bar = findViewById(R.id.pbLoading);
        ctrlBar = findViewById(R.id.playerCtrl);
        tvTitle = (TextView) findViewById(R.id.tvPlayerTitle);
        tvPos = (TextView) findViewById(R.id.tvPos);
        tvDur = (TextView) findViewById(R.id.tvDur);
        tvSpeed = (TextView) findViewById(R.id.btnSpeed);
        tvKernel = (TextView) findViewById(R.id.btnKernel);
        tvClockP = (TextView) findViewById(R.id.tvClockP);
        btnSkipHead = (TextView) findViewById(R.id.btnSkipHead);
        btnSkipTail = (TextView) findViewById(R.id.btnSkipTail);
        seek = (SeekBar) findViewById(R.id.sbProgress);
        titleBackup = name == null ? "" : name;
        if (name != null && name.length() > 0) tvTitle.setText(name);

        // 设置：进度条显隐 / 跳片头片尾开关 / seek 步长
        SharedPreferences cfg = getSharedPreferences("cdesk_settings", Context.MODE_PRIVATE);
        boolean showBar = cfg.getBoolean("player_show_bar", true);
        boolean skipHead = cfg.getBoolean("player_skip_head", true);
        boolean skipTail = cfg.getBoolean("player_skip_tail", false);
        final int seekStep = cfg.getInt("player_seek_step", 30) * 1000;
        ctrlBar.setVisibility(showBar ? View.GONE : View.GONE);  // 默认都先藏，点出
        if (!showBar) seek.setVisibility(View.GONE);
        if (btnSkipHead != null) btnSkipHead.setVisibility(skipHead ? View.VISIBLE : View.GONE);
        if (btnSkipTail != null) btnSkipTail.setVisibility(skipTail ? View.VISIBLE : View.GONE);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (prepared) { vv.seekTo(s.getProgress()); saveProgress(s.getProgress()); }
            }
        });

        tvSpeed.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setSpeed((speedIdx + 1) % SPEEDS.length); }
        });
        tvKernel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchKernel(); }
        });
        if (btnSkipHead != null) {
            btnSkipHead.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (prepared) { vv.seekTo(vv.getCurrentPosition() + seekStep); saveProgress(vv.getCurrentPosition()); }
                }
            });
        }
        if (btnSkipTail != null) {
            btnSkipTail.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (prepared) {
                        long d = vv.getDuration();
                        long back = d - (long) seekStep;
                        if (back < 0) back = 0;
                        vv.seekTo((int) back);
                    }
                }
            });
        }

        vv.setOnTouchListener(new View.OnTouchListener() {
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
                            if (prepared) { if (vv.isPlaying()) vv.pause(); else vv.start(); }
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

        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                prepared = true;
                vv.setKeepScreenOn(true);   // 播放中防息屏（锁屏键仍在用户手里）
                autoOrient(mp.getVideoWidth(), mp.getVideoHeight());
                bar.setVisibility(View.GONE);
                seek.setMax(vv.getDuration());
                tvDur.setText(fmt(vv.getDuration()));
                long r = prefsProgress();
                startPos = r;
                if (r > 0) vv.seekTo((int) r);
                setSpeed(speedIdx);
                vv.start();
                ui.postDelayed(hideCtrl, 3500);
            }
        });

        // 竖屏短剧：按视频真实宽高自动切方向（宽<高 → 竖屏，宽>=高 → 横屏）
        // onPrepared 回调直接给了 MediaPlayer：mp.getVideoWidth/Height() 即尺寸
        // 但部分流此时还是 0x0，所以再挂 onVideoSizeChanged 兜底（API 16 起有）

        // exo 风格缓冲宽限：缓冲最多等 90 秒，期间显示"缓冲中"，不轻易报失败
        bufferingStart = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 23) {
            vv.setOnInfoListener(new android.media.MediaPlayer.OnInfoListener() {
                @Override public boolean onInfo(android.media.MediaPlayer mp, int what, int extra) {
                    if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        bufferingStart = System.currentTimeMillis();
                        tvTitle.setText(getString(R.string.player_buffering));
                        bar.setVisibility(View.VISIBLE);
                    } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        tvTitle.setText(titleBackup);
                        bar.setVisibility(View.GONE);
                        bufferingStart = 0;
                    }
                    return false;
                }
            });
        }

        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                return playerError(mp, what, extra);
            }
        });

        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if (!live) {
                    sp().edit().remove(playKey).commit();
                    finish();
                }
            }
        });

        resumeAt = sp().getLong(playKey, -1);
        startPlay(url, false);
        ui.post(tick);
    }

    private long prefsProgress() {
        if (resumeAt > 0) { long r = resumeAt; resumeAt = -1; return r; }
        return -1;
    }

    private final Runnable longPressRun = new Runnable() {
        @Override public void run() {
            speedBoost = true;
            if (prepared) mpSetSpeed(2.5f);
            Toast.makeText(PlayerActivity.this, R.string.player_speed_boost, Toast.LENGTH_SHORT).show();
        }
    };

    private final Runnable singleTapRun = new Runnable() {
        @Override public void run() { toggleCtrl(); }
    };

    private final Runnable hideCtrl = new Runnable() {
        @Override public void run() { ctrlBar.setVisibility(View.GONE); }
    };

    private void toggleCtrl() {
        if (ctrlBar.getVisibility() == View.VISIBLE) ctrlBar.setVisibility(View.GONE);
        else {
            ctrlBar.setVisibility(View.VISIBLE);
            ui.removeCallbacks(hideCtrl);
            ui.postDelayed(hideCtrl, 4000);
        }
    }

    private void startPlay(String u, boolean altKernel) {
        if (u == null || u.length() < 5) return;
        prepared = false;
        bar.setVisibility(View.VISIBLE);
        try {
            HashMap<String, String> h = new HashMap<String, String>();
            h.put("User-Agent", altKernel ? "Lavf/58.29.100" : "CDesk/1.0");
            vv.stopPlayback();
            vv.setVideoURI(android.net.Uri.parse(u), h);
        } catch (Throwable t) {
            tvTitle.setText(getString(R.string.shell_play_failed));
        }
    }

    private void switchKernel() {
        kernelIdx = (kernelIdx + 1) % KERNELS.length;
        tvKernel.setText(KERNELS[kernelIdx]);
        retries = 0;
        // 系统内核之外的两个标签：现在还没有真 ijk/exo，行为上仍走系统内核，
        // 但换 UA 与解码参数，给流一个不同的协商路径，部分源这样就活了
        startPlay(url, kernelIdx != 0);
    }

    private void setSpeed(int i) {
        speedIdx = i;
        tvSpeed.setText(SPEEDS[i]);
        if (prepared) mpSetSpeed(SPEEDV[i]);
    }

    private void mpSetSpeed(float s) {
        try {
            java.lang.reflect.Field f = VideoView.class.getDeclaredField("mMediaPlayer");
            f.setAccessible(true);
            Object mp = f.get(vv);
            if (mp instanceof MediaPlayer && Build.VERSION.SDK_INT >= 23) {
                MediaPlayer real = (MediaPlayer) mp;
                real.setPlaybackParams(real.getPlaybackParams().setSpeed(s));
            }
        } catch (Throwable ignored) { }
    }

    boolean playerError(MediaPlayer mp, int what, int extra) {
        prepared = false;
        bar.setVisibility(View.GONE);
        retries++;
        if (kernelIdx == 0 && retries == 1) {
            tvKernel.setText(KERNELS[1]);
            startPlay(url, true);
            return true;
        }
        if (retries <= alts.length + 2) {
            int idx = retries - 2;
            if (idx >= 0 && idx < alts.length) {
                Toast.makeText(PlayerActivity.this, R.string.player_switch_line, Toast.LENGTH_SHORT).show();
                startPlay(alts[idx], false);
                return true;
            }
        }
        tvTitle.setText(getString(R.string.shell_play_failed));
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (prepared && !live) {
            SharedPreferences cfg = getSharedPreferences("cdesk_settings", Context.MODE_PRIVATE);
            int step = cfg.getInt("player_seek_step", 30) * 1000;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                vv.seekTo(Math.max(0, vv.getCurrentPosition() - step));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                vv.seekTo(Math.min(vv.getDuration(), vv.getCurrentPosition() + step));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                toggleCtrl();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void saveProgress(long ms) {
        sp().edit().putLong(playKey, ms).commit();
        // 最近播放：每 5 秒刷一次（saveProgress 本来就 5 秒一调）
        History.push(this, tvTitle.getText().toString(), url,
                live ? "live" : "vod", ms);
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try { if (vv != null) vv.stopPlayback(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
