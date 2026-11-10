package com.c.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.security.MessageDigest;

/**
 * 校验自己是不是官方包。
 *
 * 原理：别人把 APK 改一改再重签，签名证书必然和我们的不一样。
 * 这里算自己证书的 SHA-256 跟写死的值比，对不上说明不是我们发的。
 *
 * 注意：换签名钥匙时必须同步改 OFFICIAL，否则所有人都会被误判成改版。
 * 当前值和 v0.2.5 ~ 0.6.1 逐个比对过，是同一把钥匙。
 */
public final class SelfCheck {

    private static final String OFFICIAL =
            "ab21886a9dabaf5084439d0ee6453799b2129f602688cbce500b2b0c698022b7";

    private SelfCheck() { }

    public static boolean isOfficial(Context c) {
        try {
            PackageInfo pi = c.getPackageManager()
                    .getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            if (pi == null || pi.signatures == null || pi.signatures.length == 0) { return false; }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(pi.signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                int v = b & 0xFF;
                if (v < 16) { sb.append('0'); }
                sb.append(Integer.toHexString(v));
            }
            String got = sb.toString();
            CLog.i("apk signature " + got);
            return OFFICIAL.equals(got);
        } catch (Throwable t) {
            CLog.e("signature check failed: " + t);
            return false;
        }
    }
}
