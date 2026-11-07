package com.c.launcher;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 影视 / 音乐壳子的占位页。
 * 壳子本体（ExoPlayer 播放器 + 自动换源）还没做完之前，
 * 点影音页的卡片先落到这里，给用户一个明确的交代，而不是没反应。
 */
public class ShellPlaceholderActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String type = getIntent() == null ? null : getIntent().getStringExtra("type");
        boolean music = "audio".equals(type);
        ThemeManager.Palette p = ThemeManager.current(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(p.bgTop);
        root.setPadding(dp(40), dp(40), dp(40), dp(40));

        View icon = new View(this);
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(music ? 0xFF16A34A : 0xFFDC2626);
        icon.setBackgroundDrawable(ig);
        root.addView(icon, new LinearLayout.LayoutParams(dp(110), dp(110)));

        TextView t = new TextView(this);
        t.setText(music ? R.string.media_music : R.string.media_play);
        t.setTextSize(26f);
        t.setTextColor(p.fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(18), 0, 0);
        root.addView(t);

        TextView s = new TextView(this);
        s.setText(R.string.media_soon);
        s.setTextSize(14f);
        s.setTextColor(p.dim);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(10), 0, 0);
        root.addView(s);

        Button back = new Button(this);
        back.setText(R.string.transfer_back);
        back.setTextSize(14f);
        ThemeManager.styleButton(back, p);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(180), dp(48));
        blp.topMargin = dp(30);
        root.addView(back, blp);

        setContentView(root);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
