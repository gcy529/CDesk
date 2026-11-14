package com.c.launcher;

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
 *
 * 播放内核统一走 MusicKernel（ExoPlayer），与音乐首页、影视/直播的
 * PlayerActivity 内核一致；API < 21 的老车机由 MusicKernel 内部回退原生。
 * UI 上保留音乐专属的底部控制条（上一首 / 播放暂停 / 下一首）。
 */
public class MusicShellActivity extends BaseShellActivity implements MusicKernel.Listener {

    private LinearLayout controlBar;
    private TextView tvNow;
    private int current = -1;
    private int errStreak = 0;

    @Override protected String prefKey() { return "music_src"; }

    @Override protected String targetKind() { return "music"; }

    @Override protected int titleRes() { return R.string.music_title; }

    @Override protected int emptyTitleRes() { return R.string.music_empty_title; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CLog.i("MusicShellActivity onCreate");
        buildControls();
        MusicKernel k = MusicKernel.get(this);
        k.addListener(this);
        k.setOnCompleted(new Runnable() {
            @Override public void run() { step(1); }
        });
        k.setOnError(new Runnable() {
            @Override public void run() {
                errStreak++;
                if (errStreak < 3) { step(1); return; }
                Toast.makeText(MusicShellActivity.this, R.string.shell_play_failed, Toast.LENGTH_SHORT).show();
                NetDiag.diag(MusicKernel.get(MusicShellActivity.this).url(), new NetDiag.Sink() {
                    @Override public void line(String s) { CLog.i("music diag: " + s); }
                });
            }
        });
        syncNow();
    }

    /** 底部控制条：上一首 / 播放暂停 / 下一首 */
    private void buildControls() {
        LinearLayout root = (LinearLayout) findViewById(R.id.rootShell);
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
                MusicKernel.get(MusicShellActivity.this).toggle();
            }
        });

        // 插到列表下面
        root.addView(controlBar);
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
        current = items.indexOf(it);
        MusicKernel.get(this).play(it.url, it.name, false);
        History.push(this, it.name, it.url, "music", 0);
        syncNow();
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

    /** 同步当前曲名到控制条 */
    private void syncNow() {
        if (tvNow == null) return;
        String t = MusicKernel.get(this).title();
        if (t.length() == 0) {
            tvNow.setText(R.string.music_none);
        } else {
            tvNow.setText(t);
        }
    }

    @Override
    public void onStateChanged(boolean playing, String title, String url, boolean live) {
        if (playing) errStreak = 0;
        syncNow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 进后台就暂停，避免车上切走后一直响
        MusicKernel.get(this).pause();
    }

    @Override
    protected void onDestroy() {
        MusicKernel k = MusicKernel.get(this);
        k.removeListener(this);
        k.setOnCompleted(null);
        k.setOnError(null);
        super.onDestroy();
    }
}
