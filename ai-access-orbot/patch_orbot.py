from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
overlay = Path(sys.argv[2]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"
java_dir.mkdir(parents=True, exist_ok=True)

for name in ["AiAccessActivity.java", "AiAccessLog.java", "SocksProbe.java", "AiAccessPrefs.kt", "LogSaveActivity.java", "LogDownloads.java"]:
    shutil.copy2(overlay / name, java_dir / name)

# Replace upstream SmartConnect with our anti-stall implementation.
smart_dst = app / "src/main/java/org/torproject/android/service/circumvention/SmartConnect.kt"
shutil.copy2(overlay / "SmartConnect.kt", smart_dst)

# Pin our application identity/version and build arm64 only.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace("val orbotBaseVersionCode = 1795300400", "val orbotBaseVersionCode = 203")
s = s.replace('applicationId = namespace', 'applicationId = "com.bmw1975487.aione.routefix3"')
s = s.replace('versionName = getVersionName().get()', 'versionName = "0.2.3-logshare"')
s = s.replace('applicationIdSuffix = ".debug"', '// AI Access: no debug suffix; separate applicationId already used')
s = s.replace('include("x86", "armeabi-v7a", "x86_64", "arm64-v8a")', 'include("arm64-v8a")')
s = s.replace('isUniversalApk = true', 'isUniversalApk = false')
s = s.replace('archivesName.set("Orbot-${android.defaultConfig.versionName}")', 'archivesName.set("AI_Access_One_v0.2.3_LOGSHARE")')
gradle.write_text(s, encoding="utf-8")

manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")

# Explicit package visibility for target app and log-share destinations.
needle = "    <queries>\n"
insert = "    <queries>\n        <package android:name=\"com.openai.chatgpt\" />\n        <package android:name=\"ru.oneme.app\" />\n        <package android:name=\"org.telegram.messenger\" />\n        <package android:name=\"org.telegram.messenger.web\" />\n        <package android:name=\"org.thunderdog.challegram\" />\n"
if needle in m and 'package android:name="com.openai.chatgpt"' not in m:
    m = m.replace(needle, insert, 1)

# Use our one-button system Activity as the only enabled launcher.
old = '''        <activity
            android:name="org.torproject.android.OrbotActivity"
            android:excludeFromRecents="false"
            android:exported="true"
            android:launchMode="singleInstance">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
'''
new = '''        <activity
            android:name="org.torproject.android.AiAccessActivity"
            android:excludeFromRecents="false"
            android:exported="true"
            android:launchMode="singleTask"
            android:label="AI Access One"
            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="org.torproject.android.LogSaveActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@android:style/Theme.Material.NoActionBar" />

        <activity
            android:name="org.torproject.android.OrbotActivity"
            android:excludeFromRecents="false"
            android:exported="false"
            android:launchMode="singleInstance" />
'''
if old not in m:
    raise SystemExit("launcher Activity block not found; upstream changed")
m = m.replace(old, new, 1)
m = m.replace('android:label="@string/app_name"', 'android:label="AI Access One"', 1)

# FileProvider for sending one diagnostic ZIP to MAX/Telegram/system share sheet.
provider = '''
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.aiaccess.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/ai_access_file_paths" />
        </provider>

'''
if '.aiaccess.files' not in m:
    m = m.replace('    </application>', provider + '    </application>', 1)
manifest.write_text(m, encoding="utf-8")

xml_dir = app / "src/main/res/xml"
xml_dir.mkdir(parents=True, exist_ok=True)
(xml_dir / "ai_access_file_paths.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="ai_access_logs" path="ai-access-share/" />
</paths>
''', encoding="utf-8")

# Version label inside ZIP/device info.
log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8")
log_text = log_text.replace('public static final String VERSION = "0.2.1-autorecovery";', 'public static final String VERSION = "0.2.3-logshare";')
log_file.write_text(log_text, encoding="utf-8")

# Source-level edits to launcher: SmartConnect events + MAX/Telegram ZIP sharing + direct Downloads save.
activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")
a = a.replace('import android.net.VpnService;', 'import android.net.Uri;\nimport android.net.VpnService;')
a = a.replace('import androidx.core.content.ContextCompat;', 'import androidx.core.content.ContextCompat;\nimport androidx.core.content.FileProvider;')
a = a.replace('import org.torproject.android.service.OrbotService;', 'import org.torproject.android.service.OrbotService;\nimport org.torproject.android.service.circumvention.SmartConnect;')
a = a.replace('import org.torproject.jni.TorService;', 'import org.torproject.jni.TorService;\n\nimport java.io.File;')
a = a.replace('private static final String MAX = "ru.oneme.app";', 'private static final String MAX = "ru.oneme.app";\n    private static final String[] TELEGRAM_PACKAGES = {"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"};\n    private static final String SMART_ACTION = SmartConnect.AI_ACTION;')
a = a.replace('private Button powerButton, probeButton, shareButton;', 'private Button powerButton, probeButton, shareButton, telegramButton, saveButton;')
a = a.replace('version=0.2.0-orbot-route', 'version=' + '" + AiAccessLog.VERSION + "')
a = a.replace('f.addAction(OrbotConstants.LOCAL_ACTION_PORTS);', 'f.addAction(OrbotConstants.LOCAL_ACTION_PORTS);\n        f.addAction(SMART_ACTION);')

ports_marker = '''            } else if (OrbotConstants.LOCAL_ACTION_PORTS.equals(action)) {'''
smart_branch = '''            } else if (SMART_ACTION.equals(action)) {
                String event = intent.getStringExtra(SmartConnect.EXTRA_EVENT);
                String smartDetail = intent.getStringExtra(SmartConnect.EXTRA_DETAIL);
                AiAccessLog.i(AiAccessActivity.this, "SMARTCONNECT_" + String.valueOf(event), String.valueOf(smartDetail));
                if ("BOOTSTRAP_STALL".equals(event) || "TRANSPORT_SWITCH".equals(event) || "AUTOCONF_OK".equals(event)) {
                    state = "CONNECTING";
                    detail = "AutoRecovery · " + String.valueOf(smartDetail);
                }
            } else if (OrbotConstants.LOCAL_ACTION_PORTS.equals(action)) {'''
if ports_marker not in a:
    raise SystemExit("Activity ports marker not found")
a = a.replace(ports_marker, smart_branch, 1)

a = a.replace('shareButton = button("ОТПРАВИТЬ ЛОГ В MAX", CARD);', 'shareButton = button("ОТПРАВИТЬ ZIP В MAX", CARD);')
share_add = '        root.addView(shareButton, lp(-1, 56, 0, 0, 0, 18));'
new_buttons = '''        root.addView(shareButton, lp(-1, 56, 0, 0, 0, 9));

        telegramButton = button("ОТПРАВИТЬ ZIP В TELEGRAM", CARD);
        telegramButton.setOnClickListener(v -> shareTelegram());
        root.addView(telegramButton, lp(-1, 56, 0, 0, 0, 9));

        saveButton = button("СОХРАНИТЬ ZIP В DOWNLOADS", CARD);
        saveButton.setOnClickListener(v -> saveToDownloads());
        root.addView(saveButton, lp(-1, 56, 0, 0, 0, 18));'''
if share_add not in a:
    raise SystemExit("Activity share button marker not found")
a = a.replace(share_add, new_buttons, 1)

share_start = a.find('    private void shareLog() {')
share_end = a.find('    private void fail(', share_start)
if share_start < 0 or share_end < 0:
    raise SystemExit("Activity shareLog block not found")
new_share = '''    private Uri createZipUri(File zip) {
        return FileProvider.getUriForFile(this, getPackageName() + ".aiaccess.files", zip);
    }

    private Intent zipShareIntent(File zip, Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_SUBJECT, "AI Access One diagnostic ZIP");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(android.content.ClipData.newRawUri("AI Access One log", uri));
        return i;
    }

    private void shareLog() {
        AiAccessLog.i(this, "LOG_SHARE_REQUEST", "target=MAX mode=zip");
        try {
            File zip = AiAccessLog.createZip(this);
            Uri uri = createZipUri(zip);
            Intent direct = zipShareIntent(zip, uri);
            direct.setPackage(MAX);
            AiAccessLog.i(this, "LOG_SHARE_MAX", "file=" + zip.getName() + " bytes=" + zip.length());
            if (direct.resolveActivity(getPackageManager()) != null) {
                startActivity(direct);
                return;
            }
            AiAccessLog.w(this, "LOG_SHARE_MAX_UNAVAILABLE", "MAX handler not available");
            startActivity(Intent.createChooser(zipShareIntent(zip, uri), "Отправить ZIP-лог"));
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SHARE_MAX_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "MAX не сработал. Используйте Telegram или Downloads.", Toast.LENGTH_LONG).show();
        }
    }

    private void shareTelegram() {
        AiAccessLog.i(this, "LOG_SHARE_REQUEST", "target=Telegram mode=zip");
        try {
            File zip = AiAccessLog.createZip(this);
            Uri uri = createZipUri(zip);
            for (String pkg : TELEGRAM_PACKAGES) {
                Intent direct = zipShareIntent(zip, uri);
                direct.setPackage(pkg);
                if (direct.resolveActivity(getPackageManager()) != null) {
                    AiAccessLog.i(this, "LOG_SHARE_TELEGRAM", "package=" + pkg + " file=" + zip.getName() + " bytes=" + zip.length());
                    startActivity(direct);
                    return;
                }
            }
            AiAccessLog.w(this, "LOG_SHARE_TELEGRAM_UNAVAILABLE", "no Telegram package handler found; opening chooser");
            startActivity(Intent.createChooser(zipShareIntent(zip, uri), "Отправить ZIP в Telegram"));
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SHARE_TELEGRAM_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Telegram не принял ZIP. Используйте «Сохранить ZIP в Downloads».", Toast.LENGTH_LONG).show();
        }
    }

    private void saveToDownloads() {
        AiAccessLog.i(this, "LOG_DOWNLOADS_REQUEST", "direct MediaStore save");
        saveButton.setEnabled(false);
        saveButton.setText("СОХРАНЯЮ…");
        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    String path = LogDownloads.save(AiAccessActivity.this);
                    main.post(() -> Toast.makeText(AiAccessActivity.this, "ZIP сохранён: " + path, Toast.LENGTH_LONG).show());
                } else {
                    AiAccessLog.w(AiAccessActivity.this, "LOG_DOWNLOADS_FALLBACK", "Android < 10; opening document picker");
                    main.post(() -> startActivity(new Intent(AiAccessActivity.this, LogSaveActivity.class)));
                }
            } catch (Throwable t) {
                AiAccessLog.e(AiAccessActivity.this, "LOG_DOWNLOADS_FAIL", String.valueOf(t.getMessage()), t);
                main.post(() -> Toast.makeText(AiAccessActivity.this, "Ошибка сохранения ZIP: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show());
            } finally {
                main.post(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText("СОХРАНИТЬ ZIP В DOWNLOADS");
                });
            }
        }, "LogDownloads").start();
    }

'''
a = a[:share_start] + new_share + a[share_end:]
a = a.replace('Движок: Tor/Orbot SmartConnect. Выходы тестируются автоматически.', 'Движок: Tor/Orbot AutoRecovery. ZIP-лог можно отправить в MAX/Telegram или сохранить напрямую в Download/AI Access One.')
activity.write_text(a, encoding="utf-8")

print("AI Access v0.2.3 LogShare overlay applied")
print("Orbot source:", root)
print("ApplicationId: com.bmw1975487.aione.routefix3")
print("Target: com.openai.chatgpt")
print("Recovery: RU MOAT + 15s anti-stall + multi-transport")
print("Logs: ZIP to MAX + Telegram + direct Downloads MediaStore")
