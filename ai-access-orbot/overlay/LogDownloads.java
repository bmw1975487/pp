package org.torproject.android;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Saves the diagnostic ZIP directly into the public Downloads collection on Android 10+. */
public final class LogDownloads {
    private LogDownloads() {}

    public static String save(Context context) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new UnsupportedOperationException("Direct Downloads save requires Android 10+");
        }

        File zip = AiAccessLog.createZip(context);
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, zip.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AI Access One");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore insert returned null");

        boolean completed = false;
        long total = 0;
        try (FileInputStream in = new FileInputStream(zip);
             OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("MediaStore output stream is null");
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.flush();
            completed = true;
        } finally {
            if (completed) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, ready, null, null);
            } else {
                try { resolver.delete(uri, null, null); } catch (Throwable ignored) {}
            }
        }

        String relative = "Download/AI Access One/" + zip.getName();
        AiAccessLog.i(context, "LOG_DOWNLOADS_SAVED", "uri=" + uri + " bytes=" + total + " path=" + relative);
        return relative;
    }
}
