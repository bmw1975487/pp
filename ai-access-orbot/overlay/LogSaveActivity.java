package org.torproject.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Small helper Activity that lets the user choose any folder via Android's system document picker. */
public final class LogSaveActivity extends Activity {
    private static final int REQ_SAVE_ZIP = 7721;
    private File pendingZip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            AiAccessLog.i(this, "LOG_SAVE_REQUEST", "mode=ACTION_CREATE_DOCUMENT");
            pendingZip = AiAccessLog.createZip(this);

            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, pendingZip.getName());
            startActivityForResult(i, REQ_SAVE_ZIP);
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SAVE_PREPARE_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Не удалось создать ZIP-лог: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE_ZIP) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            AiAccessLog.w(this, "LOG_SAVE_CANCELLED", "user cancelled folder/file selection");
            finish();
            return;
        }

        Uri target = data.getData();
        try {
            if (pendingZip == null || !pendingZip.exists()) {
                pendingZip = AiAccessLog.createZip(this);
            }
            try (FileInputStream in = new FileInputStream(pendingZip);
                 OutputStream out = getContentResolver().openOutputStream(target, "w")) {
                if (out == null) throw new IllegalStateException("ContentResolver returned null OutputStream");
                byte[] buf = new byte[32 * 1024];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
                out.flush();
                AiAccessLog.i(this, "LOG_ZIP_SAVED", "uri=" + target + " bytes=" + total + " file=" + pendingZip.getName());
                Toast.makeText(this, "ZIP-лог сохранён", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SAVE_WRITE_FAIL", "uri=" + target + " message=" + String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Ошибка сохранения ZIP: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
