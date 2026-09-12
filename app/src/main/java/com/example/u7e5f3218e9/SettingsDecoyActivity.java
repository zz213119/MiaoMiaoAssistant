package com.example.u7e5f3218e9;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 伪装的"设置"页，长得像 vivo 系统 App 的通用设置样式（异常检测/检查更新/关于）。
 * 真正的机关在"检查更新"这一行：连续点击10下，会打开真正的 MainActivity 配置界面。
 * 其他两行只是摆设，点了没有实际效果，纯粹让页面看起来更完整、不显得单薄。
 */
public class SettingsDecoyActivity extends Activity {

    private static final int UNLOCK_TAP_COUNT = 10;
    private static final long TAP_RESET_INTERVAL_MS = 2000;

    private int updateTapCount = 0;
    private long lastTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F2F2F2"));
        root.setPadding(dp(20), dp(40), dp(20), dp(20));
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

        // 顶部：返回箭头 + "设置"标题
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(24));

        TextView back = new TextView(this);
        back.setText("\u2039");
        back.setTextSize(28.0f);
        back.setTextColor(Color.BLACK);
        back.setPadding(dp(4), 0, dp(16), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(20.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.BLACK);
        topBar.addView(title);

        root.addView(topBar);

        // 白色圆角卡片，里面三行
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(12));
        card.setBackground(cardBg);

        card.addView(settingsRow("异常检测", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(SettingsDecoyActivity.this, "暂未检测到异常", Toast.LENGTH_SHORT).show();
            }
        }));
        card.addView(divider());
        card.addView(settingsRow("检查更新", "V5.3.4.5", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsDecoyActivity.this.onCheckUpdateTapped();
            }
        }));
        card.addView(divider());
        card.addView(settingsRow("关于", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(SettingsDecoyActivity.this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
            }
        }));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(16), 0, 0);
        root.addView(card, cardLp);

        setContentView(root);
    }

    /** 检查更新连点10下解锁真实设置界面；超过间隔没有继续点就重新计数，避免误触长期累积生效 */
    private void onCheckUpdateTapped() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime > TAP_RESET_INTERVAL_MS) {
            updateTapCount = 0;
        }
        lastTapTime = now;
        updateTapCount++;

        if (updateTapCount >= UNLOCK_TAP_COUNT) {
            updateTapCount = 0;
            startActivity(new Intent(this, MainActivity.class));
        }
        // 中间过程不给任何提示，避免暴露有隐藏机关
    }

    private View settingsRow(final String label, final String valueText, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(20), dp(20), dp(20));
        row.setClickable(true);
        row.setOnClickListener(listener);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16.0f);
        labelView.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(labelView, labelLp);

        if (valueText != null) {
            TextView valueView = new TextView(this);
            valueView.setText(valueText);
            valueView.setTextSize(14.0f);
            valueView.setTextColor(Color.GRAY);
            valueView.setPadding(0, 0, dp(8), 0);
            row.addView(valueView);
        }

        TextView arrow = new TextView(this);
        arrow.setText("\u203A");
        arrow.setTextSize(18.0f);
        arrow.setTextColor(Color.LTGRAY);
        row.addView(arrow);

        return row;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#EAEAEA"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(20), 0, dp(20), 0);
        line.setLayoutParams(lp);
        return line;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
