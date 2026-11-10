package com.c.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 影音页入口卡片：直播 / 点播 / 音乐 / 书库。
 * 这是内置壳的入口，跟用户装了什么应用无关，卡片数量固定，
 * 不存在第三方播放器名字被扫进来的风险。
 */
public class MediaAdapter extends BaseAdapter {

    private static final String[] TYPES = new String[] { "live", "video", "audio", "library" };
    /** 卡片：深色玻璃实底 + 彩色描边。之前用 20% 透明色块，在壁纸上几乎隐形（真 bug） */
    private static final int FILL = 0xF2161C2C;
    private static final int[] STROKES = new int[] { 0xFFFF7E9D, 0xFF4D6BFE, 0xFF2DD4BF, 0xFFF59E0B };
    private static final int[] LABELS = new int[] { R.string.live_title, R.string.vod_title, R.string.music_title, R.string.library_title };

    private final Context ctx;

    /** 卡片点击回调：卡片自己是可点的，点完直接回调，不依赖 GridView 的 onItemClick。
     *  原因：item_media 是 focusable 的，DPAD/触摸的确认事件会落在卡片上，
     *  GridView 收不到，onItemClick 永远不触发 —— 这就是「点了没反应」。 */
    public interface OnCardClick {
        void onCardClick(int position, String type);
    }

    private OnCardClick cardClick;

    public MediaAdapter(Context c) { ctx = c; }

    public void setOnCardClick(OnCardClick l) { cardClick = l; }

    public String typeAt(int pos) { return TYPES[pos % TYPES.length]; }

    /** 网格列数：MainActivity 的 DPAD 边界判断和 setNumColumns 都读这里，不许两处各写一个字面量 */
    public int columns() { return 3; }

    @Override public int getCount() { return TYPES.length; }
    @Override public Object getItem(int position) { return typeAt(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_media, parent, false);
        }
        final int pos = position;
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View vv) {
                CLog.i("media card click pos=" + pos + " type=" + typeAt(pos));
                if (cardClick != null) cardClick.onCardClick(pos, typeAt(pos));
            }
        });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View vv, boolean has) {
                FocusAnimator.setFocusState(vv, has);
            }
        });
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View vv, MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    FocusAnimator.setFocusState(vv, true);
                } else if (ev.getActionMasked() == MotionEvent.ACTION_UP
                        || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    vv.postDelayed(new Runnable() {
                        @Override public void run() {
                            FocusAnimator.setFocusState(vv, vv.isFocused());
                        }
                    }, 180);
                }
                return false;
            }
        });
        ImageView iv = (ImageView) v.findViewById(R.id.ivMediaIcon);
        TextView tv = (TextView) v.findViewById(R.id.tvMediaName);
        // 画真正的图标：圆角色块 + 白色图形（播放/音符/胶片/书本）
        int[] colors = STROKES;
        int ci = position % colors.length;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(dp(96), dp(96), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        // 底色圆角块
        paint.setColor(0xF2161C2C);
        android.graphics.RectF rf = new android.graphics.RectF(0, 0, dp(96), dp(96));
        cv.drawRoundRect(rf, dp(24), dp(24), paint);
        // 描边
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(colors[ci]);
        cv.drawRoundRect(rf, dp(24), dp(24), paint);
        // 白色图形
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        int type = position % TYPES.length;
        float cx = dp(48), cy = dp(48), r = dp(18);
        if (type == 0) {
            // 直播：播放三角
            android.graphics.Path tri = new android.graphics.Path();
            tri.moveTo(cx - r * 0.5f, cy - r * 0.8f);
            tri.lineTo(cx - r * 0.5f, cy + r * 0.8f);
            tri.lineTo(cx + r * 0.9f, cy);
            tri.close();
            cv.drawPath(tri, paint);
        } else if (type == 1) {
            // 影视：胶片（两个方块+中间竖线）
            cv.drawRect(cx - r, cy - r * 0.7f, cx - r * 0.2f, cy + r * 0.7f, paint);
            cv.drawRect(cx + r * 0.2f, cy - r * 0.7f, cx + r, cy + r * 0.7f, paint);
        } else if (type == 2) {
            // 音乐：音符（圆+竖线）
            cv.drawCircle(cx - r * 0.4f, cy + r * 0.5f, r * 0.45f, paint);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            cv.drawLine(cx - r * 0.4f, cy + r * 0.5f, cx - r * 0.4f, cy - r * 0.8f, paint);
            cv.drawLine(cx - r * 0.4f, cy - r * 0.8f, cx + r * 0.6f, cy - r * 0.5f, paint);
        } else {
            // 书库：书本（两个竖矩形）
            cv.drawRect(cx - r * 0.9f, cy - r * 0.6f, cx - r * 0.1f, cy + r * 0.6f, paint);
            cv.drawRect(cx + r * 0.1f, cy - r * 0.6f, cx + r * 0.9f, cy + r * 0.6f, paint);
        }
        if (iv != null) iv.setImageBitmap(bmp);
        if (tv != null) {
            tv.setText(LABELS[position % LABELS.length]);
            tv.setTextColor(ThemeManager.TEXT_MAIN);
            v.setTag(R.id.tagAppName, tv);
        }
        return v;
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
