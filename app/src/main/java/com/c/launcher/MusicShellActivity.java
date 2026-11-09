package com.c.launcher;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 音乐壳：导入用户自己的音频列表（M3U / TXT / JSON），点条目播放。
 * 用系统 MediaPlayer，后台播放跟随 Activity 生命周期；不内置任何音源。
 */
public class MusicShellActivity extends BaseShellActivity {

    private MediaPlayer mp;
    private LinearLayout controlBar;
    private TextView tvNow;
    private int current = -1;
    private int errStreak = 0;

    @Override protected String prefKey() { return "music_src"; }

    @Override
    protected String targetKind() { return "music"; }

    @Override protected int titleRes() { return R.string.music_title; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.apply(this);
        buildControls();
    }

    /** 底部控制条：上一首 / 播放暂停 / 下一首 */
    private void buildControls() {
        LinearLayout root = (LinearLayout) findViewById(R.id.lvItems).getParent();
        ThemeManager.Palette p = ThemeManager.current(this);

        controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setPadding(0, dp(10), 0, 0);

        tvNow = new TextView(this);
        tvNow.setText(R.string.music_none);
        tvNow.setTextSize(13f);
        tvNow.setTextColor(p.dim);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        controlBar.addView(tvNow, tlp);

        Button prev = mkButton("⏮");
        Button pp = mkButton("⏯");
        Button next = mkButton("⏭");
        controlBar.addView(prev);
        controlBar.addView(pp);
        controlBar.addView(next);

        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { step(-1); }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { step(1); }
        });
        pp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mp == null) { step(1); return; }
                if (mp.isPlaying()) mp.pause(); else mp.start();
            }
        });

        // 插到列表下面
        LinearLayout parent = (LinearLayout) root;
        parent.addView(controlBar);
    }

    private Button mkButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18f);
        ThemeManager.styleButton(b, ThemeManager.current(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(46));
        lp.leftMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onItemSelected(PlaylistParser.Item it) {
        play(it);
    }

    private void play(PlaylistParser.Item it) {
        int idx = items.indexOf(it);
        current = idx;
        stopPlayer();
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(it.url);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer player) { errStreak = 0; player.start(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer player, int what, int extra) {
                    // 失败自动跳下一首，连挂 3 首才停下提示
                    errStreak++;
                    if (errStreak < 3) { step(1); return true; }
                    Toast.makeText(MusicShellActivity.this, R.string.shell_play_failed, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer player) { step(1); }
            });
            mp.prepareAsync();
            if (tvNow != null) tvNow.setText(it.name);
            History.push(this, it.name, it.url, "music", 0);
        } catch (Exception e) {
            Toast.makeText(this, R.string.shell_play_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 上/下一首：跳过分组头（url 为 null 的条目） */
    private void step(int dir) {
        if (items.isEmpty()) return;
        int start = current < 0 ? (dir > 0 ? -1 : 0) : current;
        int i = start;
        for (int guard = 0; guard < items.size(); guard++) {
            i += dir;
            if (i < 0) i = items.size() - 1;
            if (i >= items.size()) i = 0;
            if (items.get(i).url != null) { play(items.get(i)); return; }
        }
    }

    private void stopPlayer() {
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 进后台就停，避免车上切走后一直响
        if (mp != null && mp.isPlaying()) mp.pause();
    }

    @Override
    protected void onDestroy() {
        stopPlayer();
        super.onDestroy();
    }
}
