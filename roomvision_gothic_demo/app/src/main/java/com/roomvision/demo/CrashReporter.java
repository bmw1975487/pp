package com.roomvision.demo;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashReporter {
    private static final String TAG = "RoomVision";
    private static final String PREFS = "roomvision_diagnostics";
    private static final String KEY_PENDING = "pending_crash";
    private static final String KEY_AUTO_OPENED = "auto_opened_crash";
    private static final Object LOCK = new Object();
    private static Context appContext;
    private static File logDir;
    private static File sessionFile;
    private static Thread.UncaughtExceptionHandler previousHandler;

    private CrashReporter() { }

    public static void install(Application app) {
        synchronized (LOCK) {
            if (appContext != null) return;
            appContext = app.getApplicationContext();
            logDir = new File(appContext.getFilesDir(), "diagnostics");
            if (!logDir.exists()) logDir.mkdirs();
            sessionFile = new File(logDir, "current_session.log");
            try (FileOutputStream out = new FileOutputStream(sessionFile, false)) {
                out.write((header("SESSION_START") + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable t) {
                Log.e(TAG, "Cannot initialize diagnostic log", t);
            }
            previousHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                try {
                    writeFatalCrash(thread, throwable);
                } catch (Throwable loggingFailure) {
                    Log.e(TAG, "Crash logger failed", loggingFailure);
                } finally {
                    if (previousHandler != null) {
                        previousHandler.uncaughtException(thread, throwable);
                    } else {
                        Process.killProcess(Process.myPid());
                        System.exit(10);
                    }
                }
            });
            record("CRASH_REPORTER_INSTALLED");
        }
    }

    public static void record(String message) {
        if (appContext == null || sessionFile == null) {
            Log.i(TAG, message);
            return;
        }
        String line = timestamp() + " [" + Thread.currentThread().getName() + "] " + message + "\n";
        Log.i(TAG, message);
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(sessionFile, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) { }
        }
    }

    public static void error(String where, Throwable t) {
        record("ERROR " + where + " :: " + t.getClass().getName() + " :: " + safeMessage(t));
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(sessionFile, true)) {
                out.write(stackTrace(t).getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            } catch (Throwable ignored) { }
        }
    }

    private static void writeFatalCrash(Thread thread, Throwable throwable) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        record("FATAL_CRASH thread=" + thread.getName());
        error("UNCAUGHT_EXCEPTION", throwable);
        appendSystemState();
        File crash = new File(logDir, "crash_" + stamp + ".log");
        copyFile(sessionFile, crash);
        prefs().edit().putString(KEY_PENDING, crash.getAbsolutePath()).remove(KEY_AUTO_OPENED).commit();
    }

    public static void captureRecoverable(String where, Throwable throwable) {
        error("RECOVERABLE_" + where, throwable);
    }

    public static void createDiagnosticSnapshot(String reason) {
        try {
            record("DIAGNOSTIC_SNAPSHOT " + reason);
            appendSystemState();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            File snapshot = new File(logDir, "diagnostic_" + stamp + ".log");
            copyFile(sessionFile, snapshot);
            prefs().edit().putString(KEY_PENDING, snapshot.getAbsolutePath()).remove(KEY_AUTO_OPENED).commit();
        } catch (Throwable t) {
            Log.e(TAG, "Cannot create diagnostic snapshot", t);
        }
    }

    private static void appendSystemState() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        record("SYSTEM_STATE usedHeap=" + used + " freeHeap=" + rt.freeMemory() + " totalHeap=" + rt.totalMemory() + " maxHeap=" + rt.maxMemory());
    }

    private static String header(String reason) {
        String version = "unknown";
        try {
            PackageInfo pi = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
            version = pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Throwable ignored) { }
        Runtime rt = Runtime.getRuntime();
        return "===== ROOM VISION DIAGNOSTIC =====\n" +
                "reason=" + reason + "\n" +
                "time=" + timestamp() + "\n" +
                "app=" + version + "\n" +
                "package=" + appContext.getPackageName() + "\n" +
                "device=" + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "deviceCode=" + Build.DEVICE + " / " + Build.PRODUCT + "\n" +
                "android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT + "\n" +
                "abi=" + String.join(",", Build.SUPPORTED_ABIS) + "\n" +
                "heapMax=" + rt.maxMemory() + "\n" +
                "==================================";
    }

    public static boolean hasPendingCrash() {
        String path = prefs().getString(KEY_PENDING, null);
        return path != null && new File(path).isFile();
    }

    public static String pendingCrashName() {
        String path = prefs().getString(KEY_PENDING, null);
        return path == null ? null : new File(path).getName();
    }

    public static boolean shouldAutoOpenMaxOnce() {
        String pending = prefs().getString(KEY_PENDING, null);
        String opened = prefs().getString(KEY_AUTO_OPENED, null);
        return pending != null && !pending.equals(opened) && new File(pending).isFile();
    }

    public static void markAutoOpened() {
        String pending = prefs().getString(KEY_PENDING, null);
        if (pending != null) prefs().edit().putString(KEY_AUTO_OPENED, pending).apply();
    }

    public static boolean sharePendingToMax(Activity activity) {
        String path = prefs().getString(KEY_PENDING, null);
        if (path == null) return false;
        File file = new File(path);
        if (!file.isFile()) return false;
        String text = readFile(file, 450_000);
        if (text == null || text.isEmpty()) return false;
        String body = "RoomVision crash-log\n\n" + text;
        Intent maxIntent = new Intent(Intent.ACTION_SEND);
        maxIntent.setType("text/plain");
        maxIntent.putExtra(Intent.EXTRA_SUBJECT, "RoomVision crash log " + file.getName());
        maxIntent.putExtra(Intent.EXTRA_TEXT, body);
        maxIntent.setPackage("ru.oneme.app");
        try {
            activity.startActivity(maxIntent);
            record("CRASH_LOG_OPENED_IN_MAX " + file.getName());
            return true;
        } catch (ActivityNotFoundException notInstalled) {
            Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("text/plain");
            fallback.putExtra(Intent.EXTRA_SUBJECT, "RoomVision crash log " + file.getName());
            fallback.putExtra(Intent.EXTRA_TEXT, body);
            try {
                activity.startActivity(Intent.createChooser(fallback, "Отправить crash-log"));
                record("CRASH_LOG_OPENED_IN_CHOOSER " + file.getName());
                return true;
            } catch (Throwable t) {
                error("SHARE_LOG_FAILED", t);
                return false;
            }
        } catch (Throwable t) {
            error("SHARE_LOG_FAILED", t);
            return false;
        }
    }

    public static String currentSessionText() {
        return readFile(sessionFile, 200_000);
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String readFile(File file, int maxBytes) {
        if (file == null || !file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                if (total + n > maxBytes) n = maxBytes - total;
                if (n <= 0) break;
                out.write(buf, 0, n);
                total += n;
                if (total >= maxBytes) break;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            return "Cannot read diagnostic file: " + t;
        }
    }

    private static void copyFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst, false)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        } catch (Throwable t) {
            try (FileOutputStream out = new FileOutputStream(dst, false)) {
                out.write((header("CRASH_COPY_FAILED") + "\n" + stackTrace(t)).getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) { }
        }
    }

    private static String stackTrace(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Throwable ignored) {
            return t.getClass().getName() + ": " + safeMessage(t);
        }
    }

    private static String safeMessage(Throwable t) {
        try { return String.valueOf(t.getMessage()); }
        catch (Throwable ignored) { return "<message unavailable>"; }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
