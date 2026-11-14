package com.c.launcher.update;

/**
 * POJO representing remote update metadata.
 */
public class UpdateInfo {
    public int versionCode;
    public String versionName = "";
    public String url = "";
    public boolean mandatory;
    public String releaseNotes = "";
    public int minSupportedCode;
}
