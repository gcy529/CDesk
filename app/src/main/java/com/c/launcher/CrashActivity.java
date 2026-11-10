package com.c.launcher;

import android.app.Activity;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 崩溃兜底页：捕获未处理异常后显示，带复制按钮 */
public class CrashActivity extends Activity {

    public static final String EXTRA_MSG = "msg";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String msg = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_MSG);
        final String show = msg == null || msg.length() == 0 ? getString(R.string.crash_no_msg) : msg;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1D23);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText(R.string.crash_title);
        title.setTextSize(24);
        title.setTextColor(0xFFF8FAFC);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.crash_hint);
        hint.setTextSize(14);
        hint.setTextColor(0xFF8E9BAE);
        hint.setPadding(0, 12, 0, 12);
        root.addView(hint);

        ScrollView sv = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(show);
        body.setTextSize(13);
        body.setTextColor(0xFFCBD5E1);
        body.setPadding(16, 16, 16, 16);
        body.setBackgroundColor(0xFF0B0F19);
        sv.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(sv, lp);

        TextView copy = new TextView(this);
        copy.setText(R.string.crash_copy);
        copy.setTextSize(18);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(20, 20, 20, 20);
        copy.setBackgroundColor(0xFF4D6BFE);
        copy.setTextColor(0xFFFFFFFF);
        copy.setFocusable(true);
        copy.setClickable(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = 24;
        root.addView(copy, bp);

        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setText(show);
                    Toast.makeText(CrashActivity.this, R.string.crash_copied, Toast.LENGTH_SHORT).show();
                } catch (Throwable t) { }
            }
        });

        setContentView(root);

        // 崩溃页只停留 6 秒：够看清、够截图，又不至于一直占着屏幕。
        // 到点先 finish 再结束进程，避免把这个坏掉的进程留在后台。
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try { finish(); } catch (Throwable ignored) { }
                try { android.os.Process.killProcess(android.os.Process.myPid()); } catch (Throwable ignored) { }
            }
        }, 6000L);
    }
}