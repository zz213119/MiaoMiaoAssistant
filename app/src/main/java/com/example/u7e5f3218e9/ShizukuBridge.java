package com.example.u7e5f3218e9;

import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Experimental Shizuku bridge. This is intentionally isolated from the existing
 * Accessibility implementation so QQ/Douyin/Kuaishou behavior is unchanged.
 */
public final class ShizukuBridge {
    public static final int REQUEST_PERMISSION_CODE = 1001;

    private ShizukuBridge() {
    }

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            if (isAvailable() && !hasPermission()) {
                Shizuku.requestPermission(REQUEST_PERMISSION_CODE);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Run a shell command through the deprecated Shizuku.newProcess API.
     * Shizuku 13.1.x keeps the method but makes it private while transitioning
     * callers to UserService. Reflection is used here only for this experiment.
     */
    public static String runShell(String... command) {
        if (!hasPermission()) {
            return "SHIZUKU_PERMISSION_NOT_GRANTED";
        }
        ShizukuRemoteProcess process = null;
        try {
            Method method = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            process = (ShizukuRemoteProcess) method.invoke(null, command, null, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(process.getInputStream(), out);
            copy(process.getErrorStream(), out);
            process.waitFor();
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Throwable e) {
            return "SHIZUKU_ERROR: " + e;
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) throws Exception {
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
    }
}
