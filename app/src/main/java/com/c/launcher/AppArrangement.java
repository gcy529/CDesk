package com.c.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Stores the user's desktop app order and hidden packages. */
public final class AppArrangement {
    private static final String PREFS = "cdesk_apps";
    private static final String ORDER = "order";

    private AppArrangement() { }

    public static List<AppAdapter.AppItem> visible(Context c, List<AppAdapter.AppItem> source) {
        SharedPreferences p = prefs(c);
        List<String> order = csv(p.getString(ORDER, ""));
        List<AppAdapter.AppItem> result = new ArrayList<AppAdapter.AppItem>();
        java.util.HashSet<String> added = new java.util.HashSet<String>();
        for (String pkg : order) {
            for (AppAdapter.AppItem item : source) {
                if (pkg.equals(item.pkg)) {
                    result.add(item);
                    added.add(pkg);
                    break;
                }
            }
        }
        for (AppAdapter.AppItem item : source) {
            if (!added.contains(item.pkg)) result.add(item);
        }
        return result;
    }

    public static void move(Context c, String pkg, int delta, List<AppAdapter.AppItem> current) {
        List<AppAdapter.AppItem> items = visible(c, current);
        int at = -1;
        for (int i = 0; i < items.size(); i++) if (pkg.equals(items.get(i).pkg)) { at = i; break; }
        int to = at + delta;
        if (at < 0 || to < 0 || to >= items.size()) return;
        AppAdapter.AppItem item = items.remove(at);
        items.add(to, item);
        saveOrder(c, items);
    }

    private static void saveOrder(Context c, List<AppAdapter.AppItem> items) {
        StringBuilder b = new StringBuilder();
        for (AppAdapter.AppItem item : items) {
            if (b.length() > 0) b.append(',');
            b.append(item.pkg);
        }
        prefs(c).edit().putString(ORDER, b.toString()).commit();
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static List<String> csv(String raw) {
        List<String> result = new ArrayList<String>();
        if (raw != null) for (String item : raw.split(",")) if (item.length() > 0) result.add(item);
        return result;
    }

}
