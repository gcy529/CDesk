package com.c.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

/**
 * 手机遥控服务：局域网里开一个小 HTTP 服务，手机扫码打开网页就是遥控器。
 *
 * 为什么做成前台服务而不是页面：遥控要在「桌面正在使用时」一直有效，
 * 不能要求用户停在遥控页上。服务随桌面存活，关掉桌面才停。
 *
 * 协议（全部要求令牌，避免同一 Wi-Fi 下被人乱按）：
 *   GET /?k=TOKEN            遥控页面 remote.html
 *   GET /key?k=TOKEN&c=UP    派发一个按键，返回 {"ok":true}
 *   GET /ping?k=TOKEN        连通性检查
 *
 * 纯 ServerSocket + 守护线程，Android 4.1 起可用，不引第三方库。
 */
public class RemoteService extends Service {

    public static final String ACTION_START = "com.c.launcher.action.REMOTE_START";
    public static final String ACTION_STOP = "com.c.launcher.action.REMOTE_STOP";

    private static final int[] PORTS = new int[] { 8090, 8091, 8098, 9091, 9099 };
    private static final String CHANNEL_ID = "cdesk_remote";
    private static final int NOTIFICATION_ID = 1002;
    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static volatile boolean sRunning = false;
    private static volatile String sUrl = "";
    private static volatile String sToken = "";

    private ServerSocket server;
    private volatile boolean running = false;

    public static boolean isRunning() { return sRunning; }
    public static String url() { return sUrl; }
    public static String token() { return sToken; }

    /** 统一入口：Android 8+ 必须用 startForegroundService，否则会抛异常 */
    public static void start(Context ctx) {
        Intent i = new Intent(ctx, RemoteService.class);
        i.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, RemoteService.class);
        i.setAction(ACTION_STOP);
        try { ctx.startService(i); } catch (Throwable ignored) { }
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
            startServer();
        }
        return START_STICKY;
    }

    private void startServer() {
        ServerSocket ss = null;
        int port = -1;
        for (int i = 0; i < PORTS.length; i++) {
            try {
                ss = new ServerSocket(PORTS[i]);
                port = PORTS[i];
                break;
            } catch (IOException ignored) {
                ss = null;
            }
        }
        if (ss == null) {
            CLog.i("remote: no free port");
            stopSelf();
            return;
        }
        server = ss;
        sToken = newToken();
        sUrl = "http://" + localIp() + ":" + port;
        sRunning = true;
        CLog.i("remote started " + sUrl);

        Thread t = new Thread(new Runnable() {
            @Override public void run() { acceptLoop(); }
        }, "cdesk-remote-accept");
        t.setDaemon(true);
        t.start();
    }

    private static String newToken() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(TOKEN_CHARS.charAt(rnd.nextInt(TOKEN_CHARS.length())));
        return sb.toString();
    }

    private String localIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                            + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
                }
            }
        } catch (Throwable ignored) { }
        return "127.0.0.1";
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = server.accept();
                s.setSoTimeout(15000);
                Thread t = new Thread(new Runnable() {
                    @Override public void run() { handle(s); }
                }, "cdesk-remote-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!running) break;
            } catch (Throwable ignored) { }
        }
    }

    private void handle(Socket sock) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];

            // 头部读完即可，遥控请求没有 body
            String line;
            while ((line = readLine(in)) != null) {
                if (line.length() == 0) break;
            }

            if (!"GET".equals(method)) {
                sendJson(out, 405, "{\"ok\":false,\"err\":\"method\"}");
                return;
            }

            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }

            if (!tokenOk(query)) {
                sendJson(out, 403, "{\"ok\":false,\"err\":\"token\"}");
                return;
            }

            if (path.startsWith("/key")) {
                String code = queryParam(query, "c");
                boolean ok = RemoteKeys.dispatch(getApplicationContext(), code);
                sendJson(out, 200, ok ? "{\"ok\":true}" : "{\"ok\":false}");
            } else if (path.startsWith("/ping")) {
                sendJson(out, 200, "{\"ok\":true}");
            } else if (path.equals("/") || path.startsWith("/index")) {
                sendHtml(out);
            } else if (path.startsWith("/favicon")) {
                byte[] empty = new byte[0];
                writeHeaders(out, 204, "No Content", "text/plain", empty.length);
            } else {
                sendJson(out, 404, "{\"ok\":false,\"err\":\"notfound\"}");
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (out != null) out.flush(); } catch (Throwable ignored) { }
            try { if (sock != null) sock.close(); } catch (Throwable ignored) { }
        }
    }

    private boolean tokenOk(String query) {
        String k = queryParam(query, "k");
        String expected = sToken;
        if (expected == null || expected.length() == 0) return false;
        return expected.equals(k);
    }

    private static String queryParam(String query, String key) {
        if (query == null || query.length() == 0) return "";
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            int eq = pairs[i].indexOf('=');
            if (eq < 0) continue;
            String name = pairs[i].substring(0, eq);
            if (name.equals(key)) return pairs[i].substring(eq + 1);
        }
        return "";
    }

    private void sendHtml(OutputStream out) throws IOException {
        byte[] body = readAsset("remote.html");
        if (body == null) body = ("<h1>" + getString(R.string.app_name) + "</h1>").getBytes("UTF-8");
        writeHeaders(out, 200, "OK", "text/html; charset=utf-8", body.length);
        out.write(body);
        out.flush();
    }

    private void sendJson(OutputStream out, int status, String json) throws IOException {
        byte[] body = json.getBytes("UTF-8");
        writeHeaders(out, status, status == 200 ? "OK" : "Error", "application/json", body.length);
        out.write(body);
        out.flush();
    }

    private byte[] readAsset(String name) {
        InputStream is = null;
        try {
            is = getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (Throwable ignored) { }
        }
    }

    private void writeHeaders(OutputStream out, int code, String reason, String type, int len) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(" ").append(reason).append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(len).append("\r\n");
        h.append("Connection: close\r\n");
        h.append("Cache-Control: no-store\r\n\r\n");
        out.write(h.toString().getBytes("UTF-8"));
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == 10) return new String(bos.toByteArray(), "UTF-8");
            if (c != 13) bos.write(c);
            if (bos.size() > 8192) return null;
        }
        return bos.size() > 0 ? new String(bos.toByteArray(), "UTF-8") : null;
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
        Intent open = new Intent(this, RemoteActivity.class);
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
        b.setContentText(getString(R.string.remote_running));
        b.setSmallIcon(android.R.drawable.ic_menu_compass);
        b.setContentIntent(pi);
        b.setOngoing(true);
        return b.build();
    }

    @Override
    public void onDestroy() {
        running = false;
        sRunning = false;
        sUrl = "";
        sToken = "";
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        server = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
