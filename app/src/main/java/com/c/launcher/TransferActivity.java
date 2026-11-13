package com.c.launcher;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * LAN transfer: the phone opens the printed address in a browser and posts files.
 *
 * Protocol:
 *   GET  /          serves assets/transfer.html
 *   POST /upload    body is the file, reply {"ok":true,"bytes":N}
 *
 * Works from Android 4.0 upward: plain ServerSocket plus daemon threads,
 * no third party libraries. Ports are probed in order 8080/8081/8088/8888/9090.
 */
public class TransferActivity extends Activity {

    private static final int[] PORTS = new int[] { 8080, 8081, 8088, 8888, 9090 };

    private TextView tvStatus;
    private TextView tvAddr;
    private TextView tvLog;
    private Button btnToggle;
    private ScrollView scrollLog;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<String> logLines = new ArrayList<String>();
    private ServerSocket server;
    private volatile boolean running = false;
    private File saveDir;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.apply(this);
        setContentView(R.layout.activity_transfer);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvAddr = (TextView) findViewById(R.id.tvAddr);
        tvLog = (TextView) findViewById(R.id.tvLog);
        btnToggle = (Button) findViewById(R.id.btnToggle);
        scrollLog = (ScrollView) findViewById(R.id.scrollLog);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (running) stopServer(); else startServer();
            }
        });

        File ext = getExternalFilesDir(null);
        String root = ext != null ? ext.getAbsolutePath() : getFilesDir().getAbsolutePath();
        saveDir = new File(root, "CDeskTransfer");
        if (!saveDir.exists()) saveDir.mkdirs();

        tvStatus.setText(R.string.transfer_idle);
        btnToggle.setText(R.string.transfer_start);
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void appendLog(final String line) {
        ui.post(new Runnable() {
            @Override public void run() {
                logLines.add(line);
                if (logLines.size() > 60) logLines.remove(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < logLines.size(); i++) {
                    sb.append(logLines.get(i));
                    sb.append((char) 10);
                }
                tvLog.setText(sb.toString());
                scrollLog.post(new Runnable() {
                    @Override public void run() { scrollLog.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private void startServer() {
        if (running) return;
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
            tvStatus.setText(R.string.transfer_port_busy);
            Toast.makeText(this, R.string.transfer_port_busy, Toast.LENGTH_LONG).show();
            return;
        }
        server = ss;
        running = true;

        final String url = "http://" + localIp() + ":" + port;
        ui.post(new Runnable() {
            @Override public void run() {
                tvAddr.setText(url);
                btnToggle.setText(R.string.transfer_stop);
                showQr(url);
            }
        });
        tvStatus.setText(R.string.transfer_running);
        appendLog(getString(R.string.transfer_log_start) + " " + url);
        appendLog(getString(R.string.transfer_save_to) + " " + saveDir.getAbsolutePath());

        Thread t = new Thread(new Runnable() {
            @Override public void run() { acceptLoop(); }
        }, "cdesk-transfer-accept");
        t.setDaemon(true);
        t.start();
    }

    private void stopServer() {
        if (!running) return;
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        server = null;
        ui.post(new Runnable() {
            @Override public void run() {
                if (isFinishing()) return;
                btnToggle.setText(R.string.transfer_start);
                tvAddr.setText("");
                tvStatus.setText(R.string.transfer_idle);
                ImageView iv = (ImageView) findViewById(R.id.ivQr);
                TextView hint = (TextView) findViewById(R.id.tvQrHint);
                if (iv != null) iv.setVisibility(View.GONE);
                if (hint != null) hint.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 把传输地址画成二维码。手机扫码直接打开传输页，不用手输 IP。
     * 用 ZXing 的 QRCodeWriter（纯 Java），黑模块用深色、白底留静区，深浅主题都能扫。
     */
    private void showQr(String url) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints =
                    new java.util.HashMap<com.google.zxing.EncodeHintType, Object>();
            hints.put(com.google.zxing.EncodeHintType.MARGIN, Integer.valueOf(1));
            com.google.zxing.common.BitMatrix mx =
                    w.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512, hints);
            int s = mx.getWidth();
            int[] px = new int[s * s];
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    px[y * s + x] = mx.get(x, y) ? 0xFF11131B : 0xFFFFFFFF;
                }
            }
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, s, 0, 0, s, s);
            ImageView iv = (ImageView) findViewById(R.id.ivQr);
            TextView hint = (TextView) findViewById(R.id.tvQrHint);
            if (iv != null) { iv.setImageBitmap(bmp); iv.setVisibility(View.VISIBLE); }
            if (hint != null) hint.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) { }
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
                s.setSoTimeout(30000);
                Thread t = new Thread(new Runnable() {
                    @Override public void run() { handle(s); }
                }, "cdesk-transfer-conn");
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
            String path = parts[1];

            long contentLength = -1L;
            String fileName = null;
            String line;
            while ((line = readLine(in)) != null) {
                if (line.length() == 0) break;
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Long.parseLong(line.substring(15).trim());
                    } catch (Throwable ignored) {
                        contentLength = -1L;
                    }
                } else if (lower.startsWith("x-file-name:")) {
                    fileName = line.substring("x-file-name:".length()).trim();
                }
            }

            if ("POST".equals(method) && path.startsWith("/upload")) {
                receiveFile(in, out, contentLength, fileName);
            } else {
                sendLandingPage(out);
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (out != null) out.flush(); } catch (Throwable ignored) { }
            try { if (sock != null) sock.close(); } catch (Throwable ignored) { }
        }
    }

    private void receiveFile(InputStream in, OutputStream out, long len, String rawName) throws IOException {
        String name = sanitizeName(rawName);
        if (name == null || name.length() == 0) {
            name = "upload-" + System.currentTimeMillis() + ".bin";
        }
        File f = new File(saveDir, name);
        long got = 0L;
        FileOutputStream fos = new FileOutputStream(f);
        byte[] buf = new byte[8192];
        try {
            while (len < 0 || got < len) {
                int want = buf.length;
                if (len >= 0) want = (int) Math.min((long) buf.length, len - got);
                int n = in.read(buf, 0, want);
                if (n <= 0) break;
                fos.write(buf, 0, n);
                got += n;
            }
            fos.flush();
        } finally {
            try { fos.close(); } catch (Throwable ignored) { }
        }
        appendLog(human(got) + "  ->  " + name);
        String json = "{\"ok\":true,\"bytes\":" + got + "}";
        byte[] body = json.getBytes("UTF-8");
        writeHeaders(out, 200, "OK", "application/json", body.length);
        out.write(body);
        out.flush();
    }

    /** 过滤文件名，去掉路径分隔符与非法字符，防止目录穿越 */
    private static String sanitizeName(String raw) {
        if (raw == null) return null;
        String n = raw.trim();
        if (n.length() == 0) return null;
        int slash = n.lastIndexOf('/');
        int bs = n.lastIndexOf('\\');
        int cut = Math.max(slash, bs);
        if (cut >= 0) n = n.substring(cut + 1);
        n = n.replace("..", "").replace(":", "").replace("*", "")
             .replace("?", "").replace("\"", "").replace("<", "")
             .replace(">", "").replace("|", "").trim();
        return n.length() == 0 ? null : n;
    }

    private void sendLandingPage(OutputStream out) throws IOException {
        byte[] body = readAsset("transfer.html");
        if (body == null) {
            body = "<h1>C桌面</h1>".getBytes("UTF-8");
        }
        writeHeaders(out, 200, "OK", "text/html; charset=utf-8", body.length);
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

    private static String human(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1048576L) return (bytes / 1024L) + " KB";
        if (bytes < 1073741824L) return (bytes / 1048576L) + " MB";
        return (bytes / 1073741824L) + " GB";
    }
}
