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
    // 仅QQ有稳定公开的资源ID，作为快速路径；其余App一律走通用查找(findEditable)兜底
    private static final String ID_INPUT = "com.tencent.mobileqq:id/input";
    private static final String ID_SEND = "com.tencent.mobileqq:id/send_btn";
    private static final String TAG = "QQCatSvc";
    // 内存日志缓冲区：不依赖系统logcat（部分定制系统会限制App读取自身logcat），
    // 直接把日志存在进程内存里，供设置界面的"查看运行日志"按钮读取，跨机型更可靠。
    private static final int LOG_CAPACITY = 300;
    private static final ArrayDeque<String> LOG_BUFFER = new ArrayDeque<>();
    // 调试日志总开关，跟随CatConfig.enableDebugLog同步，默认关闭以省电。
    // 用volatile是因为轮询线程(主线程Handler)和事件回调都可能读到它。
    private static volatile boolean debugLogEnabled = false;

    private static synchronized void appendLog(String msg) {
        if (!debugLogEnabled) {
            return;
        }
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

    private CatConfig loadConfigAndSyncLogFlag() {
        CatConfig c = CatConfig.load(this);
        debugLogEnabled = c.enableDebugLog;
        return c;
    }

    /**
     * 关键修复：把"当前打字追踪到哪儿了"这份记账数据从"整个服务共用一份"
     * 改成"每个包名各自一份"。
     *
     * 之前的设计是不管当前处理的是哪个App，都往同一组实例变量
     * （userOriginal/lastSet/lastProcessedOriginal等）里读写。这在全局模式下
     * 会出问题：某些厂商定制系统会有额外的系统界面组件（比如vivo的
     * com.vivo.systemuiplugin，可能是分屏/悬浮窗相关的界面）也会被判定为
     * "目标应用"并短暂穿插进来处理，跟真正的聊天App（比如微信）来回抢着
     * 写同一份共享状态，导致互相踩踏——表现出来就是颜文字莫名其妙地
     * 反复随机跳动、文字被错误拼接等各种诡异现象。
     *
     * 不同厂商的系统组件包名五花八门，没法一个个拉黑名单穷举，所以正确
     * 的做法是从根上让不同包名的状态互不干扰：每个包名对应一份独立的
     * PkgState，即使全局模式下多个包名交替出现，各自的记账数据也不会
     * 被别人污染。
     */
    private static class PkgState {
        String userOriginal = "";
        String lastProcessedOriginal = "";
        String lastSet = "";
        long lastWriteTime = 0L;
        String lastPolledRaw = "";
    }

    private final Map<String, PkgState> pkgStates = new HashMap<>();

    private PkgState stateFor(String pkg) {
        PkgState s = this.pkgStates.get(pkg);
        if (s == null) {
            s = new PkgState();
            this.pkgStates.put(pkg, s);
        }
        return s;
    }

    private CatConfig cachedConfig;
    private boolean processing = false;
    // 发送后的"冷静期"：发送动作触发后的这段时间里，即使收到再多事件通知
    // 也先不处理，把节奏让给宿主App自己走完"读取文字→发送→清空输入框"的流程，
    // 避免我们的重复写入跟它自己的发送逻辑撞在一起，把同一句话拆成好几条发出去。
    private static final long SEND_COOLDOWN_MS = 1500L;
    private long lastSendTime = 0;
    // 轮询兜底：微信这类App可能完全不上报"内容变化"这类无障碍事件通知
    // （推测是出于防自动化考虑，QQ/抖音/快手目前实测都会正常上报，微信不会）。
    // 既然被动等通知等不到，就换成主动定时去查——不管有没有收到事件，
    // 每400ms自己去读一次当前输入框的文字，这样即使宿主App不推送通知，
    // 只要它的界面结构本身还能被正常查询到，就有机会绕过去。
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

    /**
     * 判断这个节点所在的窗口是不是"应用窗口"（普通App正常界面）。
     * 输入法弹出的窗口、系统状态栏/分屏悬浮层这些，Android会把窗口类型标记成
     * 非TYPE_APPLICATION（比如TYPE_INPUT_METHOD、TYPE_SYSTEM）。
     *
     * 关键修复：之前遇到的干扰问题（vivo系统组件、讯飞输入法）本质上是同一类——
     * 某个"非聊天App本体"的窗口也被处理了，跟真正的聊天App抢着改同一段文字。
     * 逐个拉黑具体包名治标不治本（换个手机、换个输入法牌子又要重新踩坑），
     * 用窗口类型这个通用标记来判断，不管是哪个厂商的系统组件、哪个牌子的
     * 输入法，只要不是"应用窗口"就一律不处理，一次性堵死这整类问题。
     */
    private boolean isAppWindow(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        try {
            android.view.accessibility.AccessibilityWindowInfo w = node.getWindow();
            if (w == null) {
                // 拿不到窗口信息时保守放行，避免因个别机型/权限差异导致功能完全用不了
                return true;
            }
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
            if (now - this.lastSendTime < SEND_COOLDOWN_MS) {
                return;
            }
            CatConfig cfg = this.cachedConfig;
            if (cfg == null) {
                cfg = loadConfigAndSyncLogFlag();
                this.cachedConfig = cfg;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            if (!isAppWindow(root)) {
                root.recycle();
                return;
            }
            String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
            if (!cfg.isTargetPackage(pkg)) {
                root.recycle();
                return;
            }
            PkgState st = stateFor(pkg);
            AccessibilityNodeInfo focused = findFocusedEditable(root);
            if (focused == null) {
                // 诊断分支：区分"界面能读到、但找不到任何已聚焦的可编辑控件"这种情况。
                // 每个App每10秒最多记一次，避免400ms轮询把日志刷爆。
                if (now - this.lastPollDiagTime > 10000L) {
                    this.lastPollDiagTime = now;
                    AccessibilityNodeInfo anyEditable = findAnyEditable(root);
                    if (anyEditable == null) {
                        appendLog("[轮询诊断] pkg=" + pkg + " 界面窗口能读到，但树里连一个isEditable()的控件都没有");
                    } else {
                        appendLog("[轮询诊断] pkg=" + pkg + " 树里找到了可编辑控件(class=" + anyEditable.getClassName()
                                + ")，但它的isFocused()不是true，所以没被当成目标输入框");
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
            if (raw.equals(st.lastPolledRaw)) {
                return;
            }
            st.lastPolledRaw = raw;
            if (raw.isEmpty()) {
                return;
            }
            appendLog("[轮询] pkg=" + pkg + " 检测到输入框内容: " + raw);
            String mode = cfg.processingMode != null ? cfg.processingMode : CatConfig.MODE_PUNCTUATION;
            if (CatConfig.MODE_REALTIME.equals(mode)) {
                doProcess(pkg, false);
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
        // 关键修复：窗口切换事件一律先刷新配置，再判断是否目标应用。
        // 旧逻辑是"先判断是否目标应用，再刷新配置"，如果用户刚在设置里开启了
        // 全局模式/新增了某个App，但缓存的还是旧配置，会被旧配置判定为"不是目标应用"
        // 而直接return，永远走不到刷新配置那一步，导致设置要等系统重启服务才生效。
        if (type == 32) {
            this.cachedConfig = loadConfigAndSyncLogFlag();
        }
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = loadConfigAndSyncLogFlag();
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
        if (type == 32) {
            // 窗口切换：清空这个包名自己的记账状态即可，不影响其他包名。
            this.pkgStates.remove(pkg);
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
            // 关键修复：不再"只要屏幕内容变化就重新扫一遍找输入框"，
            // 而是先看这次变化事件本身是从哪个控件发出的（e.getSource()）。
            // 只有当变化确实发生在一个"当前已聚焦的可编辑控件"上时才继续处理，
            // 否则直接忽略——避免把别的UI刷新（比如收到新消息、页面滚动）
            // 误判成用户在打字，进而把输入框里的占位提示文字当成真实内容改写/发送。
            AccessibilityNodeInfo src = e.getSource();
            if (src == null) {
                return;
            }
            boolean srcOk = src.isEditable() && src.isFocused() && !src.isShowingHintText() && isAppWindow(src);
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
                doProcess(pkg, false);
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
                doProcess(pkg, false);
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

    private void doProcess(String pkg, boolean isSendClick) {
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
        if (inp == null) {
            inp = findEditable(root);
        }
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
            // 这条诊断信息意义不大但触发很频繁（发送后输入框清空、每次点击都可能命中），
            // 节流成每个App每5秒最多记一次，避免把有用的日志挤出缓冲区。
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
        // 关键修复：如果"用户真实原始输入"跟上一次处理时完全一样，就不该重新处理，
        // 否则每次事件/轮询触发（哪怕用户根本没有继续打字，只是我们自己的重复检查）
        // 都会重新调一次改写逻辑，颜文字这部分每次都会重新随机选一个，导致明明字
        // 没变，颜文字却一直在自己跳动闪烁。点击发送这次(isSendClick=true)必须放行，
        // 确保最终发送前一定会走一次完整处理。
        if (!isSendClick && st.userOriginal.equals(st.lastProcessedOriginal)) {
            inp.recycle();
            root.recycle();
            this.processing = false;
            return;
        }
        CatConfig effectiveCfg = cfg;
        // 微信的发送按钮点击事件我们从来没能可靠捕捉到（大概率跟输入框一样受同样的
        // 内容混淆/事件屏蔽影响），导致"打字时先不加颜文字、点发送那一刻才补上"这套
        // 逻辑在微信这边永远等不到触发时机，颜文字实质上永远加不上。所以微信这边
        // 干脆放弃这个"防闪烁"优化，每次处理都直接带上颜文字，接受打字过程中
        // 颜文字会随机变换这个小瑕疵，换来颜文字确实能用。
        boolean isWeChat = CatConfig.PKG_WECHAT.equals(pkg);
        if (isRealtime && cfg.enableRandomEmoticon && !isSendClick && !isWeChat) {
            effectiveCfg = cloneConfigWithoutEmoticon(cfg);
        }
        String target = TextProcessor.process(st.userOriginal, effectiveCfg);
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
        Arrays.sort(toStrip, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
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
        this.cachedConfig = loadConfigAndSyncLogFlag();
        this.pkgStates.clear();
    }
}
