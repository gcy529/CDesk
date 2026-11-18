package com.c.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 手机遥控页：显示地址与二维码，手机扫码后就是遥控器。
 *
 * 服务是前台服务，退出本页不会停；想关掉时点「停止遥控」。
 * 进入本页会自动启动，少一步操作。
 */
public class RemoteActivity extends Activity {

    private TextView tvStatus;
    private TextView tvAddr;
    private Button btnToggle;
    private ImageView ivQr;
    private TextView tvQrHint;
    private CheckBox ckBoot;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean attached;
    private String lastQrUrl;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            render();
            ui.postDelayed(this, 800L);
        }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.apply(this);
        setContentView(R.layout.activity_remote);

        View rootBg = findViewById(R.id.rootRemote);
        WPManager.applyToRoot(this, rootBg);

        tvStatus = (TextView) findViewById(R.id.tvRemoteStatus);
        tvAddr = (TextView) findViewById(R.id.tvRemoteAddr);
        btnToggle = (Button) findViewById(R.id.btnRemoteToggle);
        ivQr = (ImageView) findViewById(R.id.ivRemoteQr);
        tvQrHint = (TextView) findViewById(R.id.tvRemoteQrHint);
        ckBoot = (CheckBox) findViewById(R.id.ckRemoteBoot);

        final SharedPreferences sp = getSharedPreferences("cdesk_remote", MODE_PRIVATE);
        ckBoot.setChecked(sp.getBoolean("auto_start", true));
        ckBoot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = ((CheckBox) v).isChecked();
                sp.edit().putBoolean("auto_start", on).apply();
                CLog.i("remote auto-start -> " + on);
            }
        });

        findViewById(R.id.btnRemoteBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (RemoteService.isRunning()) RemoteService.stop(RemoteActivity.this);
                else RemoteService.start(RemoteActivity.this);
                tvStatus.setText(R.string.remote_starting);
                ui.postDelayed(new Runnable() {
                    @Override public void run() { render(); }
                }, 600L);
            }
        });

        // 进入即启动，扫码就能用
        if (!RemoteService.isRunning()) RemoteService.start(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        attached = true;
        ui.post(poll);
    }

    @Override
    protected void onPause() {
        attached = false;
        ui.removeCallbacks(poll);
        super.onPause();
    }

    private void render() {
        if (isFinishing()) return;
        boolean running = RemoteService.isRunning();
        if (running) {
            String url = RemoteService.url();
            String token = RemoteService.token();
            String full = url + "/?k=" + token;
            tvStatus.setText(R.string.remote_running);
            tvAddr.setText(full);
            btnToggle.setText(R.string.remote_stop);
            if (!full.equals(lastQrUrl)) {
                showQr(full);
                lastQrUrl = full;
            }
            ivQr.setVisibility(View.VISIBLE);
            tvQrHint.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setText(R.string.remote_idle);
            tvAddr.setText("");
            btnToggle.setText(R.string.remote_start);
            ivQr.setVisibility(View.GONE);
            tvQrHint.setVisibility(View.GONE);
            lastQrUrl = null;
        }
    }

    private void showQr(String url) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints =
                    new java.util.HashMap<com.google.zxing.EncodeHintType, Object>();
            hints.put(com.google.zxing.EncodeHintType.MARGIN, Integer.valueOf(1));
            com.google.zxing.common.BitMatrix mx =
                    w.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512, hints);
            int s = mx.getWidth();
            int[] px = new int[s * s];
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    px[y * s + x] = mx.get(x, y) ? 0xFF11131B : 0xFFFFFFFF;
                }
            }
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    s, s, android.graphics.Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, s, 0, 0, s, s);
            ivQr.setImageBitmap(bmp);
        } catch (Throwable ignored) { }
    }
}
