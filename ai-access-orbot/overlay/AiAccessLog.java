package org.torproject.android;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AiAccessLog {
    private static final String TAG = "AI-ACCESS-ROUTE";
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 768 * 1024;
    private static final String FILE = "ai-access-route.log";
    private static volatile String appState = "OFF";
    private static volatile String appDetail = "";

    private AiAccessLog() {}

    public static void setState(String state, String detail) {
        appState = state == null ? "" : state;
        appDetail = detail == null ? "" : detail;
    }

    public static void i(Context c, String event, String detail) {
        Log.i(TAG, event + " " + safe(detail));
        write(c, "I", event, detail, null);
    }

    public static void w(Context c, String event, String detail) {
        Log.w(TAG, event + " " + safe(detail));
        write(c, "W", event, detail, null);
    }

    public static void e(Context c, String event, String detail, Throwable t) {
        Log.e(TAG, event + " " + safe(detail), t);
        write(c, "E", event, detail, t);
    }

    public static String tail(Context c, int maxChars) {
        synchronized (LOCK) {
            File f = file(c);
            if (!f.exists()) return "Лог пока пуст.";
            try (FileInputStream in = new FileInputStream(f);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                String s = out.toString(StandardCharsets.UTF_8.name());
                if (s.length() > maxChars) s = s.substring(s.length() - maxChars);
                return s;
            } catch (Throwable t) {
                return "Не удалось прочитать лог: " + t.getClass().getSimpleName() + ": " + safe(t.getMessage());
            }
        }
    }

    public static String sharePayload(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("AI ACCESS ONE ROUTE LOG\n");
        b.append("version=0.2.0-orbot-route\n");
        b.append("sdk=").append(Build.VERSION.SDK_INT).append("\n");
        b.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n");
        b.append("package=").append(c.getPackageName()).append("\n");
        b.append("state=").append(appState).append("\n");
        b.append("detail=").append(appDetail).append("\n\n");
        b.append(tail(c, 100_000));
        return b.toString();
    }

    private static void write(Context c, String level, String event, String detail, Throwable t) {
        if (c == null) return;
        synchronized (LOCK) {
            try {
                File f = file(c);
                if (f.exists() && f.length() > MAX_BYTES) {
                    File old = new File(c.getFilesDir(), FILE + ".old");
                    if (old.exists()) old.delete();
                    if (!f.renameTo(old)) f.delete();
                }
                try (FileWriter w = new FileWriter(file(c), true)) {
                    w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()));
                    w.write(" [" + level + "] [" + Thread.currentThread().getName() + "] " + safe(event));
                    if (detail != null && !detail.isEmpty()) w.write(" | " + safe(detail));
                    w.write('\n');
                    if (t != null) {
                        StringWriter sw = new StringWriter();
                        t.printStackTrace(new PrintWriter(sw));
                        w.write(sw.toString());
                        if (!sw.toString().endsWith("\n")) w.write('\n');
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }
    private static String safe(String s) {
        if (s == null) return "";
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}
