package com.c.launcher;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import java.util.HashMap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

/**
 * 播放器：用系统自带 VideoView（Android 4.1 起就能跑 HLS/MP4），零第三方依赖。
 * 直播流断流会回调 onError，这里提示并允许返回重选。
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LIVE = "live";

    private VideoView vv;
    private ProgressBar bar;
    private TextView tip;
    private int retries = 0;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        vv = (VideoView) findViewById(R.id.vvPlayer);
        bar = (ProgressBar) findViewById(R.id.pbLoading);
        tip = (TextView) findViewById(R.id.tvPlayerTitle);

        final String url = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        String name = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_NAME);
        boolean live = getIntent() != null && getIntent().getBooleanExtra(EXTRA_LIVE, false);
        if (name != null && name.length() > 0) tip.setText(name);

        if (url == null || url.length() == 0) {
            Toast.makeText(this, R.string.shell_play_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        MediaController mc = new MediaController(this);
        mc.setAnchorView(vv);
        vv.setMediaController(mc);

        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                bar.setVisibility(View.GONE);
                vv.start();
            }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                bar.setVisibility(View.GONE);
                retries++;
                if (retries <= 2) {
                    // 直播流常见瞬断：自动重连两次
                    tip.setText(R.string.shell_reconnect);
                    try {
                        vv.resume();
                        bar.setVisibility(View.VISIBLE);
                        return true;
                    } catch (Throwable ignored) { }
                }
                tip.setText(R.string.shell_play_failed);
                Toast.makeText(PlayerActivity.this, R.string.shell_play_failed, Toast.LENGTH_LONG).show();
                return true;
            }
        });
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if (!live) finish();
            }
        });

        try {
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", "CDesk/1.0");
            vv.setVideoURI(Uri.parse(url), headers);
            bar.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.shell_play_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        try { if (vv != null) vv.stopPlayback(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
