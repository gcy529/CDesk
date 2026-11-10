package com.c.launcher;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;

/**
 * 播放过程中的一次性统计，用来区分「网络不够」和「机器解不动」：
 *   drop      丢帧数，涨得快 = 解不过来（硬解不支持这个编码，或者 CPU 顶不住）
 *   underrun  音频欠载，涨 = 送数据不及时（网络抖动 / 读盘慢）
 *   kbps      实际码率，跟带宽对得上就知道是不是带不动
 *   buf       缓冲百分比，掉到很低基本就是网络问题
 * 输出全英文，因为会显示在播放页上。
 */
public final class PlayStats implements AnalyticsListener {

    private long dropped;
    private long underruns;

    @Override public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        dropped += droppedFrames;
    }

    @Override public void onAudioUnderrun(EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        underruns++;
    }

    public long dropped() { return dropped; }

    public String line(Player p) {
        String res = "-";
        String codec = "-";
        long kbps = 0;
        int buf = 0;
        try {
            Format f = p.getVideoFormat();
            if (f != null) {
                res = f.width + "x" + f.height;
                if (f.codecs != null) { codec = f.codecs; }
                kbps = f.bitrate / 1000L;
            }
            buf = p.getBufferedPercentage();
        } catch (Throwable ignored) {
        }
        return "drop " + dropped + " | under " + underruns + " | " + res + " " + codec
                + " " + kbps + "kbps | buf " + buf + "%";
    }
}
