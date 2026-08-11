from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
overlay = Path(sys.argv[2]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"
java_dir.mkdir(parents=True, exist_ok=True)

for name in ["AiAccessActivity.java", "AiAccessLog.java", "SocksProbe.java", "AiAccessPrefs.kt"]:
    shutil.copy2(overlay / name, java_dir / name)

# Replace upstream SmartConnect with our anti-stall implementation.
smart_dst = app / "src/main/java/org/torproject/android/service/circumvention/SmartConnect.kt"
shutil.copy2(overlay / "SmartConnect.kt", smart_dst)

# Pin our application identity/version and build arm64 only.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace("val orbotBaseVersionCode = 1795300400", "val orbotBaseVersionCode = 201")
s = s.replace('applicationId = namespace', 'applicationId = "com.bmw1975487.aione.routefix"')
s = s.replace('versionName = getVersionName().get()', 'versionName = "0.2.1-autorecovery"')
s = s.replace('applicationIdSuffix = ".debug"', '// AI Access: no debug suffix; separate applicationId already used')
s = s.replace('include("x86", "armeabi-v7a", "x86_64", "arm64-v8a")', 'include("arm64-v8a")')
s = s.replace('isUniversalApk = true', 'isUniversalApk = false')
s = s.replace('archivesName.set("Orbot-${android.defaultConfig.versionName}")', 'archivesName.set("AI_Access_One_v0.2.1_AUTORECOVERY")')
gradle.write_text(s, encoding="utf-8")

manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")

# Explicit package visibility for the target app and MAX log sharing.
needle = "    <queries>\n"
insert = "    <queries>\n        <package android:name=\"com.openai.chatgpt\" />\n        <package android:name=\"ru.oneme.app\" />\n"
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
            android:name="org.torproject.android.OrbotActivity"
            android:excludeFromRecents="false"
            android:exported="false"
            android:launchMode="singleInstance" />
'''
if old not in m:
    raise SystemExit("launcher Activity block not found; upstream changed")
m = m.replace(old, new, 1)
m = m.replace('android:label="@string/app_name"', 'android:label="AI Access One"', 1)

# FileProvider for sending one diagnostic ZIP to MAX.
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

# Small source-level edits to the launcher: SmartConnect events + ZIP sharing.
activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")
a = a.replace('import android.net.VpnService;', 'import android.net.Uri;\nimport android.net.VpnService;')
a = a.replace('import androidx.core.content.ContextCompat;', 'import androidx.core.content.ContextCompat;\nimport androidx.core.content.FileProvider;')
a = a.replace('import org.torproject.android.service.OrbotService;', 'import org.torproject.android.service.OrbotService;\nimport org.torproject.android.service.circumvention.SmartConnect;')
a = a.replace('import org.torproject.jni.TorService;', 'import org.torproject.jni.TorService;\n\nimport java.io.File;')
a = a.replace('private static final String MAX = "ru.oneme.app";', 'private static final String MAX = "ru.oneme.app";\n    private static final String SMART_ACTION = SmartConnect.AI_ACTION;')
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

share_start = a.find('    private void shareLog() {')
share_end = a.find('    private void fail(', share_start)
if share_start < 0 or share_end < 0:
    raise SystemExit("Activity shareLog block not found")
new_share = '''    private void shareLog() {
        AiAccessLog.i(this, "LOG_SHARE_REQUEST", "target=MAX mode=zip");
        try {
            File zip = AiAccessLog.createZip(this);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".aiaccess.files", zip);
            Intent direct = new Intent(Intent.ACTION_SEND);
            direct.setType("application/zip");
            direct.putExtra(Intent.EXTRA_SUBJECT, "AI Access One route diagnostic ZIP");
            direct.putExtra(Intent.EXTRA_STREAM, uri);
            direct.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            direct.setPackage(MAX);
            AiAccessLog.i(this, "LOG_SHARE_ZIP", "file=" + zip.getName() + " bytes=" + zip.length());
            if (direct.resolveActivity(getPackageManager()) != null) {
                startActivity(direct);
                return;
            }

            Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("application/zip");
            fallback.putExtra(Intent.EXTRA_SUBJECT, "AI Access One route diagnostic ZIP");
            fallback.putExtra(Intent.EXTRA_STREAM, uri);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(fallback, "Отправить ZIP-лог"));
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SHARE_ZIP_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Не удалось создать ZIP: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

'''
a = a[:share_start] + new_share + a[share_end:]
a = a.replace('Движок: Tor/Orbot SmartConnect. Выходы тестируются автоматически.', 'Движок: Tor/Orbot AutoRecovery. Зависший transport автоматически меняется; лог отправляется одним ZIP.')
activity.write_text(a, encoding="utf-8")

print("AI Access v0.2.1 AutoRecovery overlay applied")
print("Orbot source:", root)
print("ApplicationId: com.bmw1975487.aione.routefix")
print("Target: com.openai.chatgpt")
print("Recovery: RU MOAT + 15s anti-stall + multi-transport")
