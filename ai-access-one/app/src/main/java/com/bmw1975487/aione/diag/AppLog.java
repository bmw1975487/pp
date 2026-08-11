package com.bmw1975487.aione.diag;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.bmw1975487.aione.core.StateStore;

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

public final class AppLog {
    private static final String TAG = "AI-ACCESS";
    private static final Object LOCK = new Object();
    private static final int MAX_FILE_BYTES = 512 * 1024;
    private static final int DEFAULT_TAIL_CHARS = 60_000;

    private AppLog() {}

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

    public static String tail(Context c) {
        return tail(c, DEFAULT_TAIL_CHARS);
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
        b.append("AI ACCESS ONE DIAGNOSTIC LOG\n");
        b.append("version=0.1.2-diagfix\n");
        b.append("sdk=").append(Build.VERSION.SDK_INT).append("\n");
        b.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n");
        b.append("state=").append(StateStore.state(c)).append("\n");
        b.append("detail=").append(StateStore.detail(c)).append("\n\n");
        b.append(tail(c, DEFAULT_TAIL_CHARS));
        return b.toString();
    }

    private static void write(Context c, String level, String event, String detail, Throwable t) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        synchronized (LOCK) {
            try {
                rotateIfNeeded(app);
                File f = file(app);
                try (FileWriter w = new FileWriter(f, true)) {
                    w.write(ts());
                    w.write(" [");
                    w.write(level);
                    w.write("] [");
                    w.write(Thread.currentThread().getName());
                    w.write("] ");
                    w.write(safe(event));
                    if (detail != null && !detail.isEmpty()) {
                        w.write(" | ");
                        w.write(safe(detail));
                    }
                    w.write('\n');
                    if (t != null) {
                        StringWriter sw = new StringWriter();
                        t.printStackTrace(new PrintWriter(sw));
                        w.write(sw.toString());
                        if (!sw.toString().endsWith("\n")) w.write('\n');
                    }
                }
            } catch (Throwable ignored) {
                // Logging must never crash the app.
            }
        }
    }

    private static void rotateIfNeeded(Context c) {
        File f = file(c);
        if (!f.exists() || f.length() < MAX_FILE_BYTES) return;
        File prev = new File(c.getFilesDir(), "aione-prev.log");
        if (prev.exists()) prev.delete();
        if (!f.renameTo(prev)) f.delete();
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "aione.log");
    }

    private static String ts() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}
