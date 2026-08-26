package com.example.u7e5f3218e9;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/** Isolated Shizuku bridge. Never executes privileged work in AccessibilityService. */
public final class ShizukuBridge {
    public static final int REQUEST_PERMISSION_CODE = 1001;
    private static final int TRANSACTION_DUMP_UI = 1;
    private static final long BIND_TIMEOUT_MS = 8000L;

    private static final Shizuku.UserServiceArgs USER_SERVICE_ARGS =
            new Shizuku.UserServiceArgs(
                    new ComponentName("com.example.u7e5f3218e9", ShizukuUiDumpUserService.class.getName()))
                    .daemon(false)
                    .tag("wechat-ui-dump")
                    .processNameSuffix("shizuku_ui")
                    .version(1);

    private ShizukuBridge() {
    }

    public interface DumpCallback {
        void onResult(String result);
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
     * Bind the isolated UserService and ask it to perform one uiautomator dump.
     * The callback is invoked off the main thread. A timeout is reported so a
     * failed bind cannot leave the UI in "检测中" forever.
     */
    public static void dumpUi(final DumpCallback callback) {
        if (callback == null) return;
        if (!hasPermission()) {
            callback.onResult("SHIZUKU_PERMISSION_NOT_GRANTED");
            return;
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                if (finished.compareAndSet(false, true)) {
                    callback.onResult("USER_SERVICE_BIND_TIMEOUT after " + BIND_TIMEOUT_MS + "ms");
                }
            }
        };

        final ServiceConnection[] holder = new ServiceConnection[1];
        holder[0] = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, final IBinder service) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String result;
                        try {
                            if (service == null || !service.pingBinder()) {
                                result = "USER_SERVICE_BINDER_INVALID";
                            } else {
                                Parcel data = Parcel.obtain();
                                Parcel reply = Parcel.obtain();
                                try {
                                    boolean handled = service.transact(TRANSACTION_DUMP_UI, data, reply, 0);
                                    if (!handled) {
                                        result = "USER_SERVICE_TRANSACTION_REJECTED";
                                    } else {
                                        reply.readException();
                                        result = reply.readString();
                                    }
                                } finally {
                                    reply.recycle();
                                    data.recycle();
                                }
                            }
                        } catch (Throwable e) {
                            result = "USER_SERVICE_ERROR: " + e;
                        }

                        final String finalResult = result == null ? "<null>" : result;
                        if (finished.compareAndSet(false, true)) {
                            mainHandler.removeCallbacks(timeout);
                            callback.onResult(finalResult);
                        }
                        try {
                            Shizuku.unbindUserService(USER_SERVICE_ARGS, holder[0], true);
                        } catch (Throwable ignored) {
                        }
                    }
                }, "qwq-shizuku-dump").start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (finished.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeout);
                    callback.onResult("USER_SERVICE_DISCONNECTED");
                }
            }
        };

        try {
            Shizuku.bindUserService(USER_SERVICE_ARGS, holder[0]);
            mainHandler.postDelayed(timeout, BIND_TIMEOUT_MS);
        } catch (Throwable e) {
            if (finished.compareAndSet(false, true)) {
                callback.onResult("USER_SERVICE_BIND_ERROR: " + e);
            }
        }
    }
}
