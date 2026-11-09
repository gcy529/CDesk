package com.c.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Foreground service: samples TrafficStats and pushes speeds to SpeedOverlay.
 *
 * Compatibility notes:
 *  - Android 8.0+ requires startForegroundService + startForeground, done here.
 *  - Android 8.0+ requires a notification channel, created on demand.
 *  - Android 4.x has no channel API and no foregroundServiceType.
 *  - Android 12+ forbids starting a foreground service from the background,
 *    so the service is only ever started explicitly by the activity.
 *  - The manifest declares foregroundServiceType="specialUse" so newer
 *    releases keep the same behaviour without code changes.
 */
public class SpeedService extends Service {

    public static final String ACTION_START = "com.c.launcher.action.START";
    public static final String ACTION_STOP = "com.c.launcher.action.STOP";

    private static final String CHANNEL_ID = "cdesk_speed";
    private static final int NOTIFICATION_ID = 1001;
    private static final long SAMPLE_MS = 1000L;

    private static SpeedOverlay sOverlay;

    private Handler handler;
    private Runnable ticker;
    private long lastRx;
    private long lastTx;
    private long lastAt;
    private boolean running;

    public static SpeedOverlay overlay() {
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
            createChannelIfNeeded();
            startForeground(NOTIFICATION_ID, buildNotification());
            startTicker();
        }
        return START_STICKY;
    }

    private void startTicker() {
        if (sOverlay == null) {
            sOverlay = new SpeedOverlay(this);
            if (!sOverlay.attach() && BuildConfig.DEBUG) {
                android.util.Log.d("CDesk", "overlay attach failed (permission?)");
            }
        } else {
            sOverlay.applyTheme();
        }

        lastRx = TrafficStats.getTotalRxBytes();
        lastTx = TrafficStats.getTotalTxBytes();
        lastAt = System.currentTimeMillis();

        ticker = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                long rx = TrafficStats.getTotalRxBytes();
                long tx = TrafficStats.getTotalTxBytes();
                long at = System.currentTimeMillis();
                long dt = at - lastAt;
                if (dt > 0 && rx >= 0 && tx >= 0) {
                    double dl = ((rx - lastRx) * 8.0 / 1000000.0) / (dt / 1000.0);
                    double ul = ((tx - lastTx) * 8.0 / 1000000.0) / (dt / 1000.0);
                    if (dl < 0) dl = 0;
                    if (ul < 0) ul = 0;
                    if (sOverlay != null) sOverlay.updateSpeed(dl, ul);
                }
                lastRx = rx;
                lastTx = tx;
                lastAt = at;
                handler.postDelayed(this, SAMPLE_MS);
            }
        };
        handler.post(ticker);
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
            // targetSdk>=31 时 Android 12 强制要求显式声明可变性，缺了直接抛 IllegalArgumentException
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
        b.setContentText(getString(R.string.notification_running));
        b.setSmallIcon(android.R.drawable.stat_sys_download);
        b.setContentIntent(pi);
        b.setOngoing(true);
        return b.build();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        if (sOverlay != null) {
            sOverlay.detach();
            sOverlay = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
