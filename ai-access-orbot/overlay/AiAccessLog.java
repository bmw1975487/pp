package org.torproject.android;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AiAccessLog {
    public static final String VERSION = "0.2.1-autorecovery";
    private static final String TAG = "AI-ACCESS-ROUTE";
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 1024 * 1024;
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
        String s = readAll(c);
        if (s.isEmpty()) return "Лог пока пуст.";
        if (s.length() > maxChars) s = s.substring(s.length() - maxChars);
        return s;
    }

    public static String sharePayload(Context c) {
        return deviceInfo(c) + "\n" + readAll(c);
    }

    /** Creates one shareable diagnostic ZIP. No credentials, cookies or ChatGPT content are collected. */
    public static File createZip(Context c) throws Exception {
        synchronized (LOCK) {
            File dir = new File(c.getCacheDir(), "ai-access-share");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create log share directory");
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) if (f.isFile()) f.delete();

            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            File out = new File(dir, "AI_Access_One_Log_" + stamp + ".zip");
            String all = readAll(c);

            StringBuilder tor = new StringBuilder();
            StringBuilder route = new StringBuilder();
            StringBuilder errors = new StringBuilder();
            for (String line : all.split("\\n")) {
                if (containsAny(line, "TOR_", "BOOTSTRAP", "SMARTCONNECT", "TRANSPORT_", "AUTOCONF", "known bridges")) tor.append(line).append('\n');
                if (containsAny(line, "ROUTE_", "EXIT_", "OPENAI", "CHATGPT", "TRANSPORT_", "SMARTCONNECT", "BOOTSTRAP_")) route.append(line).append('\n');
                if (containsAny(line, " [E] ", " [W] ", "FAIL", "ERROR", "REJECTED", "STALL", "EXHAUSTED")) errors.append(line).append('\n');
            }

            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
                put(z, "01_MAIN_LOG.txt", deviceInfo(c) + "\n" + all);
                put(z, "02_TOR_LOG.txt", tor.length() == 0 ? "No Tor-specific entries.\n" : tor.toString());
                put(z, "03_ROUTE_STATUS.txt", "state=" + appState + "\ndetail=" + appDetail + "\n\n" + route);
                put(z, "04_DEVICE.txt", deviceInfo(c));
                put(z, "05_ERRORS.txt", errors.length() == 0 ? "No warnings/errors recorded.\n" : errors.toString());
            }
            i(c, "LOG_ZIP_READY", "file=" + out.getName() + " bytes=" + out.length());
            return out;
        }
    }

    private static String deviceInfo(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("AI ACCESS ONE ROUTE LOG\n");
        b.append("version=").append(VERSION).append("\n");
        b.append("sdk=").append(Build.VERSION.SDK_INT).append("\n");
        b.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n");
        b.append("package=").append(c.getPackageName()).append("\n");
        b.append("state=").append(appState).append("\n");
        b.append("detail=").append(appDetail).append("\n");
        return b.toString();
    }

    private static void put(ZipOutputStream z, String name, String text) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        z.write(bytes);
        z.closeEntry();
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static String readAll(Context c) {
        synchronized (LOCK) {
            StringBuilder b = new StringBuilder();
            appendFile(b, new File(c.getFilesDir(), FILE + ".old"));
            appendFile(b, file(c));
            return b.toString();
        }
    }

    private static void appendFile(StringBuilder b, File f) {
        if (!f.exists()) return;
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            b.append(out.toString(StandardCharsets.UTF_8.name()));
            if (b.length() > 0 && b.charAt(b.length() - 1) != '\n') b.append('\n');
        } catch (Throwable ignored) {}
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
