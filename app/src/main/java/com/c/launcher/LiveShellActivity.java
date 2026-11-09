package com.c.launcher;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * 直播壳：导入用户自己的直播源（M3U / TXT#genre# / JSON），按分组列台，点台进播放器。
 * 本应用不内置、不推荐、不分发任何频道或源。
 */
public class LiveShellActivity extends BaseShellActivity {

    @Override protected String prefKey() { return "live_src"; }

    @Override
    protected String targetKind() { return "live"; }

    @Override protected int titleRes() { return R.string.live_title; }

    @Override
    protected void onItemSelected(PlaylistParser.Item it) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, it.url);
        i.putExtra(PlayerActivity.EXTRA_NAME, it.name);
        i.putExtra(PlayerActivity.EXTRA_LIVE, true);
        startActivity(i);
    }
}
