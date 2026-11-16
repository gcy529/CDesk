package com.c.launcher;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

/**
 * 播放失败时的现场诊断。
 *
 * 老设备「本地文件能播、在线地址播不了」几乎都是这几件事：
 *   tls   4.1 只支持 TLS1.0/1.1，现在的站基本只收 1.2 → 握手就死
 *   sni   4.1 的 TLS 栈不发 SNI，同 IP 多站的 CDN 会给错证书
 *   dns   解析失败或者解析到内网/黑洞地址
 *   tcp   连得上但端口不通 / 被墙
 *   http  403 404 451 或者 http→https 跨协议跳转
 *   clock 系统时间错了会让证书「未生效/已过期」
 *
 * 输出一律用英文短标签，因为这些东西会显示在播放页上，
 * 而英文界面不许出现中文（有测试盯着）。
 */
public final class NetDiag {

    public interface Sink { void line(String text); }

    private NetDiag() { }

    public static void diag(final String rawUrl, final Sink sink) {
        if (rawUrl == null || rawUrl.length() < 6) { return; }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    URL u = new URL(rawUrl);
                    String scheme = u.getProtocol();
                    String host = u.getHost();
                    int port = u.getPort() > 0 ? u.getPort() : ("https".equals(scheme) ? 443 : 80);
                    sink.line("diag " + scheme + " " + host + ":" + port);
                    sink.line("tls " + tlsSupport());
                    dns(host, sink);
                    tcp(host, port, sink);
                    http(u, sink);
                } catch (Throwable t) {
                    sink.line("diag failed: " + t.getClass().getSimpleName());
                }
            }
        }).start();
    }

    /** 这个系统到底支持哪些 TLS 版本 */
    private static String tlsSupport() {
        try {
            String[] ps = SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ps.length; i++) {
                if (ps[i].startsWith("TLS")) {
                    if (sb.length() > 0) { sb.append('/'); }
                    sb.append(ps[i].replace("TLSv", ""));
                }
            }
            boolean has12 = sb.indexOf("1.2") >= 0;
            return sb.toString() + (has12 ? "" : " (NO TLS1.2)");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static void dns(String host, Sink sink) {
        long t0 = System.currentTimeMillis();
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            long ms = System.currentTimeMillis() - t0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < all.length && i < 3; i++) {
                if (i > 0) { sb.append(','); }
                sb.append(all[i].getHostAddress());
            }
            sink.line("dns ok " + ms + "ms " + sb);
        } catch (Throwable t) {
            sink.line("dns FAILED " + t.getClass().getSimpleName());
        }
    }

    private static void tcp(String host, int port, Sink sink) {
        Socket s = null;
        long t0 = System.currentTimeMillis();
        try {
            s = new Socket();
            s.connect(new java.net.InetSocketAddress(host, port), 6000);
            sink.line("tcp ok " + (System.currentTimeMillis() - t0) + "ms");
        } catch (Throwable t) {
            sink.line("tcp FAILED " + t.getClass().getSimpleName());
        } finally {
            if (s != null) { try { s.close(); } catch (Throwable ignored) { } }
        }
    }

    private static void http(URL u, Sink sink) {
        HttpURLConnection c = null;
        long t0 = System.currentTimeMillis();
        try {
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CDesk/1.0");
            c.setRequestProperty("Range", "bytes=0-1");
            int code = c.getResponseCode();
            long ms = System.currentTimeMillis() - t0;
            String ct = c.getContentType();
            String loc = c.getHeaderField("Location");
            sink.line("http " + code + " " + ms + "ms " + (ct == null ? "" : ct));
            if (loc != null && loc.length() > 0) { sink.line("redirect " + shorten(loc)); }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                byte[] buf = new byte[2];
                int n = in.read(buf);
                in.close();
                sink.line("bytes " + (n < 0 ? 0 : n));
            }
            clock(c, sink);
        } catch (SSLException e) {
            sink.line("http TLS FAILED: " + shorten(String.valueOf(e.getMessage())));
        } catch (Throwable t) {
            sink.line("http FAILED " + t.getClass().getSimpleName());
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Throwable ignored) { } }
        }
    }

    /** 服务器时间 vs 本机时间：差太多证书会被判过期 */
    private static void clock(HttpURLConnection c, Sink sink) {
        try {
            String d = c.getHeaderField("Date");
            if (d == null) { return; }
            SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date server = f.parse(d);
            long diffMin = (System.currentTimeMillis() - server.getTime()) / 60000L;
            if (Math.abs(diffMin) >= 5) {
                sink.line("clock skew " + diffMin + "min (cert may look expired)");
            }
        } catch (Throwable ignored) {
        }
    }

    private static String shorten(String s) {
        if (s == null) { return ""; }
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }
}
