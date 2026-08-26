package com.example.u7e5f3218e9;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;

public class QQAccessibilityService extends AccessibilityService {
    private static final String ID_INPUT = "com.tencent.mobileqq:id/input";
    private static final String ID_SEND = "com.tencent.mobileqq:id/send_btn";
    private static final String TAG = "QQCatSvc";
    private static final int LOG_CAPACITY = 300;
    private static final long SEND_COOLDOWN_MS = 1500L;
    private static final long POLL_INTERVAL_MS = 400L;

    private static final ArrayDeque<String> LOG_BUFFER = new ArrayDeque<>();

    private CatConfig cachedConfig;
    private String userOriginal = "";
    private String lastSet = "";
    private boolean processing = false;
    private long lastWriteTime = 0L;
    private long lastSendTime = 0L;
    private String trackedPkg = "";
    private String lastPolledRaw = "";

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollOnce();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private static synchronized void appendLog(String msg) {
        Log.d(TAG, msg);
        String line = DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + "  " + msg;
        LOG_BUFFER.addLast(line);
        while (LOG_BUFFER.size() > LOG_CAPACITY) {
            LOG_BUFFER.removeFirst();
        }
    }

    public static synchronized String getLogSnapshot() {
        if (LOG_BUFFER.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : LOG_BUFFER) sb.append(line).append('\n');
        return sb.toString();
    }

    private void pollOnce() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastSendTime < SEND_COOLDOWN_MS) return;

            CatConfig cfg = cachedConfig;
            if (cfg == null) {
                cfg = CatConfig.load(this);
                cachedConfig = cfg;
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (!cfg.isTargetPackage(pkg)) {
                root.recycle();
                return;
            }

            resetTrackingIfPackageChanged(pkg);

            // 微信等 App 的输入框有时不是 Accessibility 的 focused 节点。
            // 这里不再只调用 findFocusedEditable，而是使用带兜底的 findEditable。
            AccessibilityNodeInfo input = findEditable(root);
            if (input == null) {
                appendLog("[轮询] pkg=" + pkg + " 未找到可编辑输入框");
                root.recycle();
                return;
            }
            if (input.isShowingHintText()) {
                input.recycle();
                root.recycle();
                return;
            }

            CharSequence cs = input.getText();
            boolean editable = input.isEditable();
            boolean focused = input.isFocused();
            String className = input.getClassName() == null ? "" : input.getClassName().toString();
            input.recycle();
            root.recycle();

            String raw = cs == null ? "" : cs.toString().trim();
            if (raw.equals(lastPolledRaw)) return;
            lastPolledRaw = raw;
            if (raw.isEmpty()) return;

            appendLog("[轮询] pkg=" + pkg + " 检测到输入框内容: " + raw
                    + " editable=" + editable + " focused=" + focused + " class=" + className);

            String mode = cfg.processingMode == null ? CatConfig.MODE_PUNCTUATION : cfg.processingMode;
            if (CatConfig.MODE_REALTIME.equals(mode)) {
                doProcess(false);
            } else if (isPunctuationEnding(raw)) {
                appendLog("[轮询] 标点触发: " + raw);
                doProcess(false);
            }
        } catch (Exception ex) {
            appendLog("[轮询] 异常: " + ex.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        String pkg = e.getPackageName() == null ? "" : e.getPackageName().toString();
        int type = e.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            cachedConfig = CatConfig.load(this);
        }
        CatConfig cfg = cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            cachedConfig = cfg;
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            appendLog("[窗口切换] pkg=" + pkg + " 全局模式=" + cfg.globalMode
                    + " 微信开关=" + cfg.enableWeChat
                    + " isTargetPackage=" + cfg.isTargetPackage(pkg));
        }
        if (!cfg.isTargetPackage(pkg)) return;
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && System.currentTimeMillis() - lastSendTime < SEND_COOLDOWN_MS) return;

        resetTrackingIfPackageChanged(pkg);

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            processing = false;
            userOriginal = "";
            lastSet = "";
            lastWriteTime = 0L;
            lastPolledRaw = "";
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo src = e.getSource();
            if (src != null) {
                boolean sendLike = isSendLikeNode(src);
                if (!sendLike) sendLike = hasSendLikeAncestor(src);
                if (!sendLike && isLikelyWeChatSendClick(src)) {
                    sendLike = true;
                }
                appendLog("[点击] pkg=" + pkg + " id=" + safe(src.getViewIdResourceName())
                        + " text=" + safe(src.getText())
                        + " desc=" + safe(src.getContentDescription())
                        + " sendLike=" + sendLike);
                if (sendLike) {
                    appendLog("点击发送，兜底处理");
                    doProcess(true);
                }
                src.recycle();
            }
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            AccessibilityNodeInfo src = e.getSource();
            boolean srcOk = src != null && src.isEditable() && !src.isShowingHintText();
            if (!srcOk && type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    AccessibilityNodeInfo input = findEditable(root);
                    if (input != null) {
                        srcOk = !input.isShowingHintText();
                        input.recycle();
                    }
                    root.recycle();
                }
            }
            if (src != null) src.recycle();
            if (!srcOk) return;

            String mode = cfg.processingMode == null ? CatConfig.MODE_PUNCTUATION : cfg.processingMode;
            if (CatConfig.MODE_REALTIME.equals(mode)) {
                doProcess(false);
                return;
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            AccessibilityNodeInfo input = findEditable(root);
            if (input == null) {
                appendLog("[事件] pkg=" + pkg + " 未找到可编辑输入框");
                root.recycle();
                return;
            }
            CharSequence cs = input.getText();
            input.recycle();
            root.recycle();
            String raw = cs == null ? "" : cs.toString().trim();
            if (!raw.isEmpty() && isPunctuationEnding(raw)) {
                appendLog("标点触发: " + raw);
                doProcess(false);
            }
        }
    }

    private void resetTrackingIfPackageChanged(String pkg) {
        if (!pkg.equals(trackedPkg)) {
            trackedPkg = pkg;
            processing = false;
            userOriginal = "";
            lastSet = "";
            lastWriteTime = 0L;
            lastPolledRaw = "";
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isSendLikeNode(AccessibilityNodeInfo n) {
        if (n == null || !n.isClickable()) return false;
        String t = safe(n.getText());
        String d = safe(n.getContentDescription());
        String id = safe(n.getViewIdResourceName());
        String all = (t + " " + d + " " + id).toLowerCase();
        return t.contains("发送") || d.contains("发送")
                || t.equalsIgnoreCase("send") || d.equalsIgnoreCase("send")
                || all.contains("send") || all.contains("send_button") || all.contains("sendbtn");
    }

    private boolean hasSendLikeAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 6 && current != null; i++) {
            if (i > 0 && isSendLikeNode(current)) return true;
            AccessibilityNodeInfo parent = current.getParent();
            if (i > 0) current.recycle();
            current = parent;
        }
        if (current != null) current.recycle();
        return false;
    }

    /**
     * 微信部分版本的发送按钮可能没有可访问文本。这里用“点击位置在输入框右侧、
     * 同一水平区域、且当前确实存在文本输入框”作为弱兜底，只对微信启用，避免影响 QQ/抖音/快手。
     */
    private boolean isLikelyWeChatSendClick(AccessibilityNodeInfo clicked) {
        if (!CatConfig.PKG_WECHAT.equals(trackedPkg) || clicked == null || !clicked.isClickable()) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo input = findEditable(root);
        if (input == null) {
            root.recycle();
            return false;
        }
        CharSequence text = input.getText();
        if (text == null || text.toString().trim().isEmpty()) {
            input.recycle();
            root.recycle();
            return false;
        }
        Rect inputRect = new Rect();
        Rect clickRect = new Rect();
        input.getBoundsInScreen(inputRect);
        clicked.getBoundsInScreen(clickRect);
        input.recycle();
        root.recycle();

        int inputCenterY = (inputRect.top + inputRect.bottom) / 2;
        int clickCenterY = (clickRect.top + clickRect.bottom) / 2;
        boolean sameRow = Math.abs(inputCenterY - clickCenterY) <= Math.max(80, inputRect.height());
        boolean rightSide = clickRect.left >= inputRect.right - Math.max(24, inputRect.width() / 5);
        boolean notInput = clickRect.left > inputRect.left + 20;
        return sameRow && rightSide && notInput;
    }

    private boolean isPunctuationEnding(String s) {
        if (s == null || s.isEmpty()) return false;
        char last = s.charAt(s.length() - 1);
        return last == 12290 || last == 65281 || last == '!' || last == 65311 || last == '?' || last == ' ';
    }

    private void doProcess(boolean isSendClick) {
        if (processing) return;
        processing = true;
        if (isSendClick) lastSendTime = System.currentTimeMillis();

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            appendLog("[诊断] pkg=" + trackedPkg + " getRootInActiveWindow()返回null");
            processing = false;
            return;
        }

        AccessibilityNodeInfo inp = findEditable(root);
        if (inp == null) {
            appendLog("[诊断] pkg=" + trackedPkg + " 未找到任何可编辑输入框节点");
            root.recycle();
            processing = false;
            return;
        }
        if (inp.isShowingHintText()) {
            appendLog("[诊断] pkg=" + trackedPkg + " 当前是占位提示文字，非真实输入，跳过");
            inp.recycle();
            root.recycle();
            processing = false;
            return;
        }

        CharSequence cs = inp.getText();
        if (cs == null || cs.length() == 0) {
            appendLog("[诊断] pkg=" + trackedPkg + " 找到输入框但当前文本为空(editable="
                    + inp.isEditable() + " focused=" + inp.isFocused() + " class=" + inp.getClassName() + ")");
            inp.recycle();
            root.recycle();
            processing = false;
            userOriginal = "";
            lastSet = "";
            return;
        }

        String raw = cs.toString().trim();
        if (raw.isEmpty()) {
            inp.recycle();
            root.recycle();
            processing = false;
            userOriginal = "";
            lastSet = "";
            return;
        }

        CatConfig cfg = cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            cachedConfig = cfg;
        }

        long now = System.currentTimeMillis();
        if (lastWriteTime > 0 && now - lastWriteTime < 600 && raw.equals(lastSet)) {
            appendLog("写入回显跳过");
            lastWriteTime = 0L;
            inp.recycle();
            root.recycle();
            processing = false;
            return;
        }

        boolean isRealtime = CatConfig.MODE_REALTIME.equals(cfg.processingMode);
        if (!isRealtime && lastSet.isEmpty()) {
            userOriginal = stripAll(raw, cfg);
            appendLog("首条剥离: " + userOriginal);
        } else if (lastSet.isEmpty() || !raw.startsWith(lastSet)) {
            userOriginal = stripAll(raw, cfg);
            appendLog((lastSet.isEmpty() ? "首条" : "不匹配") + "剥离: " + userOriginal);
        } else {
            String added = raw.substring(lastSet.length());
            userOriginal += added;
            appendLog("前缀增量: +" + added + "  userOriginal=" + userOriginal);
        }

        if (userOriginal.isEmpty()) {
            appendLog("原文为空，跳过");
            inp.recycle();
            root.recycle();
            processing = false;
            return;
        }

        CatConfig effectiveCfg = cfg;
        if (isRealtime && cfg.enableRandomEmoticon && !isSendClick) {
            effectiveCfg = cloneConfigWithoutEmoticon(cfg);
        }

        String target = TextProcessor.process(userOriginal, effectiveCfg);
        if (!target.equals(raw)) {
            appendLog("写入: raw=" + raw + "  userOriginal=" + userOriginal + "  target=" + target);
            boolean ok = setText(inp, target);
            appendLog("[诊断] pkg=" + trackedPkg + " 写入调用返回=" + ok);
            if (ok) {
                lastSet = target;
                lastWriteTime = System.currentTimeMillis();
            }
        } else {
            lastSet = target;
        }

        inp.recycle();
        root.recycle();
        processing = false;
    }

    private CatConfig cloneConfigWithoutEmoticon(CatConfig src) {
        CatConfig c = new CatConfig();
        c.enableAppend = src.enableAppend;
        c.appendText = src.appendText;
        c.enableRandomEmoticon = false;
        c.processingMode = src.processingMode;
        c.customEmoticons = src.customEmoticons;
        c.rules = src.rules;
        c.enableQQ = src.enableQQ;
        c.enableWeChat = src.enableWeChat;
        c.enableDouyin = src.enableDouyin;
        c.enableKuaishou = src.enableKuaishou;
        c.globalMode = src.globalMode;
        c.customPackages = src.customPackages;
        return c;
    }

    private String stripAll(String text, CatConfig cfg) {
        if (text == null || text.isEmpty()) return "";
        String result = text;
        String[] emotes = cfg.getActiveEmoticons();
        if (emotes.length == 0) emotes = CatConfig.BUILTIN_EMOTICONS;

        String[] toStrip = emotes;
        if (cfg.enableAppend && cfg.appendText != null && !cfg.appendText.isEmpty()) {
            String[] combined = new String[emotes.length + 1];
            System.arraycopy(emotes, 0, combined, 0, emotes.length);
            combined[emotes.length] = cfg.appendText;
            toStrip = combined;
        }

        Arrays.sort(toStrip, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });

        for (String em : toStrip) {
            if (em == null || em.isEmpty()) continue;
            int idx;
            while ((idx = result.indexOf(em)) >= 0) {
                int st = (idx <= 0 || result.charAt(idx - 1) != ' ') ? idx : idx - 1;
                result = result.substring(0, st) + result.substring(idx + em.length());
            }
        }
        return result.replaceAll("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*", " ").trim();
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo n, String id) {
        if (n == null || id == null) return null;
        if (id.equals(n.getViewIdResourceName())) return AccessibilityNodeInfo.obtain(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findNodeById(c, id);
                c.recycle();
                if (r != null) return r;
            }
        }
        return null;
    }

    /** 优先 focused editable，找不到时再递归寻找任意可编辑控件。 */
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;

        AccessibilityNodeInfo focused = findFocusedEditable(n);
        if (focused != null) return focused;

        AccessibilityNodeInfo best = findBestEditable(n);
        if (best != null) return best;
        return findAnyEditable(n);
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.isFocused()) return AccessibilityNodeInfo.obtain(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findFocusedEditable(c);
                c.recycle();
                if (r != null) return r;
            }
        }
        return null;
    }

    /** 倾向于已经有文本、可编辑且可见的节点，降低误抓隐藏输入框的概率。 */
    private AccessibilityNodeInfo findBestEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && !n.isShowingHintText()) {
            CharSequence t = n.getText();
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (t != null && t.length() > 0 && !r.isEmpty()) return AccessibilityNodeInfo.obtain(n);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findBestEditable(c);
                c.recycle();
                if (r != null) return r;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findAnyEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && !n.isShowingHintText()) return AccessibilityNodeInfo.obtain(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findAnyEditable(c);
                c.recycle();
                if (r != null) return r;
            }
        }
        return null;
    }

    private boolean setText(AccessibilityNodeInfo n, String t) {
        if (n == null) return false;
        try {
            Bundle b = new Bundle();
            b.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", t);
            boolean ok = n.performAction(2097152, b);
            if (ok) {
                Bundle a = new Bundle();
                a.putInt("ACTION_ARGUMENT_SELECTION_START_INT", t.length());
                a.putInt("ACTION_ARGUMENT_SELECTION_END_INT", t.length());
                n.performAction(131072, a);
            }
            return ok;
        } catch (Exception e) {
            appendLog("[诊断] setText异常=" + e.getMessage());
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        processing = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        pollHandler.removeCallbacks(pollRunnable);
        return super.onUnbind(intent);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo i = new AccessibilityServiceInfo();
        i.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        i.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        i.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        i.notificationTimeout = 50L;
        i.packageNames = null;
        setServiceInfo(i);
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        cachedConfig = CatConfig.load(this);
        appendLog("无障碍服务已连接，开始轮询");
    }
}
