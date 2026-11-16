package com.c.launcher;

import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * 焦点/触摸统一的视觉状态机。
 *
 * 为什么要有这个类：
 *  遥控器（焦点）和手指（触摸）走的是两套事件，之前各自画各自的，
 *  视觉不一致还互相打架。现在规定：无论是 onFocusChange 还是 onTouch，
 *  都只准调用 setFocusState(View, boolean) 这一个入口，
 *  放大量、时长、描边、文字色全部在这里定义 —— 改一处全局生效。
 *
 * 兼容性：minSdk 16 (Android 4.1)
 *  - View.animate() 从 API 12 起可用 ✓
 *  - ViewPropertyAnimator.setDuration/setInterpolator API 12+ ✓
 *  - setSelected(int) API 1+ ✓
 *  - 没用 elevation / outline / stateListAnimator（都是 API 21+）✓
 */
public final class FocusAnimator {

    private FocusAnimator() { }

    /** 放大倍数：与老版本一致，视觉无跳变 */
    public static final float SCALE_ON = 1.1f;
    public static final float SCALE_OFF = 1f;

    /** 当前壁纸是否偏浅（决定文字色）。MainActivity.applyPalette 里按壁纸写入 */
    public static volatile boolean lightBg = false;

    /**
     * 唯一的状态入口。
     *
     * @param v        卡片根布局（item_app / item_media 的根 LinearLayout）
     * @param selected true = 获得焦点 或 手指按住；false = 失去焦点/抬起
     */
    public static void setFocusState(View v, boolean selected) {
        if (v == null) return;
        // 1) 放大动画（回弹式，与老版本手感一致）
        float target = selected ? SCALE_ON : SCALE_OFF;
        v.animate().scaleX(target).scaleY(target)
                .setDuration(150)
                .setInterpolator(new OvershootInterpolator(selected ? 1.2f : 0.6f))
                .start();
        // 2) 描边高亮：bg_focus.xml 是 state-list drawable，
        //    state_selected 优先级排在 state_pressed 之后、默认态之前 ——
        //    遥控器焦点用 state_selected 触发同样的蓝框，触摸按下用 state_pressed。
        //    （见 item_app.xml 配套的 selector 状态扩充）
        v.setSelected(selected);
        // 3) 文字色跟随（只认挂在我们 tag 上的标签，找不到就跳过）
        Object tag = v.getTag(R.id.tagAppName);
        if (tag instanceof TextView) {
            ((TextView) tag).setTextColor(selected
                    ? (lightBg ? 0xFF3B4CFF : ThemeManager.PRIMARY)
                    : (lightBg ? 0xFF5A6474 : ThemeManager.TEXT_SUB));
        }
    }
}
