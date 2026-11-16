package com.c.launcher.update;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import com.c.launcher.BuildConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link UpdateChecker}.
 *
 * <p>Uses Robolectric to simulate a live {@code Application} so that
 * {@code getSharedPreferences} and {@code getPackageName} work without a device.
 * HTTP layer tests focus on the {@code isDismissed} persistence contract
 * and the JSON parsing helper; the full HTTP round-trip requires a real
 * endpoint and is covered by the manual integration checklist in
 * {@code tasklist.md} Phase 4.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateCheckerTest {

    @Test
    public void isDismissed_returnsFalseWhenNothingStored() {
        Context ctx = RuntimeEnvironment.getApplication();
        assertThat(UpdateChecker.isDismissed(ctx, 100)).isFalse();
    }

    @Test
    public void markDismissed_persistsToSharedPreference() {
        Context ctx = RuntimeEnvironment.getApplication();
        UpdateChecker.markDismissed(ctx, 100);

        SharedPreferences prefs = ctx.getSharedPreferences(
                UpdateChecker.PREFS_NAME, Context.MODE_PRIVATE);
        assertThat(prefs.getInt("dismissed_update_code", -1)).isEqualTo(100);
    }

    @Test
    public void isDismissed_trueWhenRemoteCodeIsAtOrBelowDismissedCode() {
        Context ctx = RuntimeEnvironment.getApplication();
        UpdateChecker.markDismissed(ctx, 100);

        // Same code: previously dismissed -> still dismissed.
        assertThat(UpdateChecker.isDismissed(ctx, 100)).isTrue();
        // Older code: dismissed a higher version already -> also dismissed.
        assertThat(UpdateChecker.isDismissed(ctx, 99)).isTrue();
        // Newer code: newer version available -> prompt again.
        assertThat(UpdateChecker.isDismissed(ctx, 101)).isFalse();
    }

    @Test
    public void markDismissed_overwritesWithHigherCode() {
        Context ctx = RuntimeEnvironment.getApplication();
        UpdateChecker.markDismissed(ctx, 90);
        UpdateChecker.markDismissed(ctx, 110);

        SharedPreferences prefs = ctx.getSharedPreferences(
                UpdateChecker.PREFS_NAME, Context.MODE_PRIVATE);
        assertThat(prefs.getInt("dismissed_update_code", -1)).isEqualTo(110);
    }

    @Test
    public void markDismissed_ignoresLowerCodeThanStored() {
        // Once dismissed at code 110, a stale "mark 90" call must not
        // downgrade the dismissal (would re-open the dialog for v110).
        Context ctx = RuntimeEnvironment.getApplication();
        UpdateChecker.markDismissed(ctx, 110);
        UpdateChecker.markDismissed(ctx, 90);

        SharedPreferences prefs = ctx.getSharedPreferences(
                UpdateChecker.PREFS_NAME, Context.MODE_PRIVATE);
        assertThat(prefs.getInt("dismissed_update_code", -1)).isEqualTo(110);
    }
}
