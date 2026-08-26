package com.example.u7e5f3218e9;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental service used only to answer one question:
 * can Shizuku's shell-side uiautomator see the WeChat input/send controls
 * even though the normal AccessibilityService cannot?
 */
public class WeChatShizukuService extends AccessibilityService {
    private static final String TAG = "WeChatShizuku";
    private static final String WECHAT = "com.tencent.mm";
    private static final long POLL_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            dumpWeChatUi();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private void log(String s) {
        android.util.Log.d(TAG, s);
        // Reuse the existing in-app log window so the user does not need logcat.
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
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        info.notificationTimeout = 50L;
        info.packageNames = new String[]{WECHAT};
        setServiceInfo(info);

        log("实验服务已连接，Shizuku=" + ShizukuBridge.isAvailable()
                + " permission=" + ShizukuBridge.hasPermission());
        if (ShizukuBridge.isAvailable() && !ShizukuBridge.hasPermission()) {
            log("未获得Shizuku权限，请在Shizuku中给本应用授权");
            ShizukuBridge.requestPermission();
        }
        handler.removeCallbacks(poll);
        handler.post(poll);
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

    private void dumpWeChatUi() {
        if (!ShizukuBridge.hasPermission()) {
            return;
        }
        try {
            String result = ShizukuBridge.runShell("sh", "-c",
                    "rm -f /sdcard/qwq_wechat_ui.xml; "
                            + "uiautomator dump /sdcard/qwq_wechat_ui.xml >/sdcard/qwq_dump_log.txt 2>&1; "
                            + "cat /sdcard/qwq_dump_log.txt; "
                            + "echo __XML_BEGIN__; "
                            + "cat /sdcard/qwq_wechat_ui.xml; "
                            + "echo __XML_END__");
            if (result == null || result.isEmpty()) {
                log("uiautomator 没有返回数据");
                return;
            }

            int xmlStart = result.indexOf("__XML_BEGIN__");
            int xmlEnd = result.indexOf("__XML_END__");
            if (xmlStart < 0 || xmlEnd <= xmlStart) {
                log("uiautomator dump失败/没有XML: " + shorten(result));
                return;
            }
            String xml = result.substring(xmlStart + "__XML_BEGIN__".length(), xmlEnd).trim();
            log("uiautomator XML长度=" + xml.length());

            int inputs = 0;
            Matcher nodeMatcher = Pattern.compile("<node\\b[^>]*\\bclass=\\\"([^\\\"]*)\\\"[^>]*>").matcher(xml);
            while (nodeMatcher.find()) {
                String node = nodeMatcher.group();
                String cls = nodeMatcher.group(1);
                if (cls.contains("EditText") || cls.contains("TextView")) {
                    String text = attr(node, "text");
                    String desc = attr(node, "content-desc");
                    String resource = attr(node, "resource-id");
                    String clickable = attr(node, "clickable");
                    String focused = attr(node, "focused");
                    if (cls.contains("EditText") || !text.isEmpty() || !desc.isEmpty()) {
                        log("UI节点 class=" + cls + " text=" + text + " desc=" + desc
                                + " id=" + resource + " clickable=" + clickable + " focused=" + focused);
                    }
                }
                if (cls.contains("EditText")) {
                    inputs++;
                }
            }
            log("uiautomator检测到EditText数量=" + inputs);
        } catch (Throwable e) {
            log("uiautomator异常: " + e);
        }
    }

    private String attr(String node, String name) {
        Pattern p = Pattern.compile(name + "=\\\"([^\\\"]*)\\\"");
        Matcher m = p.matcher(node);
        return m.find() ? m.group(1) : "";
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v).replace('\n', ' ');
    }

    private String shorten(String s) {
        s = s.replace('\n', ' ');
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(poll);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        handler.removeCallbacks(poll);
        return super.onUnbind(intent);
    }
}
