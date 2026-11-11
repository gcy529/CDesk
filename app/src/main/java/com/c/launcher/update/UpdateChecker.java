package com.c.launcher.update;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Handles the persisted "dismissed update" state.
 * Networking (fetchUpdate) lands in task T2.
 */
public final class UpdateChecker {

    /** Shared-prefs file that stores the dismissal. */
    public static final String PREFS_NAME = "c_launch_update_prefs";

    /** Key holding the highest versionCode the user dismissed. */
    static final String KEY_DISMISSED = "dismissed_update_code";

    private UpdateChecker() {
    }

    /**
     * @return true when the user has already dismissed an update at or above
     *         {@code versionCode}, so no reminder should show.
     */
    public static boolean isDismissed(Context ctx, int versionCode) {
        SharedPreferences p = prefs(ctx);
        return p.getInt(KEY_DISMISSED, -1) >= versionCode;
    }

    /** Records the dismissal of a specific versionCode. */
    public static void markDismissed(Context ctx, int versionCode) {
        prefs(ctx).edit().putInt(KEY_DISMISSED, versionCode).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
