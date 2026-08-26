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

public class QQAccessibilityService extends AccessibilityService {
    // 仅QQ有稳定公开的资源ID，作为快速路径；其余App一律走通用查找(findEditable)兜底
    private static final String ID_INPUT = "com.tencent.mobileqq:id/input";
    private static final String ID_SEND = "com.tencent.mobileqq:id/send_btn";
    private static final String TAG = "QQCatSvc";
    // 内存日志缓冲区：不依赖系统logcat（部分定制系统会限制App读取自身logcat），
    // 直接把日志存在进程内存里，供设置界面的"查看运行日志"按钮读取，跨机型更可靠。
    private static final int LOG_CAPACITY = 300;
    private static final ArrayDeque<String> LOG_BUFFER = new ArrayDeque<>();

    private static synchronized void appendLog(String msg) {
        Log.d(TAG, msg);
        String line = DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + "  " + msg;
        LOG_BUFFER.addLast(line);
        while (LOG_BUFFER.size() > LOG_CAPACITY) {
            LOG_BUFFER.removeFirst();
        }
    }

    public static synchronized String getLogSnapshot() {
        if (LOG_BUFFER.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : LOG_BUFFER) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private CatConfig cachedConfig;
    private String userOriginal = "";
    private String lastSet = "";
    private boolean processing = false;
    private long lastWriteTime = 0;
    // 发送后的"冷静期"：发送动作触发后的这段时间里，即使收到再多事件通知
    // 也先不处理，把节奏让给宿主App自己走完"读取文字→发送→清空输入框"的流程，
    // 避免我们的重复写入跟它自己的发送逻辑撞在一起，把同一句话拆成好几条发出去。
    private static final long SEND_COOLDOWN_MS = 1500L;
    private long lastSendTime = 0;
    private String trackedPkg = "";
    // 轮询兜底：微信这类App可能完全不上报"内容变化"这类无障碍事件通知
    // （推测是出于防自动化考虑，QQ/抖音/快手目前实测都会正常上报，微信不会）。
    // 既然被动等通知等不到，就换成主动定时去查——不管有没有收到事件，
    // 每400ms自己去读一次当前输入框的文字，这样即使宿主App不推送通知，
    // 只要它的界面结构本身还能被正常查询到，就有机会绕过去。
    private static final long POLL_INTERVAL_MS = 400L;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private String lastPolledRaw = "";
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollOnce();
            QQAccessibilityService.this.pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private void pollOnce() {
        try {
            long now = System.currentTimeMillis();
            if (now - this.lastSendTime < SEND_COOLDOWN_MS) {
                return;
            }
            CatConfig cfg = this.cachedConfig;
            if (cfg == null) {
                cfg = CatConfig.load(this);
                this.cachedConfig = cfg;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
            if (!cfg.isTargetPackage(pkg)) {
                root.recycle();
                return;
            }
            if (!pkg.equals(this.trackedPkg)) {
                this.trackedPkg = pkg;
                this.processing = false;
                this.userOriginal = "";
                this.lastSet = "";
                this.lastWriteTime = 0L;
                this.lastPolledRaw = "";
            }
            AccessibilityNodeInfo focused = findFocusedEditable(root);
            if (focused == null) {
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
            if (raw.equals(this.lastPolledRaw)) {
                return;
            }
            this.lastPolledRaw = raw;
            if (raw.isEmpty()) {
                return;
            }
            appendLog("[轮询] pkg=" + pkg + " 检测到输入框内容: " + raw);
            String mode = cfg.processingMode != null ? cfg.processingMode : CatConfig.MODE_PUNCTUATION;
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
        String pkg = e.getPackageName() != null ? e.getPackageName().toString() : "";
        int type = e.getEventType();
        // 关键修复：窗口切换事件一律先刷新配置，再判断是否目标应用。
        // 旧逻辑是"先判断是否目标应用，再刷新配置"，如果用户刚在设置里开启了
        // 全局模式/新增了某个App，但缓存的还是旧配置，会被旧配置判定为"不是目标应用"
        // 而直接return，永远走不到刷新配置那一步，导致设置要等系统重启服务才生效。
        if (type == 32) {
            this.cachedConfig = CatConfig.load(this);
        }
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            this.cachedConfig = cfg;
        }
        if (type == 32) {
            appendLog("[窗口切换] pkg=" + pkg + " 全局模式=" + cfg.globalMode + " 微信开关=" + cfg.enableWeChat
                    + " isTargetPackage=" + cfg.isTargetPackage(pkg));
        }
        if (!cfg.isTargetPackage(pkg)) {
            return;
        }
        // 发送冷静期：刚触发过发送动作，短时间内所有事件一律忽略，
        // 避免跟宿主App自己的"发送并清空"流程抢节奏、造成一句话拆成多条发出去。
        if (type != 32 && System.currentTimeMillis() - this.lastSendTime < SEND_COOLDOWN_MS) {
            return;
        }
        // 切换到了不同应用（或前一次未触发窗口变化事件），重置增量追踪状态，避免跨应用串词
        if (!pkg.equals(this.trackedPkg)) {
            this.trackedPkg = pkg;
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            this.lastWriteTime = 0L;
        }
        if (type == 32) {
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            this.lastWriteTime = 0L;
            return;
        }
        if (type == 1) {
            AccessibilityNodeInfo src = e.getSource();
            if (src != null) {
                String id = src.getViewIdResourceName();
                if (ID_SEND.equals(id) || isSendLikeNode(src)) {
                    appendLog("点击发送，兜底处理");
                    doProcess(true);
                }
                src.recycle();
                return;
            }
            return;
        }
        if (type == 16 || type == 2048) {
            // 关键修复：不再"只要屏幕内容变化就重新扫一遍找输入框"，
            // 而是先看这次变化事件本身是从哪个控件发出的（e.getSource()）。
            // 只有当变化确实发生在一个"当前已聚焦的可编辑控件"上时才继续处理，
            // 否则直接忽略——避免把别的UI刷新（比如收到新消息、页面滚动）
            // 误判成用户在打字，进而把输入框里的占位提示文字当成真实内容改写/发送。
            AccessibilityNodeInfo src = e.getSource();
            if (src == null) {
                return;
            }
            boolean srcOk = src.isEditable() && src.isFocused() && !src.isShowingHintText();
            if (!srcOk && type == 2048) {
                // type=2048(界面内容变化)的事件源经常是一个容器节点，不是输入框本身，
                // 这种情况下退回到"在当前窗口里找已聚焦且非占位文字的可编辑控件"。
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
            if (!srcOk) {
                return;
            }

            String mode = cfg.processingMode != null ? cfg.processingMode : CatConfig.MODE_PUNCTUATION;
            if (CatConfig.MODE_REALTIME.equals(mode)) {
                doProcess(false);
                return;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            AccessibilityNodeInfo inp = findNodeById(root, ID_INPUT);
            if (inp == null) {
                inp = findEditable(root);
            }
            root.recycle();
            if (inp == null) {
                return;
            }
            CharSequence cs = inp.getText();
            inp.recycle();
            if (cs == null || cs.length() == 0) {
                return;
            }
            String raw = cs.toString().trim();
            if (!raw.isEmpty() && isPunctuationEnding(raw)) {
                appendLog("标点触发: " + raw);
                doProcess(false);
            }
        }
    }

    /**
     * 通用"发送"按钮识别：微信/抖音/快手的资源ID是混淆且随版本变化的，
     * 无法像QQ一样硬编码，因此改为匹配文案/描述中含"发送"或"Send"的可点击控件。
     */
    private boolean isSendLikeNode(AccessibilityNodeInfo n) {
        if (n == null || !n.isClickable()) {
            return false;
        }
        CharSequence text = n.getText();
        CharSequence desc = n.getContentDescription();
        String t = text != null ? text.toString().trim() : "";
        String d = desc != null ? desc.toString().trim() : "";
        return t.contains("发送") || d.contains("发送") || t.equalsIgnoreCase("send") || d.equalsIgnoreCase("send");
    }

    private boolean isPunctuationEnding(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        char last = s.charAt(s.length() - 1);
        return last == 12290 || last == 65281 || last == '!' || last == 65311 || last == '?' || last == ' ';
    }

    private void doProcess(boolean isSendClick) {
        if (this.processing) {
            return;
        }
        this.processing = true;
        if (isSendClick) {
            // 无论这次处理最终有没有真的写入新内容，只要是"点击发送"这条路径
            // 触发进来的，就立刻记下时间点、启动冷静期——因为宿主App的原生发送
            // 流程已经开始了，接下来这1.5秒无论收到多少事件都不再插手。
            this.lastSendTime = System.currentTimeMillis();
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            appendLog("[诊断] pkg=" + this.trackedPkg + " getRootInActiveWindow()返回null");
            this.processing = false;
            return;
        }
        AccessibilityNodeInfo inp = findNodeById(root, ID_INPUT);
        if (inp == null) {
            inp = findEditable(root);
        }
        if (inp == null) {
            appendLog("[诊断] pkg=" + this.trackedPkg + " 未找到任何可编辑输入框节点");
            root.recycle();
            this.processing = false;
            return;
        }
        if (inp.isShowingHintText()) {
            appendLog("[诊断] pkg=" + this.trackedPkg + " 当前是占位提示文字，非真实输入，跳过");
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        CharSequence cs = inp.getText();
        if (cs == null || cs.length() == 0) {
            appendLog("[诊断] pkg=" + this.trackedPkg + " 找到输入框但当前文本为空(editable=" + inp.isEditable() + " focused=" + inp.isFocused() + " class=" + inp.getClassName() + ")");
            inp.recycle();
            root.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            return;
        }
        String raw = cs.toString().trim();
        if (raw.isEmpty()) {
            inp.recycle();
            root.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            return;
        }
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            this.cachedConfig = cfg;
        }
        long now = System.currentTimeMillis();
        long j = this.lastWriteTime;
        if (j > 0 && now - j < 600 && raw.equals(this.lastSet)) {
            appendLog("写入回显跳过");
            this.lastWriteTime = 0L;
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        boolean isRealtime = CatConfig.MODE_REALTIME.equals(cfg.processingMode);
        if (!isRealtime && this.lastSet.isEmpty()) {
            this.userOriginal = stripAll(raw, cfg);
            appendLog("标点首次剥离: " + this.userOriginal);
        } else if (this.lastSet.isEmpty() || !raw.startsWith(this.lastSet)) {
            if (this.lastSet.isEmpty()) {
                this.userOriginal = stripAll(raw, cfg);
                appendLog("首条剥离: " + this.userOriginal);
            } else {
                this.userOriginal = stripAll(raw, cfg);
                appendLog("不匹配剥离: " + this.userOriginal);
            }
        } else {
            String added = raw.substring(this.lastSet.length());
            this.userOriginal += added;
            appendLog("前缀增量: +" + added + "  userOriginal=" + this.userOriginal);
        }
        if (this.userOriginal.isEmpty()) {
            appendLog("原文为空，跳过");
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        CatConfig effectiveCfg = cfg;
        if (isRealtime && cfg.enableRandomEmoticon && !isSendClick) {
            effectiveCfg = cloneConfigWithoutEmoticon(cfg);
        }
        String target = TextProcessor.process(this.userOriginal, effectiveCfg);
        if (!target.equals(raw)) {
            appendLog("写入: raw=" + raw + "  userOriginal=" + this.userOriginal + "  target=" + target);
            boolean ok = setText(inp, target);
            appendLog("[诊断] pkg=" + this.trackedPkg + " 写入调用返回=" + ok + "（如果这里是true但App里没变化，说明是该App拦截了无障碍写入）");
            if (ok) {
                this.lastSet = target;
                this.lastWriteTime = System.currentTimeMillis();
            }
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        this.lastSet = target;
        inp.recycle();
        root.recycle();
        this.processing = false;
    }

    private CatConfig cloneConfigWithoutEmoticon(CatConfig src) {
        CatConfig c = new CatConfig();
        c.enableAppend = src.enableAppend;
        c.appendText = src.appendText;
        c.enableRandomEmoticon = false;
        c.processingMode = src.processingMode;
        c.customEmoticons = src.customEmoticons;
        c.rules = src.rules;
        return c;
    }

    private String stripAll(String text, CatConfig cfg) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        String[] emotes = cfg.getActiveEmoticons();
        if (emotes.length == 0) {
            emotes = CatConfig.BUILTIN_EMOTICONS;
        }
        // 关键修复：之前只剥离"随机颜文字"，漏了"断句追加"功能加的文字（比如"喵"）。
        // 断句追加是每句话后面都会加一次，读回显文字时如果不把它也当成需要
        // 剥离的内容，就会把这个"喵"字误当成用户新打的字，再叠加一层"喵"，
        // 无限循环雪崩式增长（z→z喵→z喵喵→z喵喵喵……）。
        String[] toStrip = emotes;
        if (cfg.enableAppend && cfg.appendText != null && !cfg.appendText.isEmpty()) {
            String[] combined = new String[emotes.length + 1];
            System.arraycopy(emotes, 0, combined, 0, emotes.length);
            combined[emotes.length] = cfg.appendText;
            toStrip = combined;
        }
        Arrays.sort(toStrip, new Comparator() {
            @Override
            public int compare(Object obj, Object obj2) {
                return QQAccessibilityService.lambda$stripAll$0((String) obj, (String) obj2);
            }
        });
        for (String em : toStrip) {
            if (em == null || em.isEmpty()) {
                continue;
            }
            int idx;
            while ((idx = result.indexOf(em)) >= 0) {
                int st;
                if (idx <= 0 || result.charAt(idx - 1) != ' ') {
                    st = idx;
                } else {
                    st = idx - 1;
                }
                result = result.substring(0, st) + result.substring(idx + em.length());
            }
        }
        return result.replaceAll("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*", " ").trim();
    }

    static  int lambda$stripAll$0(String a, String b) {
        return b.length() - a.length();
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo n, String id) {
        if (n == null || id == null) {
            return null;
        }
        if (id.equals(n.getViewIdResourceName())) {
            return AccessibilityNodeInfo.obtain(n);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findNodeById(c, id);
                c.recycle();
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * 查找输入框：优先匹配"当前已获得输入焦点"的可编辑控件，避免误抓抖音/快手
     * 评论区底部那种"发消息"假占位按钮（那种控件通常是可编辑但并未真正获得焦点，
     * 点击后才会弹出真正的输入框）。找不到已聚焦的才退回旧逻辑（任意可编辑控件兜底）。
     */
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo focused = findFocusedEditable(n);
        if (focused != null) {
            return focused;
        }
        return findAnyEditable(n);
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo n) {
        if (n == null) {
            return null;
        }
        if (n.isEditable() && n.isFocused()) {
            return AccessibilityNodeInfo.obtain(n);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findFocusedEditable(c);
                c.recycle();
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findAnyEditable(AccessibilityNodeInfo n) {
        if (n == null) {
            return null;
        }
        if (n.isEditable()) {
            return AccessibilityNodeInfo.obtain(n);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findAnyEditable(c);
                c.recycle();
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private boolean setText(AccessibilityNodeInfo n, String t) {
        if (n == null) {
            return false;
        }
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
        // 关键修复：原来只监听32(窗口切换)+16(文字变化)+1(点击)。
        // 微信的输入框可能不会上报"文字变化"这个精确通知，而是只上报更宽泛的
        // "界面内容变化"(2048)。原来漏了这个类型，导致微信打字全程没有任何
        // 事件传到服务里，看起来就像"完全没反应"。现在把2048也加进来。
        i.eventTypes = 32 | 16 | 1 | 2048;
        i.feedbackType = 16;
        i.flags = 81;
        i.notificationTimeout = 50L;
        // packageNames 留空(null)表示监听所有应用产生的事件，具体是否处理由
        // CatConfig.isTargetPackage() 结合用户在设置里勾选的 应用范围/全局模式/自定义包名 判断
        i.packageNames = null;
        setServiceInfo(i);
        this.pollHandler.removeCallbacks(this.pollRunnable);
        this.pollHandler.postDelayed(this.pollRunnable, POLL_INTERVAL_MS);
        this.cachedConfig = CatConfig.load(this);
    }
}