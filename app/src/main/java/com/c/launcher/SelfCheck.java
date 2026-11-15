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
 * 注意：换签名钥匙时必须同步改混淆分段常量，否则所有人都会被误判成改版。
 * 当前值和 v0.2.5 ~ 0.6.1 逐个比对过，是同一把钥匙。
 *
 * 安全：期望签名哈希分 4 段，每段存为异或后的字符码数组，运行时逐段异或
 * 还原并拼接。避免明文哈希直接躺在 APK 里被反编译一眼读走，单段被提取
 * 也无法独立还原，逆向门槛高于单纯反转串。
 */
public final class SelfCheck {

    /** 期望官方签名 SHA-256 的 4 段异或混淆字符码（每段 16 个字符码） */
    private static final int[] OBF_SEG0 = {
        0x3B, 0x38, 0x68, 0x6B, 0x62, 0x62, 0x6C, 0x3B, 0x63, 0x3E, 0x3B, 0x38, 0x3B, 0x3C, 0x6F, 0x6A
    };
    private static final int[] OBF_SEG1 = {
        0x04, 0x08, 0x08, 0x0F, 0x05, 0x58, 0x0C, 0x59, 0x59, 0x0A, 0x08, 0x09, 0x0F, 0x0B, 0x05, 0x05
    };
    private static final int[] OBF_SEG2 = {
        0x1C, 0x4C, 0x4F, 0x4C, 0x47, 0x18, 0x48, 0x4E, 0x4C, 0x48, 0x46, 0x46, 0x1D, 0x1C, 0x1D, 0x1B
    };
    private static final int[] OBF_SEG3 = {
        0x1A, 0x1F, 0x1F, 0x4D, 0x1D, 0x4D, 0x1F, 0x4C, 0x19, 0x16, 0x17, 0x1F, 0x1D, 0x1D, 0x4D, 0x18
    };

    /** 每段对应的异或密钥（与段顺序一一对应） */
    private static final int[] OBF_KEYS = { 0x5A, 0x3C, 0x7E, 0x2F };

    private static final String CONFIG_PREFS = "cdesk_signcheck";
    private static final String KEY_OFFICIAL = "official_signature";

    private SelfCheck() { }

    /** 逐段异或还原并拼接出完整明文哈希（仅运行时使用，不落盘、不输出） */
    private static String officialHash() {
        int[][] segs = { OBF_SEG0, OBF_SEG1, OBF_SEG2, OBF_SEG3 };
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < segs.length; i++) {
            int[] arr = segs[i];
            int k = OBF_KEYS[i];
            for (int j = 0; j < arr.length; j++) {
                sb.append((char) (arr[j] ^ k));
            }
        }
        return sb.toString();
    }

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
            String sig = configuredOfficial(c);
            return sig.equals(got);
        } catch (Throwable t) {
            CLog.e("signature check failed: " + t);
            return false;
        }
    }

    /** 签名校验值默认用编译期常量，可经 SharedPreferences 下发替换，避免单点硬编码。 */
    private static String configuredOfficial(Context c) {
        try {
            String v = c.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_OFFICIAL, null);
            return v == null ? officialHash() : v;
        } catch (Throwable t) {
            return officialHash();
        }
    }
}
