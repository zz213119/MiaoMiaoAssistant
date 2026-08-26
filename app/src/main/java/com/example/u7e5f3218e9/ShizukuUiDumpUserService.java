package com.example.u7e5f3218e9;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Isolated Shizuku UserService used only for the WeChat UI dump experiment.
 * It does not run inside QQAccessibilityService.
 */
public class ShizukuUiDumpUserService extends Binder {
    private static final String TAG = "QwqShizukuUser";
    private static final int TRANSACTION_DUMP_UI = 1;
    private static final int TRANSACTION_DESTROY_AIDL = 16777114;
    private static final int TRANSACTION_DESTROY = 16777115;

    public ShizukuUiDumpUserService() {
        Log.i(TAG, "UserService created");
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        try {
            if (code == TRANSACTION_DUMP_UI) {
                String result = dumpUi();
                reply.writeNoException();
                reply.writeString(result);
                return true;
            }
            if (code == TRANSACTION_DESTROY_AIDL || code == TRANSACTION_DESTROY) {
                reply.writeNoException();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SystemClock.sleep(50L);
                        System.exit(0);
                    }
                }).start();
                return true;
            }
        } catch (Throwable e) {
            try {
                reply.writeNoException();
                reply.writeString("ERROR: " + Log.getStackTraceString(e));
            } catch (Throwable ignored) {
            }
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private String dumpUi() throws Exception {
        String path = "/data/local/tmp/qwq_wechat_ui.xml";
        File outFile = new File(path);
        if (outFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
        }

        Process process = new ProcessBuilder("/system/bin/uiautomator", "dump", path)
                .redirectErrorStream(true)
                .start();
        String processOutput = readStream(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            return "DUMP_FAILED exit=" + exit + " output=" + processOutput;
        }
        if (!outFile.exists()) {
            return "DUMP_FAILED no_xml output=" + processOutput;
        }

        String xml = readUtf8(outFile);
        if (xml == null) xml = "";
        String compact = compactForLog(xml);
        return "OK exit=" + exit + " xmlLength=" + xml.length() + "\n" + compact;
    }

    private String compactForLog(String xml) {
        if (xml.isEmpty()) return "<empty>";
        String lower = xml.toLowerCase();
        StringBuilder sb = new StringBuilder();
        String[] needles = new String[]{"edittext", "input", "textbox", "com.tencent.mm", "send", "发送"};
        for (String needle : needles) {
            int from = 0;
            int count = 0;
            while (count < 8) {
                int idx = lower.indexOf(needle.toLowerCase(), from);
                if (idx < 0) break;
                int start = Math.max(0, xml.lastIndexOf('<', idx));
                int end = xml.indexOf('>', idx);
                if (end < 0) break;
                sb.append(xml, start, Math.min(xml.length(), end + 1)).append('\n');
                from = end + 1;
                count++;
            }
        }
        if (sb.length() == 0) {
            int limit = Math.min(xml.length(), 6000);
            sb.append(xml, 0, limit);
            if (xml.length() > limit) sb.append("\n...[truncated]");
        }
        return sb.toString();
    }

    private String readUtf8(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
        } finally {
            r.close();
        }
        return sb.toString();
    }

    private String readStream(java.io.InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } finally {
            r.close();
        }
        return sb.toString().trim();
    }
}
