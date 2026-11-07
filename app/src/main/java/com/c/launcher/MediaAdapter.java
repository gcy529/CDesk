package com.c.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 影音页的两个大卡片：影视 / 音乐。
 * 这是内置壳（CPlay / C听）的入口，跟用户装了什么应用无关，
 * 所以界面上永远只会出现这四个字，不存在第三方播放器名字被扫的风险。
 */
public class MediaAdapter extends BaseAdapter {

    private static final String[] TYPES = new String[] { "live", "video", "audio" };
    /** 卡片：深色玻璃实底 + 彩色描边。之前用 20% 透明色块，在壁纸上几乎隐形（真 bug） */
    private static final int FILL = 0xF2161C2C;
    private static final int[] STROKES = new int[] { 0xFFFF7E9D, 0xFF4D6BFE, 0xFF2DD4BF };
    private static final int[] LABELS = new int[] { R.string.live_title, R.string.vod_title, R.string.music_title };

    private final Context ctx;

    public MediaAdapter(Context c) { ctx = c; }

    public String typeAt(int pos) { return TYPES[pos % TYPES.length]; }

    @Override public int getCount() { return TYPES.length; }
    @Override public Object getItem(int position) { return typeAt(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_media, parent, false);
        }
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View vv, boolean has) {
                float s = has ? 1.1f : 1f;
                vv.animate().scaleX(s).scaleY(s)
                        .setDuration(150)
                        .setInterpolator(new OvershootInterpolator(has ? 1.2f : 0.6f))
                        .start();
            }
        });
        ImageView iv = (ImageView) v.findViewById(R.id.ivMediaIcon);
        TextView tv = (TextView) v.findViewById(R.id.tvMediaName);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(FILL);
        g.setStroke(dp(2), STROKES[position % STROKES.length]);
        if (iv != null) iv.setImageDrawable(g);
        if (tv != null) {
            tv.setText(LABELS[position % LABELS.length]);
            tv.setTextColor(ThemeManager.TEXT_MAIN);
        }
        return v;
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
