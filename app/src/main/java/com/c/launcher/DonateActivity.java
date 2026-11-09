package com.c.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * 打赏页。
 *
 * 设计取舍（有意为之，不是偷懒）：
 *  1. 不接任何支付 SDK —— 个人开发者申请不到 App 支付权限，
 *     第三方聚合支付既不合规又会把 APK 撑大好几 MB。
 *     这里只展示静态二维码图片，由系统相机/微信去扫。
 *  2. 不做「打赏解锁功能」—— 技术上无法验证真伪，
 *     且一旦带对价就变成经营行为，性质完全不同。
 *  3. 二维码图片放在 res/drawable-nodpi/，换图即换码，不改代码。
 */
public class DonateActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.apply(this);
        setContentView(R.layout.activity_donate);

        View rootBg = findViewById(R.id.rootDonate);
        WPManager.applyToRoot(this, rootBg);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        // 标题跟随当前撞色，和桌面保持一套视觉
        TextView tvTitle = (TextView) findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setTextColor(ThemeManager.current(this).accent);
        }
    }
}
