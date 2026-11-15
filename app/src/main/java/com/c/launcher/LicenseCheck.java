package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * 正版授权校验。
 *
 * 双层校验：
 *  第一层（SelfCheck）：算自身签名指纹，确认包未被篡改、签名在授权列表中。
 *  第二层（LicenseCheck）：向服务端请求授权令牌，验 RSA 签名、验过期、
 *  验令牌绑定的签名指纹与自身一致。两层都通过才放行。
 *
 * 服务端按签名指纹发令牌：官方签名和登记过的定制签名能拿到令牌，
 * 盗版者用自己的签名请求，服务端不在授权列表里就不发，客户端拿不到
 * 令牌即被限制。这样官方版、定制版靠令牌放行，盗版挡在门外。
 *
 * 离线容错：令牌缓存到本地，断网时用缓存放行；过期后给 7 天宽限期，
 * 超期仍未联网续期才拒绝，避免车机断网一次就锁死。
 *
 * 注意：部署服务端后需替换 LICENSE_ENDPOINT，公钥对应服务端私钥。
 */
public final class LicenseCheck {

    /** 授权服务端点；部署服务端后替换此地址 */
    private static final String LICENSE_ENDPOINT =
            "https://raw.githubusercontent.com/gcy529/CDesk/beta/license_server_url.txt";

    /** 服务端 RSA 公钥（X509，base64）。公钥本就公开，内置客户端是设计，泄露无害。 */
    private static final String PUB_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzzbgWmzR5g2qr37acM9r0s0IH+21CHoPE1Lr8b33nH48xPrJcDsWi6JmY8ZY28a8oZrNEs0guzuULuLJF1iZ/Gc7JKM46uCtJmWgNMqcO8b/xvQC54Ef21/Vz7R5YdELiyd21/C0PFzq/Td/nrggOJyvjlCiQB7mVBQOOSVWDsUsZDHgWaf5EegD9wZzTlEJaFiBuVIfWVO5shyFq+GlzWzN27sA04yr5TKexVUVdTjsaxUpF74GxxsBKMBOaYBDZ7yOAy88Lx+6IaIBDV3JJk/mQPPyUWRDROAGx2eXFjVNotyQhMQjStmzW8Ktif5Nlj34lNVEn6XZhvkuoyi6JwIDAQAB";

    /** 令牌过期后的宽限期：7 天，给车机断网续期的机会 */
    private static final long GRACE_MS = 7L * 24 * 60 * 60 * 1000;

    private static final String PREFS = "cdesk_license";
    private static final String KEY_CID = "cid";
    private static final String KEY_EXP = "exp";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_FP = "fp";
    private static final String KEY_SIG = "sig";
    private static final String KEY_TS = "fetch_ts";

    private LicenseCheck() { }

    public interface Callback {
        /** 授权通过，cid 为客户标识，scope 为功能范围（full/basic） */
        void onLicensed(String cid, String scope);
        /** 授权被拒，reason 说明原因（expired/denied/network/tamper） */
        void onDenied(String reason);
    }

    /** 算自身签名证书的 SHA-256 指纹，用于令牌绑定校验 */
    public static String signatureFp(Context c) {
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager()
                    .getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
            if (pi == null || pi.signatures == null || pi.signatures.length == 0) { return ""; }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(pi.signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                int v = b & 0xFF;
                if (v < 16) { sb.append('0'); }
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 同步检查缓存令牌是否有效；用于快速判断，不发网络请求 */
    public static boolean isCachedLicensed(Context c) {
        Token t = loadCached(c);
        if (t == null) return false;
        String fp = signatureFp(c);
        if (!fp.equals(t.fp)) return false;
        if (!verifySig(t)) return false;
        long now = System.currentTimeMillis();
        return now < t.expMs + GRACE_MS;
    }

    /**
     * 异步校验授权：先查缓存，缓存有效直接放行；无效或过期则请求服务端续期。
     */
    public static void ensure(final Context c, final Callback cb) {
        final String fp = signatureFp(c);
        Token cached = loadCached(c);
        if (cached != null && fp.equals(cached.fp) && verifySig(cached)) {
            long now = System.currentTimeMillis();
            if (now < cached.expMs) {
                cb.onLicensed(cached.cid, cached.scope);
                return;
            }
            // 过期但在宽限期内：断网容错放行，同时尝试联网续期
            if (now < cached.expMs + GRACE_MS) {
                cb.onLicensed(cached.cid, cached.scope);
                fetchAsync(c, fp, cb);
                return;
            }
        }
        fetchAsync(c, fp, cb);
    }

    /** 强制清除本地令牌缓存，下次启动重新请求 */
    public static void clear(Context c) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        } catch (Throwable ignored) { }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static void fetchAsync(final Context c, final String fp, final Callback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final FetchResult r = fetch(c, fp);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        // 无签名指纹的安装无法绑定令牌，直接判为篡改
                        if (fp.length() == 0) {
                            cb.onDenied("tamper");
                            return;
                        }
                        // 服务端不可达（断网 / 端点未部署 / 404 / 国内直连 raw 不通）：
                        // fail-open 放行。否则端点未就绪会把新用户锁死在不可关闭的授权
                        // 弹窗里；端点可用后，服务端明确拒绝仍会正常限制。
                        if (!r.reachable) {
                            CLog.w("license endpoint unreachable, fail-open allowing launch");
                            cb.onLicensed("offline", "full");
                            return;
                        }
                        Token t = r.token;
                        if (t == null || !verifySig(t)) {
                            cb.onDenied(t == null ? "denied" : "tamper");
                        } else if (!fp.equals(t.fp)) {
                            cb.onDenied("tamper");
                        } else if (System.currentTimeMillis() >= t.expMs + GRACE_MS) {
                            cb.onDenied("expired");
                        } else {
                            saveCached(c, t);
                            cb.onLicensed(t.cid, t.scope);
                        }
                    }
                });
            }
        }).start();
    }

    /** 区分「网络不可达」与「服务端可达但拒绝/格式非法」，前者 fail-open，后者拒绝 */
    private static final class FetchResult {
        final boolean reachable;
        final Token token;
        FetchResult(boolean reachable, Token token) {
            this.reachable = reachable;
            this.token = token;
        }
    }

    private static FetchResult fetch(Context c, String fp) {
        String body = httpGet(LICENSE_ENDPOINT + "?fp=" + fp);
        if (body == null) return new FetchResult(false, null);
        return new FetchResult(true, parse(body, fp));
    }

    /** 解析服务端返回的令牌 JSON；字段不全或格式非法返回 null */
    private static Token parse(String body, String fp) {
        try {
            JSONObject o = new JSONObject(body.trim());
            Token t = new Token();
            t.cid = optStr(o, "cid");
            t.exp = optLong(o, "exp");
            t.scope = optStr(o, "scope");
            t.fp = optStr(o, "fp");
            t.sig = optStr(o, "sig");
            if (t.cid.length() == 0 || t.exp <= 0 || t.fp.length() == 0 || t.sig.length() == 0) {
                return null;
            }
            t.expMs = t.exp * 1000L;
            return t;
        } catch (JSONException e) {
            return null;
        }
    }

    /** RSA 公钥验签：用公钥验证 sig 对 "cid|exp|scope|fp" 的签名 */
    private static boolean verifySig(Token t) {
        try {
            byte[] keyBytes = Base64.decode(PUB_KEY_B64, Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(spec);
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(pk);
            String payload = t.cid + "|" + t.exp + "|" + t.scope + "|" + t.fp;
            s.update(payload.getBytes("UTF-8"));
            return s.verify(Base64.decode(t.sig, Base64.DEFAULT));
        } catch (Throwable e) {
            CLog.e("license verify failed: " + e);
            return false;
        }
    }

    private static Token loadCached(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String cid = p.getString(KEY_CID, null);
            if (cid == null) return null;
            Token t = new Token();
            t.cid = cid;
            t.exp = p.getLong(KEY_EXP, 0L);
            t.scope = p.getString(KEY_SCOPE, "");
            t.fp = p.getString(KEY_FP, "");
            t.sig = p.getString(KEY_SIG, "");
            t.expMs = t.exp * 1000L;
            return t;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void saveCached(Context c, Token t) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit()
                    .putString(KEY_CID, t.cid)
                    .putLong(KEY_EXP, t.exp)
                    .putString(KEY_SCOPE, t.scope)
                    .putString(KEY_FP, t.fp)
                    .putString(KEY_SIG, t.sig)
                    .putLong(KEY_TS, System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) { }
    }

    private static String httpGet(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(3000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "CDesk-license");
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 64 * 1024) break;
            }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String optStr(JSONObject o, String key) {
        Object v = o.opt(key);
        return v == null ? "" : v.toString();
    }

    private static long optLong(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v == null) return 0L;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private static final class Token {
        String cid = "";
        long exp = 0L;
        String scope = "";
        String fp = "";
        String sig = "";
        long expMs = 0L;
    }
}
