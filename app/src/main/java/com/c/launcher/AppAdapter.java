package com.c.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 已安装应用列表适配器。
 *
 * 兼容 Android 4.0 ~ 最新版本：
 *  - 用 PackageManager.queryIntentActivities 拿 LAUNCHER 应用，无需 QUERY_ALL_PACKAGES
 *  - Android 11+ 需在 manifest 里声明 <queries> 才能看全，已在 manifest 中处理
 *  - 图标加载做了空判，防止某些车机应用 getActivityIcon 抛 NPE
 */
public class AppAdapter extends BaseAdapter {

    public static class AppItem {
        public final String label;
        public final String pkg;
        public final String cls;
        public final Drawable icon;
        public AppItem(String label, String pkg, String cls, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
        }
    }

    /**
     * 触摸点击回调。
     * item 根布局是 focusable + focusableInTouchMode，手指点下去 item 会抢焦点，
     * 此时 GridView.onItemClick 不会触发 —— 必须在 item 上自己挂 OnClickListener。
     * 遥控器走 GridView 的按键路径不受影响。
     */
    public interface OnCardClick {
        void onAppClick(int position);
    }

    private final Context ctx;
    private final List<AppItem> items = new ArrayList<AppItem>();
    private final List<AppItem> all = new ArrayList<AppItem>();
    /** 浅色壁纸（日光少女/晨光）下图标名换深灰，海盐灰在奶油底上看不清 */
    private volatile boolean lightBg = false;
    private OnCardClick cardClick;

    public void setOnCardClick(OnCardClick l) { this.cardClick = l; }

    public AppAdapter(Context context) {
        this.ctx = context;
        reload();
    }

    public void reload() {
        all.clear();
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
            // 注意：这里刻意不跳过自己。
            // 有些车机固件锁死了 Home 键，用户无法把第三方桌面设为默认，
            // 唯一能唤出系统「选择桌面」窗口的办法，就是从应用列表里点自己那一下。
            for (ResolveInfo ri : apps) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                Drawable icon = null;
                try { icon = ri.activityInfo.loadIcon(pm); } catch (Throwable ignored) { }
                if (icon == null) icon = pm.getDefaultActivityIcon();
                String label;
                try { label = ri.loadLabel(pm).toString(); } catch (Throwable th) { label = pkg; }
                all.add(new AppItem(label, pkg, ri.activityInfo.name, icon));
            }
            Collections.sort(all, new Comparator<AppItem>() {
                @Override public int compare(AppItem a, AppItem b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } catch (Throwable ignored) { }
        items.clear();
        items.addAll(all);
    }

    /** 全部应用（不分页），分页逻辑在 MainActivity 里按需筛选 */
    public List<AppItem> allItems() {
        return new ArrayList<AppItem>(all);
    }

    /** 壁纸明暗切换时由 MainActivity 调用：浅底 = 图标名用深灰 */
    public void setLightBg(boolean light) {
        if (this.lightBg == light) return;
        this.lightBg = light;
        notifyDataSetChanged();
    }

    /** 当前底色是否偏浅（决定图标名颜色） */
    public boolean isLightBg() { return lightBg; }

    /** 切页：换掉当前展示的数据 */
    public void setItems(List<AppItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public AppItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_app, parent, false);
        }
        AppItem it = getItem(position);
        ImageView iv = (ImageView) v.findViewById(R.id.ivIcon);
        final TextView tv = (TextView) v.findViewById(R.id.tvName);
        if (iv != null) iv.setImageDrawable(it.icon);
        if (tv != null) {
            tv.setText(it.label);
            tv.setTextColor(lightBg ? 0xFF5A6474 : ThemeManager.TEXT_SUB);
        }
        // 文字色交给 FocusAnimator 统一管：把 tv 挂到 tag 上
        v.setTag(R.id.tagAppName, tv);
        v.setScaleX(1f);
        v.setScaleY(1f);
        // 双模统一入口：焦点变化 → setFocusState；触摸按下/抬起 → 同一个方法
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View vv, boolean has) {
                FocusAnimator.setFocusState(vv, has);
            }
        });
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View vv, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        // 手指按下：立即放大+描边（视觉与遥控器焦点一致）
                        FocusAnimator.setFocusState(vv, true);
                        return false;   // 返回 false = 不吞事件，点击继续走 onItemClick
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 抬起后保持选中态一瞬再回落（GridView 的 itemClick 紧跟着触发，
                        // 界面马上要跳页了，这里不复位也不影响观感；真正复位发生在
                        // 下一次焦点变化或页切换时）
                        vv.postDelayed(new Runnable() {
                            @Override public void run() {
                                FocusAnimator.setFocusState(vv, vv.isFocused());
                            }
                        }, 180);
                        return false;
                }
                return false;
            }
        });

        final int pos = position;
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View vv) {
                CLog.i("app card click pos=" + pos);
                if (cardClick != null) cardClick.onAppClick(pos);
            }
        });
        return v;
    }
}
