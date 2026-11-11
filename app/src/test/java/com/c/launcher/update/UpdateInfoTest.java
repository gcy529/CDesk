package com.c.launcher.update;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link UpdateInfo} POJO.
 *
 * <p>Spec-refs: design.md "File Creation" (UpdateInfo.java).
 */
public class UpdateInfoTest {

    @Test
    public void defaultsAreZeroAndEmpty() {
        UpdateInfo info = new UpdateInfo();
        assertEquals(0, info.versionCode);
        assertEquals("", info.versionName);
        assertEquals("", info.url);
        assertEquals(false, info.mandatory);
        assertEquals("", info.releaseNotes);
        assertEquals(0, info.minSupportedCode);
    }

    @Test
    public void fieldsCanBeSet() {
        UpdateInfo info = new UpdateInfo();
        info.versionCode = 42;
        info.versionName = "2.1.0";
        info.url = "https://example.com/app.apk";
        info.mandatory = true;
        info.releaseNotes = "fixes";
        info.minSupportedCode = 10;
        assertEquals(42, info.versionCode);
        assertEquals("2.1.0", info.versionName);
        assertEquals("https://example.com/app.apk", info.url);
        assertEquals(true, info.mandatory);
        assertEquals("fixes", info.releaseNotes);
        assertEquals(10, info.minSupportedCode);
    }
}
