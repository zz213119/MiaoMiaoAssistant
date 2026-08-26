package com.example.u7e5f3218e9;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private CheckBox cbAppend;
    private CheckBox cbEmoticon;
    private CatConfig config;
    private EditText etAppendText;
    private EditText etCustomEmoticons;
    private EditText etRules;
    private CheckBox rbPunctuation;
    private CheckBox rbRealtime;
    private TextView statusText;
    private Button toggleButton;
    private TextView shizukuStatusText;
    private Button shizukuRequestButton;
    private Button shizukuOpenButton;

    // 应用范围
    private CheckBox cbAppQQ;
    private CheckBox cbAppWeChat;
    private CheckBox cbAppDouyin;
    private CheckBox cbAppKuaishou;
    private CheckBox cbGlobalMode;
    private EditText etCustomPackages;
    private LinearLayout appCheckRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            this.config = CatConfig.load(this);
        } catch (Exception e) {
            this.config = new CatConfig();
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(40, 40, 40, 80);
        root.setBackgroundColor(Color.parseColor("#FFF8E1"));
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("文本改写助手");
        title.setTextSize(24.0f);
        title.setTextColor(Color.rgb(230, 81, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(17);
        title.setPadding(0, 40, 0, 8);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("控制面板 · 支持QQ/微信/抖音/快手/全局/自定义");
        subtitle.setTextSize(14.0f);
        subtitle.setTextColor(Color.rgb(141, 110, 99));
        subtitle.setGravity(17);
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        this.statusText = new TextView(this);
        this.statusText.setTextSize(16.0f);
        this.statusText.setGravity(17);
        this.statusText.setPadding(24, 18, 24, 18);
        this.statusText.setBackgroundColor(-1);
        this.statusText.setTextColor(Color.rgb(51, 51, 51));
        root.addView(this.statusText);
        this.toggleButton = new Button(this);
        this.toggleButton.setTextSize(16.0f);
        this.toggleButton.setTextColor(-1);
        this.toggleButton.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, 16, 0, 0);
        this.toggleButton.setLayoutParams(btnLp);
        this.toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.openAccessibilitySettings();
            }
        });
        root.addView(this.toggleButton);

        Button logButton = new Button(this);
        logButton.setText("查看运行日志（调试用）");
        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.showDebugLog();
            }
        });
        root.addView(logButton);

        // Shizuku 实验区：只负责显示状态和申请授权，不影响原有无障碍功能。
        root.addView(divider());
        TextView shizukuTitle = new TextView(this);
        shizukuTitle.setText("Shizuku（微信实验功能）");
        shizukuTitle.setTextSize(18.0f);
        shizukuTitle.setTextColor(Color.rgb(93, 64, 55));
        shizukuTitle.setTypeface(Typeface.DEFAULT_BOLD);
        shizukuTitle.setPadding(0, 16, 0, 8);
        root.addView(shizukuTitle);

        TextView shizukuHint = new TextView(this);
        shizukuHint.setText("用于测试通过独立 Shizuku UserService + UiAutomator 读取微信界面。不会在无障碍主线程执行特权命令。");
        shizukuHint.setTextSize(12.0f);
        shizukuHint.setTextColor(Color.rgb(141, 110, 99));
        shizukuHint.setPadding(0, 0, 0, 8);
        root.addView(shizukuHint);

        this.shizukuStatusText = new TextView(this);
        this.shizukuStatusText.setTextSize(15.0f);
        this.shizukuStatusText.setPadding(20, 14, 20, 14);
        this.shizukuStatusText.setBackgroundColor(-1);
        this.shizukuStatusText.setTextColor(Color.rgb(51, 51, 51));
        root.addView(this.shizukuStatusText);

        this.shizukuRequestButton = new Button(this);
        this.shizukuRequestButton.setText("申请 Shizuku 权限");
        this.shizukuRequestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.requestShizukuPermission();
            }
        });
        root.addView(this.shizukuRequestButton);

        this.shizukuOpenButton = new Button(this);
        this.shizukuOpenButton.setText("打开 Shizuku");
        this.shizukuOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.openShizuku();
            }
        });
        root.addView(this.shizukuOpenButton);
        root.addView(divider());

        TextView scopeTitle = new TextView(this);
        scopeTitle.setText("应用范围");
        scopeTitle.setTextSize(18.0f);
        scopeTitle.setTextColor(Color.rgb(93, 64, 55));
        scopeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        scopeTitle.setPadding(0, 16, 0, 8);
        root.addView(scopeTitle);

        this.cbGlobalMode = addCheckbox(root, "全局模式", "对所有应用生效（开启后下方单项开关与自定义包名均被忽略，请谨慎开启）", this.config.globalMode);
        this.cbGlobalMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.this.setAppScopeRowEnabled(!isChecked);
            }
        });

        this.appCheckRow = new LinearLayout(this);
        this.appCheckRow.setOrientation(1);
        this.appCheckRow.setPadding(0, 4, 0, 4);
        this.cbAppQQ = addCheckbox(this.appCheckRow, "QQ", "com.tencent.mobileqq", this.config.enableQQ);
        this.cbAppWeChat = addCheckbox(this.appCheckRow, "微信", "com.tencent.mm", this.config.enableWeChat);
        this.cbAppDouyin = addCheckbox(this.appCheckRow, "抖音", "com.ss.android.ugc.aweme", this.config.enableDouyin);
        this.cbAppKuaishou = addCheckbox(this.appCheckRow, "快手", "com.smile.gifmaker", this.config.enableKuaishou);
        root.addView(this.appCheckRow);

        TextView customPkgTitle = new TextView(this);
        customPkgTitle.setText("自定义应用（自定义模式）");
        customPkgTitle.setTextSize(14.0f);
        customPkgTitle.setTextColor(Color.rgb(93, 64, 55));
        customPkgTitle.setTypeface(Typeface.DEFAULT_BOLD);
        customPkgTitle.setPadding(0, 12, 0, 4);
        root.addView(customPkgTitle);
        TextView customPkgHint = new TextView(this);
        customPkgHint.setText("每行一个包名，用于适配上面未列出的App。可在设置->应用信息中查看目标App的包名");
        customPkgHint.setTextSize(11.0f);
        customPkgHint.setTextColor(Color.rgb(161, 136, 127));
        customPkgHint.setPadding(0, 0, 0, 8);
        root.addView(customPkgHint);
        this.etCustomPackages = new EditText(this);
        this.etCustomPackages.setInputType(131073);
        this.etCustomPackages.setLines(3);
        this.etCustomPackages.setMinLines(3);
        this.etCustomPackages.setBackgroundColor(-1);
        this.etCustomPackages.setPadding(16, 12, 16, 12);
        this.etCustomPackages.setHint("例如: com.tencent.mobileqqhd");
        this.etCustomPackages.setText(joinLines(this.config.customPackages));
        root.addView(this.etCustomPackages);
        setAppScopeRowEnabled(!this.config.globalMode);
        root.addView(divider());

        TextView modeTitle = new TextView(this);
        modeTitle.setText("处理模式");
        modeTitle.setTextSize(18.0f);
        modeTitle.setTextColor(Color.rgb(93, 64, 55));
        modeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        modeTitle.setPadding(0, 16, 0, 12);
        root.addView(modeTitle);
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(0);
        modeRow.setPadding(0, 8, 0, 8);
        this.rbPunctuation = new CheckBox(this);
        this.rbPunctuation.setText("标点触发 (推荐)  ");
        this.rbPunctuation.setTextSize(16.0f);
        this.rbPunctuation.setTextColor(Color.rgb(51, 51, 51));
        this.rbPunctuation.setChecked(CatConfig.MODE_PUNCTUATION.equals(this.config.processingMode));
        this.rbPunctuation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.this.m0lambda$onCreate$0$comexampleu7e5f3218e9MainActivity(buttonView, isChecked);
            }
        });
        modeRow.addView(this.rbPunctuation);
        this.rbRealtime = new CheckBox(this);
        this.rbRealtime.setText("实时处理");
        this.rbRealtime.setTextSize(16.0f);
        this.rbRealtime.setTextColor(Color.rgb(51, 51, 51));
        this.rbRealtime.setChecked(CatConfig.MODE_REALTIME.equals(this.config.processingMode));
        this.rbRealtime.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.this.m1lambda$onCreate$1$comexampleu7e5f3218e9MainActivity(buttonView, isChecked);
            }
        });
        modeRow.addView(this.rbRealtime);
        root.addView(modeRow);
        TextView modeHint = new TextView(this);
        modeHint.setText("标点触发：打字时只在标点处立即处理\n实时处理：每输入一个字立即处理（体验可能较快）");
        modeHint.setTextSize(11.0f);
        modeHint.setTextColor(Color.rgb(161, 136, 127));
        modeHint.setPadding(0, 0, 0, 16);
        root.addView(modeHint);

        TextView funcTitle = new TextView(this);
        funcTitle.setText("功能开关");
        funcTitle.setTextSize(18.0f);
        funcTitle.setTextColor(Color.rgb(93, 64, 55));
        funcTitle.setTypeface(Typeface.DEFAULT_BOLD);
        funcTitle.setPadding(0, 16, 0, 8);
        root.addView(funcTitle);
        this.cbAppend = addCheckbox(root, "断句追加", "在句号、叹号等标点分句后追加文本", this.config.enableAppend);
        this.etAppendText = new EditText(this);
        this.etAppendText.setInputType(131073);
        this.etAppendText.setBackgroundColor(-1);
        this.etAppendText.setPadding(16, 12, 16, 12);
        this.etAppendText.setHint("追加内容（默认：喵）");
        this.etAppendText.setText(this.config.appendText != null ? this.config.appendText : "喵");
        LinearLayout.LayoutParams etLp1 = new LinearLayout.LayoutParams(-1, -2);
        etLp1.setMargins(0, 0, 0, 4);
        this.etAppendText.setLayoutParams(etLp1);
        root.addView(this.etAppendText);
        this.cbEmoticon = addCheckbox(root, "句末颜文字", "在消息末尾附加随机颜文字", this.config.enableRandomEmoticon);

        TextView ruleTitle = new TextView(this);
        ruleTitle.setText("文本替换规则");
        ruleTitle.setTextSize(18.0f);
        ruleTitle.setTextColor(Color.rgb(93, 64, 55));
        ruleTitle.setTypeface(Typeface.DEFAULT_BOLD);
        ruleTitle.setPadding(0, 16, 0, 8);
        root.addView(ruleTitle);
        TextView ruleHint = new TextView(this);
        ruleHint.setText("每行一条，按顺序应用。格式：原词=替换词（也支持 ＝ 全角等号 / →）\n例：我=本喵 / 你＝主人 / 也支持数字等任意文本");
        ruleHint.setTextSize(12.0f);
        ruleHint.setTextColor(Color.rgb(141, 110, 99));
        ruleHint.setPadding(0, 0, 0, 12);
        root.addView(ruleHint);
        this.etRules = new EditText(this);
        this.etRules.setInputType(131073);
        this.etRules.setLines(6);
        this.etRules.setMinLines(6);
        this.etRules.setBackgroundColor(-1);
        this.etRules.setPadding(16, 12, 16, 12);
        this.etRules.setText(CatConfig.rulesToString(this.config.rules));
        root.addView(this.etRules);

        TextView emojiTitle = new TextView(this);
        emojiTitle.setText("自定义颜文字");
        emojiTitle.setTextSize(18.0f);
        emojiTitle.setTextColor(Color.rgb(93, 64, 55));
        emojiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        emojiTitle.setPadding(0, 16, 0, 8);
        root.addView(emojiTitle);
        TextView emojiHint = new TextView(this);
        emojiHint.setText("每行一个颜文字，留空则使用内置库");
        emojiHint.setTextSize(12.0f);
        emojiHint.setTextColor(Color.rgb(141, 110, 99));
        emojiHint.setPadding(0, 0, 0, 12);
        root.addView(emojiHint);
        this.etCustomEmoticons = new EditText(this);
        this.etCustomEmoticons.setInputType(131073);
        this.etCustomEmoticons.setLines(4);
        this.etCustomEmoticons.setMinLines(4);
        this.etCustomEmoticons.setBackgroundColor(-1);
        this.etCustomEmoticons.setPadding(16, 12, 16, 12);
        this.etCustomEmoticons.setHint("例如: (=^w^=) 等");
        this.etCustomEmoticons.setText(joinLines(this.config.customEmoticons));
        root.addView(this.etCustomEmoticons);

        Button saveBtn = new Button(this);
        saveBtn.setText("保存设置");
        saveBtn.setTextSize(16.0f);
        saveBtn.setTextColor(-1);
        saveBtn.setBackgroundColor(Color.rgb(255, 111, 0));
        saveBtn.setPadding(40, 16, 40, 16);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, -2);
        saveLp.setMargins(0, 16, 0, 0);
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.saveConfig();
            }
        });
        root.addView(saveBtn);
        Button testBtn = new Button(this);
        testBtn.setText("测试当前配置");
        testBtn.setTextSize(14.0f);
        testBtn.setTextColor(Color.rgb(255, 111, 0));
        testBtn.setBackgroundColor(-1);
        testBtn.setPadding(40, 14, 40, 14);
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1, -2);
        testLp.setMargins(0, 12, 0, 0);
        testBtn.setLayoutParams(testLp);
        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.showTestDialog();
            }
        });
        root.addView(testBtn);
        TextView hint = new TextView(this);
        hint.setText("提示：修改设置后请点击保存，服务下次触发时自动加载");
        hint.setTextSize(11.0f);
        hint.setTextColor(Color.rgb(161, 136, 127));
        hint.setGravity(17);
        hint.setPadding(16, 36, 16, 8);
        root.addView(hint);

        scrollView.addView(root);
        setContentView(scrollView);
        updateShizukuStatus();
    }

    void m0lambda$onCreate$0$comexampleu7e5f3218e9MainActivity(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            this.rbRealtime.setChecked(false);
        }
    }

    void m1lambda$onCreate$1$comexampleu7e5f3218e9MainActivity(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            this.rbPunctuation.setChecked(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        updateShizukuStatus();
    }

    private void updateServiceStatus() {
        if (this.statusText == null || this.toggleButton == null) {
            return;
        }
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            this.statusText.setText("服务状态：已开启");
            this.statusText.setTextColor(Color.rgb(46, 125, 50));
            this.toggleButton.setText("服务已开启");
            this.toggleButton.setEnabled(false);
            this.toggleButton.setBackgroundColor(Color.rgb(165, 214, 167));
            return;
        }
        this.statusText.setText("服务状态：未开启");
        this.statusText.setTextColor(Color.rgb(198, 40, 40));
        this.toggleButton.setText("前往开启无障碍服务");
        this.toggleButton.setEnabled(true);
        this.toggleButton.setBackgroundColor(Color.rgb(255, 111, 0));
    }

    private void updateShizukuStatus() {
        if (this.shizukuStatusText == null) return;
        boolean available = ShizukuBridge.isAvailable();
        boolean permission = ShizukuBridge.hasPermission();
        if (!available) {
            this.shizukuStatusText.setText("Shizuku 状态：未连接\n请先启动系统中的 Shizuku 服务");
            this.shizukuStatusText.setTextColor(Color.rgb(198, 40, 40));
            this.shizukuRequestButton.setText("申请 Shizuku 权限");
            this.shizukuRequestButton.setEnabled(false);
            return;
        }
        if (!permission) {
            this.shizukuStatusText.setText("Shizuku 状态：已连接，但未授权本应用");
            this.shizukuStatusText.setTextColor(Color.rgb(245, 124, 0));
            this.shizukuRequestButton.setText("申请 Shizuku 权限");
            this.shizukuRequestButton.setEnabled(true);
            return;
        }
        this.shizukuStatusText.setText("Shizuku 状态：已连接并已授权 ✓\n可单独测试微信 UiAutomator UI 树");
        this.shizukuStatusText.setTextColor(Color.rgb(46, 125, 50));
        this.shizukuRequestButton.setText("检测微信 UI");
        this.shizukuRequestButton.setEnabled(true);
    }

    private void requestShizukuPermission() {
        if (!ShizukuBridge.isAvailable()) {
            Toast.makeText(this, "Shizuku 尚未启动", Toast.LENGTH_SHORT).show();
            updateShizukuStatus();
            return;
        }
        if (ShizukuBridge.hasPermission()) {
            runWeChatUiDumpTest();
            return;
        }
        ShizukuBridge.requestPermission();
        Toast.makeText(this, "已发起 Shizuku 权限申请，请在 Shizuku 中允许", Toast.LENGTH_LONG).show();
    }

    private void runWeChatUiDumpTest() {
        this.shizukuRequestButton.setEnabled(false);
        this.shizukuRequestButton.setText("检测中...");
        Toast.makeText(this, "请保持微信聊天界面在前台，再等待检测结果", Toast.LENGTH_SHORT).show();
        ShizukuBridge.dumpUi(new ShizukuBridge.DumpCallback() {
            @Override
            public void onResult(final String result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateShizukuStatus();
                        TextView tv = new TextView(MainActivity.this);
                        tv.setText(result == null ? "<null>" : result);
                        tv.setTextIsSelectable(true);
                        tv.setTextSize(11.0f);
                        tv.setPadding(24, 24, 24, 24);
                        ScrollView sv = new ScrollView(MainActivity.this);
                        sv.addView(tv);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Shizuku / UiAutomator 检测结果")
                                .setView(sv)
                                .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                        cm.setPrimaryClip(ClipData.newPlainText("wechat_ui_dump", result));
                                        Toast.makeText(MainActivity.this, "已复制检测结果", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("关闭", null)
                                .show();
                    }
                });
            }
        });
    }

    private void openShizuku() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, "未找到 Shizuku，请先安装 Shizuku", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法打开 Shizuku: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService("accessibility");
            if (am == null) {
                return false;
            }
            List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(-1);
            for (AccessibilityServiceInfo info : services) {
                if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void openAccessibilitySettings() {
        try {
            Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
            intent.setFlags(268435456);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开设置", 0).show();
        }
    }

    private CheckBox addCheckbox(LinearLayout linearLayout, String title, String desc, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setPadding(0, 8, 0, 8);
        row.setGravity(16);
        CheckBox cb = new CheckBox(this);
        cb.setChecked(checked);
        row.addView(cb, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(1);
        textCol.setPadding(12, 0, 0, 0);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16.0f);
        tvTitle.setTextColor(Color.rgb(51, 51, 51));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);
        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(12.0f);
        tvDesc.setTextColor(Color.rgb(136, 136, 136));
        textCol.addView(tvDesc);
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout.addView(row);
        return cb;
    }

    private void showDebugLog() {
        String log = QQAccessibilityService.getLogSnapshot();
        if (log == null || log.trim().isEmpty()) {
            log = "（暂无日志。请先确认无障碍服务已开启，并在目标App里打字触发一次，再回来点这个按钮看日志。\n"
                    + "注意：这份日志是App自己在内存里记的，重启App或服务后会清空，不依赖系统logcat，理论上不受机型限制）";
        }
        final String finalLog = log;

        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(finalLog);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 24, 24, 24);
        tv.setTextSize(11.0f);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("运行日志（最近300行，倒序为最新）")
                .setView(sv)
                .setPositiveButton("复制全部", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("qqmiao_log", finalLog));
                        Toast.makeText(MainActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void setAppScopeRowEnabled(boolean enabled) {
        if (this.appCheckRow != null) {
            this.appCheckRow.setAlpha(enabled ? 1.0f : 0.4f);
            for (int i = 0; i < this.appCheckRow.getChildCount(); i++) {
                this.appCheckRow.getChildAt(i).setEnabled(enabled);
            }
        }
        if (this.cbAppQQ != null) this.cbAppQQ.setEnabled(enabled);
        if (this.cbAppWeChat != null) this.cbAppWeChat.setEnabled(enabled);
        if (this.cbAppDouyin != null) this.cbAppDouyin.setEnabled(enabled);
        if (this.cbAppKuaishou != null) this.cbAppKuaishou.setEnabled(enabled);
        if (this.etCustomPackages != null) this.etCustomPackages.setEnabled(enabled);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(221, 221, 221));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 2);
        lp.setMargins(0, 24, 0, 8);
        v.setLayoutParams(lp);
        return v;
    }

    private String joinLines(String[] arr) {
        if (arr == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(t);
        }
        return sb.toString();
    }

    public void saveConfig() {
        try {
            this.config.enableAppend = this.cbAppend.isChecked();
            String append = this.etAppendText.getText().toString().trim();
            this.config.appendText = append.isEmpty() ? "喵" : append;
            this.config.enableRandomEmoticon = this.cbEmoticon.isChecked();
            this.config.processingMode = this.rbRealtime.isChecked() ? CatConfig.MODE_REALTIME : CatConfig.MODE_PUNCTUATION;

            ArrayList<CatConfig.Rule> rules = new ArrayList<>();
            String rulesText = this.etRules.getText() == null ? "" : this.etRules.getText().toString();
            for (String line : rulesText.split("\n")) {
                CatConfig.Rule r = CatConfig.parseRule(line);
                if (r != null) {
                    rules.add(r);
                }
            }
            this.config.rules = rules;

            ArrayList<String> list = new ArrayList<>();
            String customText = this.etCustomEmoticons.getText() == null ? "" : this.etCustomEmoticons.getText().toString().trim();
            if (!customText.isEmpty()) {
                for (String raw : customText.split("\n")) {
                    String t = raw.trim();
                    if (!t.isEmpty()) {
                        list.add(t);
                    }
                }
            }
            this.config.customEmoticons = list.toArray(new String[0]);

            this.config.globalMode = this.cbGlobalMode.isChecked();
            this.config.enableQQ = this.cbAppQQ.isChecked();
            this.config.enableWeChat = this.cbAppWeChat.isChecked();
            this.config.enableDouyin = this.cbAppDouyin.isChecked();
            this.config.enableKuaishou = this.cbAppKuaishou.isChecked();
            ArrayList<String> pkgList = new ArrayList<>();
            String pkgText = this.etCustomPackages.getText() == null ? "" : this.etCustomPackages.getText().toString().trim();
            if (!pkgText.isEmpty()) {
                for (String raw : pkgText.split("\n")) {
                    String t = raw.trim();
                    if (!t.isEmpty()) {
                        pkgList.add(t);
                    }
                }
            }
            this.config.customPackages = pkgList.toArray(new String[0]);

            this.config.save(this);
            Toast.makeText(this, "设置已保存", 0).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), 0).show();
        }
    }

    public void showTestDialog() {
        try {
            saveConfig();
            CatConfig testCfg = CatConfig.load(this);
            String sample = "今天我很好，你准备好了吗？我们去公园玩吧";
            String processed = TextProcessor.process(sample, testCfg);
            String msg = "断句追加：" + yn(testCfg.enableAppend) + "（" + (testCfg.appendText == null ? "" : testCfg.appendText) + "）"
                    + "\n句末颜文字：" + yn(testCfg.enableRandomEmoticon)
                    + "\n替换规则：" + testCfg.rules.size() + " 条"
                    + "\n自定义颜文字：" + (testCfg.customEmoticons.length > 0 ? testCfg.customEmoticons.length + "个" : "使用内置")
                    + "\n\n原始：\n" + sample
                    + "\n\n处理后：\n" + processed;
            new AlertDialog.Builder(this).setTitle("预览").setMessage(msg).setPositiveButton("好的", (DialogInterface.OnClickListener) null).show();
        } catch (Exception e) {
            Toast.makeText(this, "测试失败: " + e.getMessage(), 0).show();
        }
    }

    private String yn(boolean b) {
        return b ? "开" : "关";
    }
}
