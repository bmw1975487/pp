package ru.nika.v3test;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class KeyProvider {
    private static final String PLACEHOLDER =
        "sk-PLACEHOLDER-NEON-V3-KEY-00000000";
    private static final Pattern KEY_PATTERN = Pattern.compile(
        "sk-[A-Za-z0-9_-]{32}"
    );

    private KeyProvider() {}

    static String resolve(Context context) throws IOException {
        String embedded = PrivateConfig.ROUTER_AI_KEY;
        if (valid(embedded) && !PLACEHOLDER.equals(embedded)) {
            return embedded;
        }
        String extracted = fromInstalledNeon(context);
        if (valid(extracted)) return extracted;
        throw new IOException(
            "RouterAI-ключ не найден. Установите рядом рабочий Neon или используйте подписанную V3-сборку."
        );
    }

    private static boolean valid(String key) {
        return key != null && key.length() == 35 && key.startsWith("sk-");
    }

    private static String fromInstalledNeon(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo("ru.nika.voice", 0);
            File apk = new File(info.sourceDir);
            if (!apk.isFile()) return null;
            try (ZipFile zip = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.matches("classes(\\d*)\\.dex")) continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        String found = scan(in, 64 * 1024 * 1024);
                        if (found != null) return found;
                    }
                }
            }
        } catch (Exception ignored) {
            // Exact failure is recorded by the V3 engine when no key can be resolved.
        }
        return null;
    }

    private static String scan(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) throw new IOException("DEX слишком большой");
            out.write(buffer, 0, read);
        }
        String body = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        Matcher matcher = KEY_PATTERN.matcher(body);
        while (matcher.find()) {
            String value = matcher.group();
            if (!PLACEHOLDER.equals(value)) return value;
        }
        return null;
    }
}
