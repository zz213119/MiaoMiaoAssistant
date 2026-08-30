package com.example.u7e5f3218e9;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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
import java.util.HashMap;
import java.util.Map;

public class QQAccessibilityService extends AccessibilityService {
    private static final String ID_INPUT = "com.tencent.mobileqq:id/input";
    private static final String ID_SEND = "com.tencent.mobileqq:id/send_btn";
    private static final String TAG = "QQCatSvc";
    private static final int LOG_CAPACITY = 300;
    private static final ArrayDeque<String> LOG_BUFFER = new ArrayDeque<>();
    private static volatile boolean debugLogEnabled = false;

    private static synchronized void appendLog(String msg) {
        if (!debugLogEnabled) return;
        Log.d(TAG, msg);
        String line = DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + "  " + msg;
        LOG_BUFFER.addLast(line);
        while (LOG_BUFFER.size() > LOG_CAPACITY) LOG_BUFFER.removeFirst();
    }

    public static synchronized String getLogSnapshot() {
        if (LOG_BUFFER.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : LOG_BUFFER) sb.append(line).append("\n");
        return sb.toString();
    }

    private CatConfig loadConfigAndSyncLogFlag() {
        CatConfig c = CatConfig.load(this);
        debugLogEnabled = c.enableDebugLog;
        return c;
    }

    private static class PkgState {
        String userOriginal = "";
        String lastProcessedOriginal = "";
        String lastSet = "";
        long lastWriteTime = 0L;
        String lastPolledRaw = "";
    }

    private final Map<String, PkgState> pkgStates = new HashMap<>();

    // 实时模式防抖延迟：100ms（0.1秒）
    private static final long DEBOUNCE_MS = 100L;
    private final Map<String, Runnable> pendingRunnables = new HashMap<>();

    private void scheduleDebouncedProcess(final String pkg) {
        Runnable prev = this.pendingRunnables.get(pkg);
        if (prev != null) this.pollHandler.removeCallbacks(prev);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                QQAccessibilityService.this.pendingRunnables.remove(pkg);
                doProcess(pkg, false);
            }
        };
        this.pendingRunnables.put(pkg, r);
        this.pollHandler.postDelayed(r, DEBOUNCE_MS);
    }

    private void cancelPending(String pkg) {
        Runnable prev = this.pendingRunnables.remove(pkg);
        if (prev != null) this.pollHandler.removeCallbacks(prev);
    }

    private PkgState stateFor(String pkg) {
        PkgState s = this.pkgStates.get(pkg);
        if (s == null) {
            s = new PkgState();
            this.pkgStates.put(pkg, s);
        }
        return s;
    }

    private CatConfig cachedConfig;
    private volatile String currentImePkg = "";

    private void refreshCurrentImePkg() {
        try {
            String ime = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) {
                int slash = ime.indexOf('/');
                this.currentImePkg = slash > 0 ? ime.substring(0, slash) : ime;
            }
        } catch (Exception e) {
        }
    }

    private boolean processing = false;
    private static final long SEND_COOLDOWN_MS = 1500L;
    private long lastSendTime = 0;
    private static final long POLL_INTERVAL_MS = 400L;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollOnce();
            QQAccessibilityService.this.pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private long lastPollDiagTime = 0;
    private long lastEmptyDiagTime = 0;

    private boolean isAppWindow(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            android.view.accessibility.AccessibilityWindowInfo w = node.getWindow();
            if (w == null) return true;
            int type = w.getType();
            w.recycle();
            return type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION;
        } catch (Exception e) {
            return true;
        }
    }

    private void pollOnce() {
        try {
            long now = System.currentTimeMillis();
            if (now - this.lastSendTime < SEND_COOLDOWN_MS) return;
            CatConfig cfg = this.cachedConfig;
            if (cfg == null) {
                cfg = loadConfigAndSyncLogFlag();
                this.cachedConfig = cfg;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            if (!isAppWindow(root)) {
                root.recycle();
                return;
            }
            String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
            if (!cfg.isTargetPackage(pkg) || pkg.equals(this.currentImePkg)) {
                root.recycle();
                return;
            }
            PkgState st = stateFor(pkg);
            AccessibilityNodeInfo focused = findFocusedEditable(root);
            if (focused == null) {
                if (now - this.lastPollDiagTime > 10000L) {
                    this.lastPollDiagTime = now;
                    AccessibilityNodeInfo anyEditable = findAnyEditable(root);
                    if (anyEditable == null) {
                        appendLog("[轮询诊断] pkg=" + pkg + " 界面窗口能读到，但树里连一个isEditable()的控件都没有");
                    } else {
                        appendLog("[轮询诊断] pkg=" + pkg + " 树里找到了可编辑控件(class=" + anyEditable.getClassName() + ")，但它的isFocused()不是true，所以没被当成目标输入框");
                        anyEditable.recycle();
                    }
                }
                root.recycle();
                return;
            }
            if (focused.isShowingHintText()) {
                focused.recycle();
                root.recycle();
                return;
            }
            CharSequence cs = focused.getText();
            focused.recycle();
            root.recycle();
            String raw = cs != null ? cs.toString().trim() : "";
            if (raw.equals(st.lastPolledRaw)) return;
            st.lastPolledRaw = raw;
            if (raw.isEmpty()) return;
            appendLog("[轮询] pkg=" + pkg + " 检测到输入框内容: " + raw);
            String mode = cfg.processingMode != null ? cfg.processingMode : CatConfig.MODE_PUNCTUATION;
            if (CatConfig.MODE_REALTIME.equals(mode)) {
                scheduleDebouncedProcess(pkg);
            } else if (isPunctuationEnding(raw)) {
                appendLog("[轮询] 标点触发: " + raw);
                doProcess(pkg, false);
            }
        } catch (Exception ex) {
            appendLog("[轮询] 异常: " + ex.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        String pkg = e.getPackageName() != null ? e.getPackageName().toString() : "";
        int type = e.getEventType();
        if (type == 32) {
            this.cachedConfig = loadConfigAndSyncLogFlag();
            refreshCurrentImePkg();
        }
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = loadConfigAndSyncLogFlag();
            this.cachedConfig = cfg;
        }
        if (type == 32) {
            appendLog("[窗口切换] pkg=" + pkg + " 全局模式=" + cfg.globalMode + " 微信开关=" + cfg.enableWeChat + " isTargetPackage=" + cfg.isTargetPackage(pkg));
        }
        if (!cfg.isTargetPackage(pkg) || pkg.equals(this.currentImePkg)) return;
        if (type != 32 && System.currentTimeMillis() - this.lastSendTime < SEND_COOLDOWN_MS) return;
        if (type == 32) {
            this.pkgStates.remove(pkg);
            cancelPending(pkg);
            this.processing = false;
            return;
        }
        if (type == 1) {
            AccessibilityNodeInfo src = e.getSource();
            if (src != null) {
                String id = src.getViewIdResourceName();
                if (ID_SEND.equals(id) || isSendLikeNode(src)) {
                    appendLog("点击发送，兜底处理");
                    doProcess(pkg, true);
                }
                src.recycle();
                return;
            }
            return;
        }
        if (type == 16 || type == 2048) {
            AccessibilityNodeInfo src = e.getSource();
            if (src == null) return;
            boolean srcOk = src.isEditable() && src.isFocused() && !src.isShowingHintText() && isAppWindow(src);
            if (!srcOk && type == 2048) {
                AccessibilityNodeInfo rootForCheck = getRootInActiveWindow();
                if (rootForCheck != null) {
                    AccessibilityNodeInfo focusedNode = findFocusedEditable(rootForCheck);
                    if (focusedNode != null) {
                        srcOk = !focusedNode.isShowingHintText();
                        focusedNode.recycle();
                    }
                    rootForCheck.recycle();
                }
            }
            src.recycle();
            if (!srcOk) return;
            String mode = cfg.processingMode != null ? cfg.processingMode : CatConfig.MODE_PUNCTUATION;
            if (CatConfig.MODE_REALTIME.equals(mode)) {
                scheduleDebouncedProcess(pkg);
                return;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            AccessibilityNodeInfo inp = findNodeById(root, ID_INPUT);
            if (inp == null) inp = findEditable(root);
            root.recycle();
            if (inp == null) return;
            CharSequence cs = inp.getText();
            inp.recycle();
            if (cs == null || cs.length() == 0) return;
            String raw = cs.toString().trim();
            if (!raw.isEmpty() && isPunctuationEnding(raw)) {
                appendLog("标点触发: " + raw);
                doProcess(pkg, false);
            }
        }
    }

    private boolean isSendLikeNode(AccessibilityNodeInfo n) {
        if (n == null || !n.isClickable()) return false;
        CharSequence text = n.getText();
        CharSequence desc = n.getContentDescription();
        String t = text != null ? text.toString().trim() : "";
        String d = desc != null ? desc.toString().trim() : "";
        return t.contains("发送") || d.contains("发送") || t.equalsIgnoreCase("send") || d.equalsIgnoreCase("send");
    }

    private boolean isPunctuationEnding(String s) {
        if (s == null || s.isEmpty()) return false;
        char last = s.charAt(s.length() - 1);
        return last == 12290 || last == 65281 || last == '!' || last == 65311 || last == '?' || last == ' ';
    }

    private void doProcess(String pkg, boolean isSendClick) {
        if (this.processing) return;
        if (pkg.equals(this.currentImePkg)) return;
        this.processing = true;
        if (isSendClick) this.lastSendTime = System.currentTimeMillis();
        PkgState st = stateFor(pkg);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            appendLog("[诊断] pkg=" + pkg + " getRootInActiveWindow()返回null");
            this.processing = false;
            return;
        }
        if (!isAppWindow(root)) {
            appendLog("[诊断] pkg=" + pkg + " 当前活动窗口不是应用窗口(可能是输入法/系统悬浮层)，跳过");
            root.recycle();
            this.processing = false;
            return;
        }
        AccessibilityNodeInfo inp = findNodeById(root, ID_INPUT);
        if (inp == null) inp = findEditable(root);
        if (inp == null) {
            appendLog("[诊断] pkg=" + pkg + " 未找到任何可编辑输入框节点");
            root.recycle();
            this.processing = false;
            return;
        }
        if (inp.isShowingHintText()) {
            appendLog("[诊断] pkg=" + pkg + " 当前是占位提示文字，非真实输入，跳过");
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        CharSequence cs = inp.getText();
        if (cs == null || cs.length() == 0) {
            long now2 = System.currentTimeMillis();
            if (now2 - this.lastEmptyDiagTime > 5000L) {
                this.lastEmptyDiagTime = now2;
                appendLog("[诊断] pkg=" + pkg + " 找到输入框但当前文本为空(editable=" + inp.isEditable() + " focused=" + inp.isFocused() + " class=" + inp.getClassName() + ")");
            }
            inp.recycle();
            root.recycle();
            this.processing = false;
            st.userOriginal = "";
            st.lastProcessedOriginal = "";
            st.lastSet = "";
            return;
        }
        String raw = cs.toString().trim();
        if (raw.isEmpty()) {
            inp.recycle();
            root.recycle();
            this.processing = false;
            st.userOriginal = "";
            st.lastProcessedOriginal = "";
            st.lastSet = "";
            return;
        }
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = loadConfigAndSyncLogFlag();
            this.cachedConfig = cfg;
        }
        long now = System.currentTimeMillis();
        long j = st.lastWriteTime;
        if (j > 0 && now - j < 600 && raw.equals(st.lastSet)) {
            appendLog("写入回显跳过");
            st.lastWriteTime = 0L;
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        boolean isRealtime = CatConfig.MODE_REALTIME.equals(cfg.processingMode);
        if (!isRealtime && st.lastSet.isEmpty()) {
            st.userOriginal = stripAll(raw, cfg);
            appendLog("标点首次剥离: " + st.userOriginal);
        } else if (st.lastSet.isEmpty() || !raw.startsWith(st.lastSet)) {
            if (st.lastSet.isEmpty()) {
                st.userOriginal = stripAll(raw, cfg);
                appendLog("首条剥离: " + st.userOriginal);
            } else {
                st.userOriginal = stripAll(raw, cfg);
                appendLog("不匹配剥离: " + st.userOriginal);
            }
        } else {
            String added = raw.substring(st.lastSet.length());
            st.userOriginal += added;
            appendLog("前缀增量: +" + added + "  userOriginal=" + st.userOriginal);
        }
        if (st.userOriginal.isEmpty()) {
            appendLog("原文为空，跳过");
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        if (!isSendClick && st.userOriginal.equals(st.lastProcessedOriginal)) {
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        String target = TextProcessor.process(st.userOriginal, cfg);
        st.lastProcessedOriginal = st.userOriginal;
        if (!target.equals(raw)) {
            appendLog("写入: raw=" + raw + "  userOriginal=" + st.userOriginal + "  target=" + target);
            boolean ok = setText(inp, target);
            appendLog("[诊断] pkg=" + pkg + " 写入调用返回=" + ok + "（如果这里是true但App里没变化，说明是该App拦截了无障碍写入）");
            if (ok) {
                st.lastSet = target;
                st.lastWriteTime = System.currentTimeMillis();
            }
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        st.lastSet = target;
        inp.recycle();
        root.recycle();
        this.processing = false;
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
                int st;
                if (idx <= 0 || result.charAt(idx - 1) != ' ') st = idx;
                else st = idx - 1;
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

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo focused = findFocusedEditable(n);
        if (focused != null) return focused;
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

    private AccessibilityNodeInfo findAnyEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable()) return AccessibilityNodeInfo.obtain(n);
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
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        this.processing = false;
        this.pollHandler.removeCallbacks(this.pollRunnable);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        this.pollHandler.removeCallbacks(this.pollRunnable);
        return super.onUnbind(intent);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo i = new AccessibilityServiceInfo();
        i.eventTypes = 32 | 16 | 1 | 2048;
        i.feedbackType = 16;
        i.flags = 81;
        i.notificationTimeout = 50L;
        i.packageNames = null;
        setServiceInfo(i);
        this.pollHandler.removeCallbacks(this.pollRunnable);
        this.pollHandler.postDelayed(this.pollRunnable, POLL_INTERVAL_MS);
        this.cachedConfig = loadConfigAndSyncLogFlag();
        refreshCurrentImePkg();
        this.pkgStates.clear();
        for (Runnable r : this.pendingRunnables.values()) this.pollHandler.removeCallbacks(r);
        this.pendingRunnables.clear();
    }
}
