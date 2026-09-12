package com.example.u7e5f3218e9;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 桌面图标点开先看到的伪装页面，长得像 vivo 系统自带的"意见反馈"App，
 * 显示一个"网络异常"占位页。真正的设置界面藏在这后面，
 * 从右上角齿轮进 {@link SettingsDecoyActivity}，在那里连点10下"检查更新"才能解锁。
 */
public class FeedbackDecoyActivity extends Activity {

    private static final int BLUE = Color.parseColor("#1E88F0");
    private static final int GRAY_TEXT = Color.parseColor("#8A8A8A");
    private static final int LIGHT_GRAY = Color.parseColor("#DADADA");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

        // 顶部标题 + 右上角两个图标
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("意见反馈");
        title.setTextSize(24.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        topBar.addView(title, titleLp);

        TextView historyIcon = new TextView(this);
        historyIcon.setText("\u21BA");
        historyIcon.setTextSize(22.0f);
        historyIcon.setTextColor(Color.DKGRAY);
        historyIcon.setPadding(dp(12), dp(4), dp(12), dp(4));
        historyIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 伪装用，不做实际操作
            }
        });
        topBar.addView(historyIcon);

        TextView settingsIcon = new TextView(this);
        settingsIcon.setText("\u2699");
        settingsIcon.setTextSize(22.0f);
        settingsIcon.setTextColor(Color.DKGRAY);
        settingsIcon.setPadding(dp(12), dp(4), dp(4), dp(4));
        settingsIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FeedbackDecoyActivity.this, SettingsDecoyActivity.class));
            }
        });
        topBar.addView(settingsIcon);

        root.addView(topBar);

        // 中间内容区：网络异常图标 + 文字 + 两个按钮，垂直居中
        LinearLayout centerArea = new LinearLayout(this);
        centerArea.setOrientation(LinearLayout.VERTICAL);
        centerArea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(centerArea, centerLp);

        NetworkErrorIconView icon = new NetworkErrorIconView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(140), dp(100));
        iconLp.setMargins(0, 0, 0, dp(16));
        centerArea.addView(icon, iconLp);

        TextView msg = new TextView(this);
        msg.setText("网络异常，请检查网络连接");
        msg.setTextSize(15.0f);
        msg.setTextColor(GRAY_TEXT);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-2, -2);
        msgLp.setMargins(0, 0, 0, dp(40));
        centerArea.addView(msg, msgLp);

        Button retryBtn = pillButton("重试");
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(dp(200), dp(48));
        retryLp.setMargins(0, 0, 0, dp(16));
        retryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(FeedbackDecoyActivity.this, "网络异常，请检查网络连接", Toast.LENGTH_SHORT).show();
            }
        });
        centerArea.addView(retryBtn, retryLp);

        Button networkBtn = pillButton("设置网络");
        LinearLayout.LayoutParams networkLp = new LinearLayout.LayoutParams(dp(200), dp(48));
        networkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception ignored) {
                    // 部分定制系统没有这个页面就算了，纯装饰性按钮
                }
            }
        });
        centerArea.addView(networkBtn, networkLp);

        setContentView(root);
    }

    private Button pillButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(BLUE);
        button.setTextSize(15.0f);
        button.setAllCaps(false);
        button.setBackground(pillBackground());
        return button;
    }

    private GradientDrawable pillBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(dp(1), BLUE);
        drawable.setCornerRadius(dp(24));
        return drawable;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /** 简单画一个"信号+感叹号"的网络异常图标，不追求跟系统图标像素级一致，够以假乱真就行 */
    private static class NetworkErrorIconView extends View {
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NetworkErrorIconView(android.content.Context context) {
            super(context);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setColor(Color.parseColor("#C9C9C9"));
            redPaint.setColor(Color.parseColor("#F23C3C"));
            redPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(Color.parseColor("#4A4A4A"));
            dotPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float strokeWidth = h * 0.09f;
            arcPaint.setStrokeWidth(strokeWidth);

            float cx = w / 2f;
            float cy = h * 0.95f;
            float radius = h * 0.65f;
            RectF arcRect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(arcRect, 210, 120, false, arcPaint);

            // 感叹号：竖条 + 圆点，画在弧形缺口正中间
            float exWidth = strokeWidth * 0.8f;
            float exTop = cy - radius * 0.55f;
            float exBottom = cy - radius * 0.15f;
            RectF exRect = new RectF(cx - exWidth / 2f, exTop, cx + exWidth / 2f, exBottom);
            canvas.drawRoundRect(exRect, exWidth / 2f, exWidth / 2f, redPaint);
            canvas.drawCircle(cx, exBottom + exWidth * 1.2f, exWidth / 2f, redPaint);

            // 底下一个小圆点，模仿系统"无网络"图标的基站点
            canvas.drawCircle(cx, cy + strokeWidth * 1.5f, strokeWidth * 0.5f, dotPaint);
        }
    }
}
