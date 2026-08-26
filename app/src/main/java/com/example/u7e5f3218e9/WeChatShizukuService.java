package com.example.u7e5f3218e9;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Legacy experimental accessibility service kept for compatibility.
 * The actual privileged UI dump now runs through the isolated Shizuku UserService.
 */
public class WeChatShizukuService extends AccessibilityService {
    private static final String TAG = "WeChatShizuku";
    private static final String WECHAT = "com.tencent.mm";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private void log(String s) {
        android.util.Log.d(TAG, s);
        try {
            java.lang.reflect.Method m = QQAccessibilityService.class.getDeclaredMethod("appendLog", String.class);
            m.setAccessible(true);
            m.invoke(null, "[Shizuku] " + s);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 50L;
        info.packageNames = new String[]{WECHAT};
        setServiceInfo(info);

        log("实验服务已连接，Shizuku=" + ShizukuBridge.isAvailable()
                + " permission=" + ShizukuBridge.hasPermission());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return;
        }
        AccessibilityNodeInfo src = event.getSource();
        if (src == null) {
            log("微信点击事件没有source节点");
            return;
        }
        try {
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            log("微信点击: class=" + safe(src.getClassName())
                    + " id=" + safe(src.getViewIdResourceName())
                    + " text=" + safe(src.getText())
                    + " desc=" + safe(src.getContentDescription())
                    + " clickable=" + src.isClickable()
                    + " bounds=" + r.flattenToString());
        } finally {
            src.recycle();
        }
    }

    /**
     * Trigger one isolated Shizuku UserService dump. No shell execution happens
     * inside this accessibility service.
     */
    public void dumpWeChatUi() {
        if (!ShizukuBridge.hasPermission()) {
            log("Shizuku未授权");
            return;
        }
        log("开始通过独立UserService执行uiautomator dump");
        ShizukuBridge.dumpUi(new ShizukuBridge.DumpCallback() {
            @Override
            public void onResult(String result) {
                log(result == null ? "<null>" : result);
            }
        });
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v).replace('\n', ' ');
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }
}
