package com.c.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 系统媒体悬浮卡片：监听系统 MediaSession，在桌面顶部显示封面、标题和播放控制。
 * 仅作为画中画方向的可行性 PoC，不绑定特定音乐应用。
 */
public class MediaCardOverlay {

    public interface Listener {
        void onTogglePlay();
        void onNext();
        void onClose();
    }

    private final Context ctx;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private LinearLayout root;
    private ImageView ivArt;
    private TextView tvTitle;
    private TextView btnPlay;
    private TextView btnNext;
    private TextView btnClose;
    private WindowManager.LayoutParams lp;

    private volatile boolean attached = false;
    private String title = "";
    private Bitmap art;
    private boolean playing;

    public MediaCardOverlay(Context context, Listener l) {
        this.ctx = context.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.listener = l;
    }

    public boolean attach() {
        if (attached) return true;
        try {
            root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(ThemeManager.SURFACE);
            bg.setStroke(dp(1), ThemeManager.TEXT_SUB);
            root.setBackgroundDrawable(bg);

            ivArt = new ImageView(ctx);
            ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable artBg = new GradientDrawable();
            artBg.setCornerRadius(dp(8));
            artBg.setColor(ThemeManager.BG_DEEP);
            ivArt.setBackgroundDrawable(artBg);
            LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(44), dp(44));
            artLp.rightMargin = dp(12);
            root.addView(ivArt, artLp);

            tvTitle = new TextView(ctx);
            tvTitle.setTextSize(15f);
            tvTitle.setMaxLines(1);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvTitle.setTextColor(ThemeManager.TEXT_MAIN);
            root.addView(tvTitle, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            btnPlay = new TextView(ctx);
            btnPlay.setTextSize(20f);
            btnPlay.setGravity(Gravity.CENTER);
            btnPlay.setPadding(dp(14), 0, dp(8), 0);
            btnPlay.setTextColor(ThemeManager.TEXT_MAIN);
            GradientDrawable playBg = new GradientDrawable();
            playBg.setCornerRadius(dp(999));
            playBg.setColor(ThemeManager.PRIMARY);
            btnPlay.setBackgroundDrawable(playBg);
            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (listener != null) listener.onTogglePlay(); }
            });
            root.addView(btnPlay, new LinearLayout.LayoutParams(dp(52), dp(44)));

            btnNext = new TextView(ctx);
            btnNext.setTextSize(18f);
            btnNext.setGravity(Gravity.CENTER);
            btnNext.setPadding(dp(10), 0, dp(8), 0);
            btnNext.setTextColor(ThemeManager.TEXT_MAIN);
            GradientDrawable nextBg = new GradientDrawable();
            nextBg.setCornerRadius(dp(999));
            nextBg.setColor(0x26FFFFFF);
            btnNext.setBackgroundDrawable(nextBg);
            btnNext.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (listener != null) listener.onNext(); }
            });
            root.addView(btnNext, new LinearLayout.LayoutParams(dp(44), dp(44)));

            btnClose = new TextView(ctx);
            btnClose.setTextSize(18f);
            btnClose.setGravity(Gravity.CENTER);
            btnClose.setPadding(dp(8), 0, dp(10), 0);
            btnClose.setTextColor(ThemeManager.TEXT_SUB);
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (listener != null) listener.onClose(); }
            });
            root.addView(btnClose, new LinearLayout.LayoutParams(dp(40), dp(44)));

            int type;
            if (Build.VERSION.SDK_INT >= 26) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = dp(16);
            lp.y = dp(72);
            wm.addView(root, lp);
            attached = true;
            refresh();
            return true;
        } catch (Throwable t) {
            hide();
            return false;
        }
    }

    public void update(String t, Bitmap a, boolean isPlaying) {
        title = t == null ? "" : t;
        art = a;
        playing = isPlaying;
        ui.post(new Runnable() {
            @Override public void run() { refresh(); }
        });
    }

    public void hide() {
        if (root != null) {
            try { wm.removeView(root); } catch (Throwable ignored) { }
            root = null;
        }
        attached = false;
    }

    public boolean isAttached() {
        return attached;
    }

    private void refresh() {
        if (!attached || root == null) return;
        if (tvTitle != null) {
            tvTitle.setText(title.length() == 0 ? "" : title);
        }
        if (ivArt != null) {
            if (art != null) ivArt.setImageBitmap(art);
            else ivArt.setImageDrawable(null);
        }
        if (btnPlay != null) btnPlay.setText(playing ? "❙❙" : "▶");
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
