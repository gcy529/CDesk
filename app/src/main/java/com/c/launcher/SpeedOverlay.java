package com.c.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * 实时网速悬浮窗：下载胶囊挂屏幕左下，上传胶囊挂屏幕右下。
 *
 * 两个胶囊是「两个独立窗口」，不是一个全宽窗口加间隔。
 * 这样每个胶囊都能按自己所在位置的壁纸亮度独立决定黑白，
 * 也不会因为窗口拉全宽而挡住桌面中间区域的点击。
 *
 * 兼容 Android 4.0 以上：
 *  - 窗口类型按版本分支（O 以上用 TYPE_APPLICATION_OVERLAY，否则 TYPE_PHONE）
 *  - FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE：完全不拦截触摸
 *  - 全部 View 用代码构建，不依赖布局资源，冷启动快
 */
public class SpeedOverlay {

    private final Context ctx;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** 全局复用一个实例，壁纸位图缓存才不会每秒重新解码 */
    private final AutoContrast contrast = new AutoContrast();

    private final Pill down = new Pill();
    private final Pill up = new Pill();

    private volatile boolean attached = false;
    private long lastContrastAt = 0L;
    /** 传输活跃标记：有流量时切珊瑚粉 */
    private boolean downActive = false;
    private boolean upActive = false;

    /** 一个胶囊：窗口 + 文字 + 当前配色状态 */
    private final class Pill {
        LinearLayout root;
        TextView text;
        WindowManager.LayoutParams lp;
        int style = -1;
        String suffix;

        void detach() {
            if (root == null) return;
            try { wm.removeView(root); } catch (Throwable ignored) { }
            root = null;
            text = null;
        }
    }

    public SpeedOverlay(Context context) {
        this.ctx = context.getApplicationContext();
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    /** 挂载两个胶囊，任一失败都返回 false（通常是没给悬浮窗权限） */
    public boolean attach() {
        if (attached) return true;
        try {
            attachPill(down, "\u2193 ", Gravity.BOTTOM | Gravity.START);
            attachPill(up, "\u2191 ", Gravity.BOTTOM | Gravity.END);
            attached = true;
            ui.post(new Runnable() {
                @Override public void run() { refreshContrast(true); }
            });
            return true;
        } catch (Throwable t) {
            detach();
            return false;
        }
    }

    private void attachPill(Pill p, String prefix, int gravity) {
        p.root = new LinearLayout(ctx);
        p.root.setOrientation(LinearLayout.HORIZONTAL);
        p.root.setGravity(Gravity.CENTER_VERTICAL);

        p.text = new TextView(ctx);
        p.text.setText(prefix + "0.00");
        p.text.setTextSize(12f);
        p.text.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        p.text.setGravity(Gravity.CENTER);
        p.text.setSingleLine(true);
        p.text.setPadding(dp(10), dp(4), dp(10), dp(4));
        p.text.setTextColor(Color.WHITE);
        p.root.addView(p.text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        int type;
        if (Build.VERSION.SDK_INT >= 26) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        p.lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.lp.gravity = gravity;
        p.lp.x = dp(14);
        p.lp.y = dp(14);

        wm.addView(p.root, p.lp);
    }

    /** 每秒由 SpeedService 回调，单位 Mbps */
    public void updateSpeed(final double dlMbps, final double ulMbps) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (!attached) return;
                setText(down, "\u2193 ", fmt(dlMbps));
                setText(up, "\u2191 ", fmt(ulMbps));
                boolean dAct = dlMbps >= 0.05d;
                boolean uAct = ulMbps >= 0.05d;
                boolean changed = (dAct != downActive) || (uAct != upActive);
                downActive = dAct;
                upActive = uAct;
                if (changed) refreshContrast(true);
                long now = System.currentTimeMillis();
                if (now - lastContrastAt > 2000L) {
                    lastContrastAt = now;
                    refreshContrast(false);
                }
            }
        });
    }

    private void setText(Pill p, String prefix, String value) {
        if (p.text == null) return;
        p.text.setText(prefix + value);
    }

    /** 撞色切换后重刷描边 */
    public void applyTheme() {
        ui.post(new Runnable() {
            @Override public void run() { refreshContrast(true); }
        });
    }

    /** 自动黑白：两个胶囊各按自己位置的壁纸亮度决定 */
    private void refreshContrast(boolean force) {
        if (!attached) return;
        int accent = ThemeManager.current(ctx).accent;
        applyPill(down, force, accent);
        applyPill(up, force, accent);
    }

    /**
     * 深海与微光规范：
     *  默认 —— 海盐灰文字 + 玻璃底，低调不抢视线
     *  传输中 —— 珊瑚粉文字 + 珊瑚粉描边
     * 不再按壁纸明暗切黑白：胶囊自带深色玻璃底，任何壁纸上都可读。
     */
    private void applyPill(Pill p, boolean force, int accent) {
        if (p.root == null || p.text == null) return;
        boolean active = (p == down) ? downActive : upActive;
        int want = active ? ThemeManager.ACCENT2 : ThemeManager.TEXT_SUB;
        if (!force && want == p.style) return;
        p.style = want;

        p.text.setTextColor(want);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(999));
        bg.setColor(ThemeManager.SURFACE);
        bg.setStroke(dp(1), active ? ThemeManager.ACCENT2 : 0x26FFFFFF);
        p.text.setBackgroundDrawable(bg);
    }

    public void detach() {
        down.detach();
        up.detach();
        attached = false;
    }

    public boolean isAttached() { return attached; }

    private String fmt(double v) {
        if (v >= 1000) return String.valueOf(Math.round(v));
        if (v >= 100) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.2f", v);
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
