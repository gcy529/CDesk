package com.c.launcher;

import android.content.Intent;

/**
 * 点播壳：导入用户自己的点播列表（M3U / JSON / 名称,地址 文本），点条目进播放器。
 * 播放器用系统 VideoView，支持 MP4 与 HLS；不内置任何片源。
 */
public class VodShellActivity extends BaseShellActivity {

    @Override protected String prefKey() { return "vod_src"; }

    @Override protected int titleRes() { return R.string.vod_title; }

    @Override
    protected void onItemSelected(PlaylistParser.Item it) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, it.url);
        i.putExtra(PlayerActivity.EXTRA_NAME, it.name);
        i.putExtra(PlayerActivity.EXTRA_LIVE, false);
        startActivity(i);
    }
}
