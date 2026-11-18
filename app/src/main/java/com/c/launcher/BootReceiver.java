package com.c.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 开机自启遥控服务。
 *
 * 作用：机器一开机、桌面还没进前台时，先把遥控服务拉起来，
 * 让用户开机即能用手机扫码遥控，不用等手动进桌面。
 *
 * 触发条件：仅监听 BOOT_COMPLETED，且尊重「设置 → 手机遥控 → 开机自启」开关
 * （cdesk_remote 的 auto_start，默认开）。开关关掉则开机不自启。
 *
 * 说明：开机广播启动前台服务在 Android 12+ 属豁免场景，
 * RemoteService 自身已声明 foregroundServiceType，可直接 start。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences sp = context.getSharedPreferences("cdesk_remote", Context.MODE_PRIVATE);
        if (!sp.getBoolean("auto_start", true)) return;

        try {
            RemoteService.start(context);
            CLog.i("boot: remote service auto-started");
        } catch (Throwable t) {
            CLog.e("boot: remote auto-start failed", t);
        }
    }
}
