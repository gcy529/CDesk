package com.c.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;

/**
 * 系统媒体监听服务：轮询系统活跃 MediaSession，把封面、标题、播放状态
 * 推给 MediaCardOverlay，并响应卡片上的播放/暂停、下一首控制。
 *
 * 这是「音乐加地图车机版画中画」方向的可行性 PoC：
 * 只监听系统 MediaSession，不绑定任何特定音乐应用，先确认车机系统
 * 是否允许 CDesk 读取媒体会话并绘制悬浮卡片。
 */
public class MediaCardService extends Service {

    public static final String ACTION_START = "com.c.launcher.action.MEDIA_CARD_START";
    public static final String ACTION_STOP = "com.c.launcher.action.MEDIA_CARD_STOP";

    private static final String CHANNEL_ID = "cdesk_mcard";
    private static final int NOTIFICATION_ID = 1003;
    private static final long POLL_MS = 1500L;

    private static MediaCardOverlay sOverlay;
    private static volatile boolean sRunning;

    public static boolean isRunning() {
        return sRunning;
    }

    private Handler handler;
    private Runnable poller;
    private MediaSessionManager sessionManager;
    private MediaController controller;
    private boolean running;

    public static MediaCardOverlay overlay() {
        return sOverlay;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            sRunning = true;
            createChannelIfNeeded();
            startForeground(NOTIFICATION_ID, buildNotification());
            attachOverlay();
            startPolling();
        }
        return START_STICKY;
    }

    private void attachOverlay() {
        if (sOverlay == null) {
            sOverlay = new MediaCardOverlay(this, new MediaCardOverlay.Listener() {
                @Override public void onTogglePlay() { togglePlay(); }
                @Override public void onNext() { skipToNext(); }
                @Override public void onClose() { stopSelf(); }
            });
        }
        if (!sOverlay.attach() && BuildConfig.DEBUG) {
            CLog.i("media card overlay attach failed (permission?)");
        }
    }

    private void startPolling() {
        if (Build.VERSION.SDK_INT < 21) return;
        sessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        poller = new Runnable() {
            @Override public void run() {
                if (!running) return;
                bindController();
                handler.postDelayed(this, POLL_MS);
            }
        };
        handler.post(poller);
    }

    /** 取当前活跃媒体会话，换一个会话就换控制器并重新拉元数据 */
    private void bindController() {
        if (sessionManager == null) return;
        MediaController next = activeController();
        if (next == null) {
            if (controller != null) {
                controller = null;
                sOverlay.update("", null, false);
            }
            return;
        }
        if (controller == next) return;
        controller = next;
        pushState();
    }

    private MediaController activeController() {
        try {
            List<MediaController> sessions = sessionManager.getActiveSessions(null);
            if (sessions == null || sessions.isEmpty()) return null;
            return sessions.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把当前会话的元数据和播放状态刷到卡片 */
    private void pushState() {
        if (controller == null || sOverlay == null) return;
        try {
            MediaMetadata md = controller.getMetadata();
            String title = "";
            Bitmap art = null;
            if (md != null) {
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null) title = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            boolean playing = false;
            PlaybackState st = controller.getPlaybackState();
            if (st != null) {
                int state = st.getState();
                playing = state == PlaybackState.STATE_PLAYING
                        || state == PlaybackState.STATE_BUFFERING;
            }
            if (title == null) title = "";
            sOverlay.update(title, art, playing);
        } catch (Throwable ignored) {
        }
    }

    private void togglePlay() {
        if (controller == null) return;
        try {
            if (isPlaying()) {
                controller.getTransportControls().pause();
            } else {
                controller.getTransportControls().play();
            }
        } catch (Throwable ignored) {
        }
    }

    private void skipToNext() {
        if (controller == null) return;
        try {
            controller.getTransportControls().skipToNext();
        } catch (Throwable ignored) {
        }
    }

    private boolean isPlaying() {
        try {
            PlaybackState st = controller.getPlaybackState();
            if (st == null) return false;
            int state = st.getState();
            return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING;
        } catch (Throwable t) {
            return false;
        }
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= 23) {
            pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pi = PendingIntent.getActivity(this, 0, open, 0);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(getString(R.string.app_name));
        b.setContentText(getString(R.string.notification_media_card));
        b.setSmallIcon(android.R.drawable.ic_media_play);
        b.setContentIntent(pi);
        b.setOngoing(true);
        return b.build();
    }

    @Override
    public void onDestroy() {
        running = false;
        sRunning = false;
        if (handler != null && poller != null) handler.removeCallbacks(poller);
        controller = null;
        if (sOverlay != null) {
            sOverlay.hide();
            sOverlay = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
