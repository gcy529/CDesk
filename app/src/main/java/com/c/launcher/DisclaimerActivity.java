package com.c.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * 首次启动的使用须知。只弹一次（记住「我已阅读并同意」）。
 * 内容说清三件事：按现状提供、不内置任何源、内容合规是用户自己的责任。
 */
public class DisclaimerActivity extends Activity {

    private static final String PREFS = "cdesk_disclaimer";
    private static final String KEY_OK = "agreed";

    public static boolean agreed(Activity a) {
        return a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getBoolean(KEY_OK, false);
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LangManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disclaimer);

        TextView body = (TextView) findViewById(R.id.tvDisclaimerBody);
        body.setText(R.string.disclaimer_body);

        Button ok = (Button) findViewById(R.id.btnAgree);
        ok.setText(R.string.disclaimer_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                        .edit().putBoolean(KEY_OK, true).commit();
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // 不同意就不能用：按返回等于退出
        super.onBackPressed();
    }
}
