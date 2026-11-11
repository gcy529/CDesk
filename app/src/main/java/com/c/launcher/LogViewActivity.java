package com.c.launcher;

import android.app.Activity;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 整页日志查看：滚动的正文 + 复制 + 返回。
 * 取代原来「复制到剪贴板然后弹一个 2 秒 Toast」的做法 —— 那种根本没法截图。
 */
public class LogViewActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        CLog.i("LogViewActivity onCreate");
        Screen.apply(this);

        ThemeManager.Palette p = ThemeManager.current(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bgTop);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.settings_log);
        title.setTextSize(26f);
        title.setTextColor(p.isLight ? p.fg : p.accent);
        root.addView(title);

        final String report = CLog.report();

        ScrollView sv = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(report);
        body.setTextSize(13f);
        body.setTextColor(p.fg);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.setTextIsSelectable(true);
        sv.addView(body);
        // 倒序显示：最新的在最后。滚到底（或点复制）看到的就是最近发生的事
        sv.post(new Runnable() {
            @Override public void run() {
                sv.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(16);
        root.addView(sv, slp);

        Button copy = new Button(this);
        copy.setText(R.string.crash_copy);
        copy.setAllCaps(false);
        ThemeManager.styleButton(copy, p);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setText(CLog.report());
                    Toast.makeText(LogViewActivity.this, R.string.crash_copied, Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Toast.makeText(LogViewActivity.this, R.string.settings_log_failed, Toast.LENGTH_LONG).show();
                }
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        blp.topMargin = dp(16);
        root.addView(copy, blp);

        Button back = new Button(this);
        back.setText(R.string.transfer_back);
        back.setAllCaps(false);
        ThemeManager.styleButton(back, p);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        blp2.topMargin = dp(10);
        root.addView(back, blp2);

        setContentView(root);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
