package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 设备类型识别：TV / 车机 / Pad / 手机。
 * 设置页可手动覆盖自动识别结果。
 */
public class DeviceType {

    public static final int AUTO = 0;
    public static final int TV = 1;
    public static final int CAR = 2;
    public static final int PAD = 3;
    public static final int PHONE = 4;

    private static final String PREFS = "cdesk_device";
    private static final String KEY_TYPE = "type";

    /** 自动识别：按 Build.BRAND / Build.DEVICE / 屏幕尺寸判断 */
    public static int detect(Context c) {
        try {
            String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
            String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
            String device = Build.DEVICE != null ? Build.DEVICE.toLowerCase() : "";

            // TV 特征：品牌含 tv / 盒子常见品牌
            if (brand.contains("tv") || device.contains("tv")
                    || brand.contains("roku") || brand.contains("amazon")
                    || brand.contains("hisense") || brand.contains("tcl")
                    || brand.contains("humminbird") || brand.contains("weos")) {
                return TV;
            }

            // 车机特征：品牌含 car / 常见车机厂牌
            if (brand.contains("car") || device.contains("car")
                    || brand.contains("desay") || brand.contains("flyaudio")
                    || brand.contains("navi") || model.contains("head unit")) {
                return CAR;
            }

            // 屏幕尺寸判断：小屏 = 手机，大屏 = Pad
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int wDp = (int) (dm.widthPixels / dm.density);
            int hDp = (int) (dm.heightPixels / dm.density);
            int shortDp = Math.min(wDp, hDp);
            if (shortDp >= 600) return PAD;
            return PHONE;
        } catch (Throwable t) {
            return PHONE;
        }
    }

    /** 读取生效类型：手动覆盖优先，否则自动识别 */
    public static int effective(Context c) {
        int manual = getManual(c);
        if (manual != AUTO) return manual;
        return detect(c);
    }

    public static int getManual(Context c) {
        try {
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_TYPE, AUTO);
        } catch (Throwable t) {
            return AUTO;
        }
    }

    public static void setManual(Context c, int type) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_TYPE, type).commit();
        } catch (Throwable ignored) { }
    }

    /** 标签：自动 / TV / 车机 / Pad / 手机 */
    public static String label(Context c, int type) {
        switch (type) {
            case TV: return "TV";
            case CAR: return c.getString(R.string.device_car);
            case PAD: return c.getString(R.string.device_pad);
            case PHONE: return c.getString(R.string.device_phone);
            default: return c.getString(R.string.device_auto);
        }
    }
}
