package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 天气：IP 自动定位，支持手动城市。
 *
 * 手动城市存在 prefs（名字 + 经纬度），一旦设置就永远优先于 IP 定位，
 * IP 定位再飘也不影响 —— 车机基本不动，手动一次就够了。
 *
 * 链路（全部 HTTPS、免费、无 key，任何国家自动适配）：
 *   ipwho.is        -> 经纬度 + 城市名（IP 定位，不需要枚举国家）
 *   api.open-meteo  -> 当前温度 + WMO 天气码（全球统一标准码）
 *
 * 缓存 30 分钟；任何一步失败都静默返回 null，桌面上那一行就不显示，
 * 绝不能因为天气挂掉影响桌面。
 *
 * 手动选城市（第二版）：放到局域网传输的网页里做 —— 手机键盘打字，
 * 写回车机。遥控器不适合输入，这是输入方式的问题，不是国家多的问题。
 */
public final class WeatherManager {

    public interface Callback { void onResult(String line); }

    private static final long CACHE_MS = 30L * 60L * 1000L;
    private static final String PREFS = "cdesk_weather";
    private static final String KEY_MODE = "mode";       // auto | manual
    private static final String KEY_CITY = "manual_city";
    private static final String KEY_LAT = "manual_lat";
    private static final String KEY_LON = "manual_lon";

    private WeatherManager() { }

    /** 手动城市：设置后永久覆盖 IP 定位 */
    public static void setManualCity(Context c, String name, double lat, double lon) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODE, "manual").putString(KEY_CITY, name)
                .putString(KEY_LAT, String.valueOf(lat)).putString(KEY_LON, String.valueOf(lon))
                .putString("line", null).putLong("at", 0L).commit();
    }

    /** 回到 IP 自动 */
    public static void setAutoCity(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODE, "auto").putString("line", null).putLong("at", 0L).commit();
    }

    public static boolean isManual(Context c) {
        return "manual".equals(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, "auto"));
    }

    /** 手动城市经纬度，没设置返回 null */
    public static double[] manualLatLng(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return new double[] {
                    Double.parseDouble(p.getString(KEY_LAT, "")),
                    Double.parseDouble(p.getString(KEY_LON, "")) };
        } catch (Throwable t) { return null; }
    }

    public static String manualCityName(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CITY, null);
    }

    /** 有缓存先用缓存，过期才走网络；回调一定回到调用线程，由 UI 自己 post */
    public static void fetch(final Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        String cached = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("line", null);
        long at = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0L);
        if (cached != null && System.currentTimeMillis() - at < CACHE_MS) {
            cb.onResult(cached);
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                String line = load(app);
                // 手动模式但没填城市 → 静默回退 IP
                if (line != null) {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString("line", line)
                        .putLong("at", System.currentTimeMillis()).commit();
                }
                cb.onResult(line);
            }
        }, "cdesk-weather");
        t.setDaemon(true);
        t.start();
    }

    private static String load(Context ctx) {
        try {
            double[] ll;
            String[] city = new String[1];
            if (isManual(ctx)) {
                ll = manualLatLng(ctx);
                city[0] = manualCityName(ctx);
                if (ll == null || city[0] == null) return null;
            } else {
                String geo = get("https://ipwho.is/");
                if (geo == null) return null;
                if (!parseGeo(geo, ll = new double[2], city)) return null;
            }

            String wx = get("https://api.open-meteo.com/v1/forecast?latitude="
                    + ll[0] + "&longitude=" + ll[1] + "&current_weather=true");
            if (wx == null) return null;
            double[] temp = new double[1];
            int[] code = new int[1];
            if (!parseWx(wx, temp, code)) return null;

            int res = condRes(code[0]);
            String cond = res == 0 ? "" : "  " + ctx.getString(res);
            return city[0] + "  " + Math.round(temp[0]) + "\u00B0C" + cond;
        } catch (Throwable t) {
            return null;
        }
    }

    /** {"success":true,"city":"Shenzhen","latitude":22.55,"longitude":114.05,...} */
    private static boolean parseGeo(String json, double[] ll, String[] city) {
        JsonReader r = new JsonReader(new java.io.StringReader(json));
        try {
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if ("success".equals(name)) {
                    if (!r.nextBoolean()) return false;
                } else if ("latitude".equals(name)) {
                    ll[0] = r.nextDouble();
                } else if ("longitude".equals(name)) {
                    ll[1] = r.nextDouble();
                } else if ("city".equals(name)) {
                    city[0] = r.nextString();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
            return city[0] != null && ll[0] != 0 && ll[1] != 0;
        } catch (Throwable t) {
            return false;
        } finally {
            try { r.close(); } catch (Throwable ignored) { }
        }
    }

    /** {"current_weather":{"temperature":23.4,"weathercode":2,...}} */
    private static boolean parseWx(String json, double[] temp, int[] code) {
        JsonReader r = new JsonReader(new java.io.StringReader(json));
        try {
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if ("current_weather".equals(name)) {
                    r.beginObject();
                    while (r.hasNext()) {
                        String k = r.nextName();
                        if ("temperature".equals(k)) temp[0] = r.nextDouble();
                        else if ("weathercode".equals(k)) code[0] = r.nextInt();
                        else r.skipValue();
                    }
                    r.endObject();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { r.close(); } catch (Throwable ignored) { }
        }
    }

    /** WMO 天气码 -> 文案资源（粗分桶，够用就好） */
    private static int condRes(int c) {
        if (c == 0) return R.string.weather_clear;
        if (c == 1 || c == 2) return R.string.weather_partly;
        if (c == 3) return R.string.weather_overcast;
        if (c == 45 || c == 48) return R.string.weather_fog;
        if (c >= 51 && c <= 57) return R.string.weather_drizzle;
        if (c >= 61 && c <= 67) return R.string.weather_rain;
        if (c >= 71 && c <= 77) return R.string.weather_snow;
        if (c >= 80 && c <= 82) return R.string.weather_showers;
        if (c >= 85 && c <= 86) return R.string.weather_snow;
        if (c >= 95 && c <= 99) return R.string.weather_thunder;
        return R.string.weather_partly;
    }

    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "CDesk/0.1");
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) { }
        }
    }
}
