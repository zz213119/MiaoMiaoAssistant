package com.example.u7e5f3218e9;

import android.app.Activity;
import android.content.Intent;
import android.graphics.RectF;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 桌面图标点开先看到的伪装页面：直接用真实 vivo 意见反馈的截图当背景图（像素级还原，
 * 比自己拼控件或者用 Canvas 画准确得多），上面盖一层完全透明的触摸层，
 * 按截图里量出来的坐标百分比划定几个可点区域。
 *
 * 坐标百分比是从 1260x2560 的裁剪后截图（去掉了截图里多余的假导航栏图标条）里
 * 用像素检测量出来的，缩放到任何屏幕尺寸都按比例对得上：
 *   历史图标：x[0.714,0.833] y[0.055,0.106]
 *   设置图标：x[0.849,0.952] y[0.055,0.106]
 *   重试按钮：x[0.356,0.644] y[0.515,0.578]
 *   设置网络按钮：x[0.356,0.644] y[0.600,0.663]
 */
public class FeedbackDecoyActivity extends Activity {

    private static final RectF ZONE_HISTORY = new RectF(0.714f, 0.055f, 0.833f, 0.106f);
    private static final RectF ZONE_SETTINGS = new RectF(0.849f, 0.055f, 0.952f, 0.106f);
    private static final RectF ZONE_RETRY = new RectF(0.356f, 0.515f, 0.644f, 0.578f);
    private static final RectF ZONE_NETWORK = new RectF(0.356f, 0.600f, 0.644f, 0.663f);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.feedback_decoy_bg);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // 完全透明的触摸层，盖在背景图上面，根据点击位置的百分比坐标判断点了哪个区域
        View touchLayer = new View(this);
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return true;
                }
                float px = event.getX() / v.getWidth();
                float py = event.getY() / v.getHeight();
                FeedbackDecoyActivity.this.handleTap(px, py);
                return true;
            }
        });
        root.addView(touchLayer, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private void handleTap(float px, float py) {
        if (ZONE_SETTINGS.contains(px, py)) {
            startActivity(new Intent(this, SettingsDecoyActivity.class));
        } else if (ZONE_HISTORY.contains(px, py)) {
            // 伪装用，不做实际操作
        } else if (ZONE_RETRY.contains(px, py)) {
            Toast.makeText(this, "网络异常，请检查网络连接", Toast.LENGTH_SHORT).show();
        } else if (ZONE_NETWORK.contains(px, py)) {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception ignored) {
                // 部分定制系统没有这个页面就算了，纯装饰性按钮
            }
        }
        // 点在空白区域不做任何反应，跟真实页面表现一致
    }
}
