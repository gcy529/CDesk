package com.c.launcher;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class TvNav {

    private TvNav() { }

    public static void dockable(final Activity a, final Button back,
                                final Button[] buttons, final TextView[] texts) {
        final View[] items = new View[(texts == null ? 0 : texts.length)
                + (buttons == null ? 0 : buttons.length)];
        int i = 0;
        if (texts != null) {
            for (TextView t : texts) items[i++] = t;
        }
        if (buttons != null) {
            for (Button b : buttons) items[i++] = b;
        }

        for (final View v : items) {
            if (v == null) continue;
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
            v.setOnKeyListener(new View.OnKeyListener() {
                @Override public boolean onKey(View sender, int keyCode, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    int cur = indexOf(items, v);
                    int next = -1;
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_DPAD_DOWN: next = cur + 1; break;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_UP:   next = cur - 1; break;
                        default: return false;
                    }
                    if (next < 0) next = items.length - 1;
                    if (next >= items.length) next = 0;
                    if (items[next] != null) {
                        items[next].requestFocus();
                        return true;
                    }
                    return false;
                }
            });
        }

        if (back != null) {
            back.setFocusable(true);
            back.setFocusableInTouchMode(true);
        }
    }

    private static int indexOf(View[] arr, View v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == v) return i;
        }
        return -1;
    }
}
