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
    public int columns() { return 4; }

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
        // 只画图形本身，颜色按类型区分；卡片底板交给 bg_media_card，
        // 不再在 bitmap 里再画一个深色圆角块（那样是"方块套方块"，远看四块黑板）。
        int side = Screen.iconSide(ctx);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        int type = position % TYPES.length;
        paint.setColor(STROKES[type]);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        float cx = side / 2f, cy = side / 2f, r = side * 0.27f;
        if (type == 0) {
            // 直播：播放三角
            android.graphics.Path tri = new android.graphics.Path();
            tri.moveTo(cx - r * 0.55f, cy - r * 0.85f);
            tri.lineTo(cx - r * 0.55f, cy + r * 0.85f);
            tri.lineTo(cx + r * 0.95f, cy);
            tri.close();
            cv.drawPath(tri, paint);
        } else if (type == 1) {
            // 点播：屏幕 + 里面的播放三角（原来画两根竖条，远看就是"暂停"，难怪别扭）
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            android.graphics.RectF screen = new android.graphics.RectF(cx - r, cy - r * 0.72f, cx + r, cy + r * 0.72f);
            cv.drawRoundRect(screen, dp(6), dp(6), paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            android.graphics.Path t2 = new android.graphics.Path();
            t2.moveTo(cx - r * 0.22f, cy - r * 0.34f);
            t2.lineTo(cx - r * 0.22f, cy + r * 0.34f);
            t2.lineTo(cx + r * 0.4f, cy);
            t2.close();
            cv.drawPath(t2, paint);
        } else if (type == 2) {
            // 音乐：音符
            cv.drawCircle(cx - r * 0.45f, cy + r * 0.45f, r * 0.42f, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            cv.drawLine(cx - r * 0.45f, cy + r * 0.45f, cx - r * 0.45f, cy - r * 0.75f, paint);
            cv.drawLine(cx - r * 0.45f, cy - r * 0.75f, cx + r * 0.55f, cy - r * 0.45f, paint);
        } else {
            // 书库：三本书脊
            paint.setStyle(android.graphics.Paint.Style.FILL);
            for (int k = -1; k <= 1; k++) {
                float x0 = cx + k * r * 0.62f;
                cv.drawRoundRect(new android.graphics.RectF(x0 - r * 0.16f, cy - r * 0.72f, x0 + r * 0.16f, cy + r * 0.72f), dp(3), dp(3), paint);
            }
        }
        if (iv != null) {
            iv.setImageBitmap(bmp);
            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            if (lp != null && (lp.width != side || lp.height != side)) {
                lp.width = side;
                lp.height = side;
                iv.setLayoutParams(lp);
            }
        }
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
