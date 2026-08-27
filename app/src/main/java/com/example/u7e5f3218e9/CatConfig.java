package com.example.u7e5f3218e9;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class CatConfig {
    public static final String[] BUILTIN_EMOTICONS = {"^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ", "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=", "₍^ >ヮ<^₎", "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ", "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ", "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑", "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎", "ฅ^._.^ฅ", "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ", "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^", "୧₍˄·͈༝·͈˄₎୨", "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒", "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ", "(=^･ᴥ･^=)", "(^ω^ฅ)", "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)", "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)", "(=ↀωↀ=)", "(=^-ω-^=)", "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿", "(^•ᴥ•^)", "( Φ ω Φ )", "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ", "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡"};

    public static final String KEY_RULES = "rules";
    public static final String KEY_ENABLE_APPEND = "enable_append";
    public static final String KEY_APPEND_TEXT = "append_text";
    public static final String KEY_ENABLE_EMOTICON = "enable_emoticon";
    public static final String KEY_CUSTOM_EMOTICONS = "custom_emoticons";
    public static final String KEY_PROCESSING_MODE = "processing_mode";
    public static final String MODE_PUNCTUATION = "punctuation";
    public static final String MODE_REALTIME = "realtime";
    private static final String PREFS_NAME = "cat_config";

    // ---- 应用范围相关 ----
    public static final String KEY_APP_QQ = "app_qq";
    public static final String KEY_APP_WECHAT = "app_wechat";
    public static final String KEY_APP_DOUYIN = "app_douyin";
    public static final String KEY_APP_KUAISHOU = "app_kuaishou";
    public static final String KEY_GLOBAL_MODE = "global_mode";
    public static final String KEY_ENABLE_DEBUG_LOG = "enable_debug_log";
    public static final String KEY_ENABLE_COMBO = "enable_combo_append_emoticon";
    public static final String KEY_CUSTOM_PACKAGES = "custom_packages";

    public static final String PKG_QQ = "com.tencent.mobileqq";
    public static final String PKG_QQ_I = "com.tencent.mobileqqi";
    public static final String PKG_WECHAT = "com.tencent.mm";
    public static final String PKG_DOUYIN = "com.ss.android.ugc.aweme";
    public static final String PKG_DOUYIN_LITE = "com.ss.android.ugc.aweme.lite";
    public static final String PKG_KUAISHOU = "com.smile.gifmaker";
    public static final String PKG_KUAISHOU_EXPRESS = "com.kuaishou.nebula";

    public static class Rule {
        public final String from;
        public final String to;

        public Rule(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return from + "=" + to;
        }
    }

    public boolean enableAppend = true;
    public String appendText = "喵";
    public boolean enableRandomEmoticon = true;
    public String processingMode = MODE_PUNCTUATION;
    public String[] customEmoticons = new String[0];
    public List<Rule> rules = new ArrayList<>();

    // 应用范围：默认只对QQ生效，与原版行为保持一致
    public boolean enableQQ = true;
    public boolean enableWeChat = false;
    public boolean enableDouyin = false;
    public boolean enableKuaishou = false;
    public boolean globalMode = false;
    // 默认关闭：调试日志主要用于排查兼容性问题，正常使用时不需要一直记，
    // 关闭后能省一点点性能开销，也避免日志缓冲区被高频诊断信息挤满。
    public boolean enableDebugLog = false;
    public boolean enableComboAppendEmoticon = false;
    public String[] customPackages = new String[0];

    /**
     * 判断某个包名当前是否处于处理范围内。
     * 全局模式开启时，忽略其余单项开关，对所有应用生效。
     */
    // 这几个包名永远不处理，不管全局模式开不开：都是系统界面/自身App，
    // 不是真正的聊天输入场景。之前发现全局模式下 com.android.systemui
    // （比如输入法上方候选词栏、系统弹窗这类临时界面）会被误当成目标应用，
    // 跟真正的聊天App来回抢着处理同一段文字，导致状态错乱、颜文字疯狂跳动。
    private static final String PKG_SYSTEMUI = "com.android.systemui";
    private static final String PKG_SELF = "com.example.u7e5f3218e9";

    public boolean isTargetPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        if (PKG_SYSTEMUI.equals(pkg) || PKG_SELF.equals(pkg)) {
            return false;
        }
        if (this.globalMode) {
            return true;
        }
        if (this.enableQQ && (PKG_QQ.equals(pkg) || PKG_QQ_I.equals(pkg))) {
            return true;
        }
        if (this.enableWeChat && PKG_WECHAT.equals(pkg)) {
            return true;
        }
        if (this.enableDouyin && (PKG_DOUYIN.equals(pkg) || PKG_DOUYIN_LITE.equals(pkg))) {
            return true;
        }
        if (this.enableKuaishou && (PKG_KUAISHOU.equals(pkg) || PKG_KUAISHOU_EXPRESS.equals(pkg))) {
            return true;
        }
        if (this.customPackages != null) {
            for (String p : this.customPackages) {
                if (p != null && p.equals(pkg)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Rule parseRule(String line) {
        if (line == null) {
            return null;
        }
        String s = line.trim();
        if (s.isEmpty()) {
            return null;
        }
        String separators = "=＝→";
        int idx = -1;
        for (int i = 0; i < separators.length(); i++) {
            int p = s.indexOf(separators.charAt(i));
            if (p >= 0 && (idx < 0 || p < idx)) {
                idx = p;
            }
        }
        if (idx <= 0) {
            return null;
        }
        String from = s.substring(0, idx).trim();
        String to = s.substring(idx + 1).trim();
        if (from.isEmpty()) {
            return null;
        }
        return new Rule(from, to);
    }

    public static String rulesToString(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        if (rules != null) {
            for (Rule r : rules) {
                if (r == null || r.from.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(r.from).append('=').append(r.to);
            }
        }
        return sb.toString();
    }

    public static CatConfig load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, 0);
        CatConfig cfg = new CatConfig();
        cfg.enableAppend = sp.getBoolean(KEY_ENABLE_APPEND, true);
        cfg.appendText = sp.getString(KEY_APPEND_TEXT, "喵");
        cfg.enableRandomEmoticon = sp.getBoolean(KEY_ENABLE_EMOTICON, true);
        cfg.processingMode = sp.getString(KEY_PROCESSING_MODE, MODE_PUNCTUATION);

        cfg.enableQQ = sp.getBoolean(KEY_APP_QQ, true);
        cfg.enableWeChat = sp.getBoolean(KEY_APP_WECHAT, false);
        cfg.enableDouyin = sp.getBoolean(KEY_APP_DOUYIN, false);
        cfg.enableKuaishou = sp.getBoolean(KEY_APP_KUAISHOU, false);
        cfg.globalMode = sp.getBoolean(KEY_GLOBAL_MODE, false);
        cfg.enableDebugLog = sp.getBoolean(KEY_ENABLE_DEBUG_LOG, false);
        cfg.enableComboAppendEmoticon = sp.getBoolean(KEY_ENABLE_COMBO, false);
        String customPkgStr = sp.getString(KEY_CUSTOM_PACKAGES, "");
        if (customPkgStr != null && !customPkgStr.trim().isEmpty()) {
            List<String> pkgList = new ArrayList<>();
            for (String s : customPkgStr.split("\n")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    pkgList.add(t);
                }
            }
            cfg.customPackages = pkgList.toArray(new String[0]);
        } else {
            cfg.customPackages = new String[0];
        }

        String rulesStr = sp.getString(KEY_RULES, "");
        if (rulesStr != null && !rulesStr.trim().isEmpty()) {
            List<Rule> list = new ArrayList<>();
            for (String line : rulesStr.split("\n")) {
                Rule r = parseRule(line);
                if (r != null) {
                    list.add(r);
                }
            }
            cfg.rules = list;
        }

        String custom = sp.getString(KEY_CUSTOM_EMOTICONS, "");
        if (custom != null && !custom.trim().isEmpty()) {
            List<String> list = new ArrayList<>();
            for (String s : custom.split("\n")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
            cfg.customEmoticons = list.toArray(new String[0]);
        } else {
            cfg.customEmoticons = new String[0];
        }
        return cfg;
    }

    public void save(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(KEY_ENABLE_APPEND, this.enableAppend);
        ed.putString(KEY_APPEND_TEXT, this.appendText == null ? "" : this.appendText);
        ed.putBoolean(KEY_ENABLE_EMOTICON, this.enableRandomEmoticon);
        ed.putString(KEY_PROCESSING_MODE, this.processingMode == null ? MODE_PUNCTUATION : this.processingMode);
        ed.putString(KEY_RULES, rulesToString(this.rules));
        ed.putString(KEY_CUSTOM_EMOTICONS, join(this.customEmoticons, "\n"));
        ed.putBoolean(KEY_APP_QQ, this.enableQQ);
        ed.putBoolean(KEY_APP_WECHAT, this.enableWeChat);
        ed.putBoolean(KEY_APP_DOUYIN, this.enableDouyin);
        ed.putBoolean(KEY_APP_KUAISHOU, this.enableKuaishou);
        ed.putBoolean(KEY_GLOBAL_MODE, this.globalMode);
        ed.putBoolean(KEY_ENABLE_DEBUG_LOG, this.enableDebugLog);
        ed.putBoolean(KEY_ENABLE_COMBO, this.enableComboAppendEmoticon);
        ed.putString(KEY_CUSTOM_PACKAGES, join(this.customPackages, "\n"));
        ed.apply();
    }

    public String[] getActiveEmoticons() {
        if (this.customEmoticons != null && this.customEmoticons.length > 0) {
            return this.customEmoticons;
        }
        return BUILTIN_EMOTICONS;
    }

    private static String join(String[] arr, String delim) {
        StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                String s = arr[i];
                if (s == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(delim);
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }
}