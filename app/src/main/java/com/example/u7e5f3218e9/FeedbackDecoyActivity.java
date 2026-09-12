package com.example.u7e5f3218e9;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 桌面图标点开先看到的伪装页面：用真实 vivo 意见反馈截图当背景图。
 *
 * 启动时先显示与参考截图一致的“正在加载”画面，并使用自绘动画模拟加载中的
 * 深蓝灰圆环 + 粉红色移动指示点；加载结束后自动露出原来的反馈异常页面。
 * 触摸区域仍按截图百分比坐标处理，因此不同屏幕尺寸下都能保持比例一致。
 */
public class FeedbackDecoyActivity extends Activity {

    private static final RectF ZONE_HISTORY = new RectF(0.714f, 0.055f, 0.833f, 0.106f);
    private static final RectF ZONE_SETTINGS = new RectF(0.849f, 0.055f, 0.952f, 0.106f);
    private static final RectF ZONE_RETRY = new RectF(0.356f, 0.515f, 0.644f, 0.578f);
    private static final RectF ZONE_NETWORK = new RectF(0.356f, 0.600f, 0.644f, 0.663f);

    // 参考截图中的加载动画显示时长。结束后恢复原来的伪装反馈页面。
    private static final long LOADING_DURATION_MS = 1500L;

    private LoadingOverlay loadingOverlay;
    private final android.os.Handler loadingHandler = new android.os.Handler();
    private final Runnable finishLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            if (loadingOverlay != null) {
                loadingOverlay.stop();
                loadingOverlay.setVisibility(View.GONE);
                loadingOverlay = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.feedback_decoy_bg);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // 真实页面交互层
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

        // 最顶层加载动画：启动后先显示 1.5 秒，期间会遮住背景中的错误提示/按钮，
        // 形成参考图中的纯空白加载状态。
        loadingOverlay = new LoadingOverlay(this);
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        loadingOverlay.start();
        loadingHandler.postDelayed(finishLoadingRunnable, LOADING_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        loadingHandler.removeCallbacks(finishLoadingRunnable);
        if (loadingOverlay != null) {
            loadingOverlay.stop();
        }
        super.onDestroy();
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

    /**
     * 参考图中的“正在加载”动画。
     * 使用 View + Canvas 而不是系统 ProgressBar，避免不同 Android 版本默认样式不一致。
     */
    private static final class LoadingOverlay extends View {
        private static final int BG_COLOR = Color.rgb(242, 242, 244);
        private static final int RING_COLOR = Color.rgb(58, 69, 94);
        private static final int TEXT_COLOR = Color.rgb(151, 151, 151);
        private static final int DOT_COLOR = Color.rgb(255, 76, 105);

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float angle = -120f;
        private ValueAnimator animator;

        LoadingOverlay(Activity context) {
            super(context);
            setClickable(true);
            setFocusable(true);

            paint.setStyle(Paint.Style.FILL);
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(TEXT_COLOR);
            textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        }

        void start() {
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(-120f, 240f);
            animator.setDuration(1150L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    angle = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }

        void stop() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            final float w = getWidth();
            final float h = getHeight();

            // 覆盖背景图中原本的错误提示/按钮，只保留与参考截图一致的浅灰背景。
            // 使用归一化坐标，适配不同分辨率和屏幕比例。
            float maskLeft = w * 0.40f;
            float maskRight = w * 0.60f;
            float maskTop = h * 0.40f;
            float maskBottom = h * 0.68f;
            paint.setColor(BG_COLOR);
            canvas.drawRect(maskLeft, maskTop, maskRight, maskBottom, paint);

            // 加载图标中心位置：与参考截图的视觉中心一致。
            float cx = w * 0.50f;
            float cy = h * 0.485f;
            float radius = w * 0.0208f;
            float stroke = w * 0.0071f;

            // 深蓝灰圆环
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(RING_COLOR);
            canvas.drawCircle(cx, cy, radius, paint);

            // 粉红色小指示点：沿圆环外圈运动，初始位置就是参考截图的左上方。
            float orbit = radius + w * 0.010f;
            double rad = Math.toRadians(angle);
            float dotCx = cx + (float) Math.cos(rad) * orbit;
            float dotCy = cy + (float) Math.sin(rad) * orbit;
            float dotWidth = w * 0.0145f;
            float dotHeight = w * 0.0082f;

            canvas.save();
            canvas.rotate(angle + 90f, dotCx, dotCy);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(DOT_COLOR);
            canvas.drawRoundRect(
                    dotCx - dotWidth / 2f,
                    dotCy - dotHeight / 2f,
                    dotCx + dotWidth / 2f,
                    dotCy + dotHeight / 2f,
                    dotHeight,
                    dotHeight,
                    paint);
            canvas.restore();

            // “正在加载”文字
            textPaint.setTextSize(w * 0.030f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(TEXT_COLOR);
            canvas.drawText("正在加载", cx, cy + w * 0.063f, textPaint);
        }
    }
}
