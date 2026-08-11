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
s = s.replace("val orbotBaseVersionCode = 1795300400", "val orbotBaseVersionCode = 204")
s = s.replace('applicationId = namespace', 'applicationId = "com.bmw1975487.aione.routefix4"')
s = s.replace('versionName = getVersionName().get()', 'versionName = "0.2.4-bootstrapgate"')
s = s.replace('applicationIdSuffix = ".debug"', '// AI Access: no debug suffix; separate applicationId already used')
s = s.replace('include("x86", "armeabi-v7a", "x86_64", "arm64-v8a")', 'include("arm64-v8a")')
s = s.replace('isUniversalApk = true', 'isUniversalApk = false')
s = s.replace('archivesName.set("Orbot-${android.defaultConfig.versionName}")', 'archivesName.set("AI_Access_One_v0.2.4_BOOTSTRAP_GATE")')
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
log_text = log_text.replace('public static final String VERSION = "0.2.1-autorecovery";', 'public static final String VERSION = "0.2.4-bootstrapgate";')
log_file.write_text(log_text, encoding="utf-8")

# Source-level edits to launcher: bootstrap gate + SmartConnect events + ZIP sharing.
activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")
a = a.replace('import android.net.VpnService;', 'import android.net.Uri;\nimport android.net.VpnService;')
a = a.replace('import androidx.core.content.ContextCompat;', 'import androidx.core.content.ContextCompat;\nimport androidx.core.content.FileProvider;')
a = a.replace('import org.torproject.android.service.OrbotService;', 'import org.torproject.android.service.OrbotService;\nimport org.torproject.android.service.circumvention.SmartConnect;')
a = a.replace('import org.torproject.jni.TorService;', 'import org.torproject.jni.TorService;\n\nimport java.io.File;')
a = a.replace('private static final String MAX = "ru.oneme.app";', 'private static final String MAX = "ru.oneme.app";\n    private static final String[] TELEGRAM_PACKAGES = {"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"};\n    private static final String SMART_ACTION = SmartConnect.AI_ACTION;')
a = a.replace('private Button powerButton, probeButton, shareButton;', 'private Button powerButton, probeButton, shareButton, telegramButton, saveButton;')
a = a.replace('private int probeGeneration = 0;', 'private int probeGeneration = 0;\n    private boolean torBootstrapped = false;\n    private boolean routeProbeArmed = false;')
a = a.replace('version=0.2.0-orbot-route', 'version=' + '" + AiAccessLog.VERSION + "')
a = a.replace('f.addAction(OrbotConstants.LOCAL_ACTION_PORTS);', 'f.addAction(OrbotConstants.LOCAL_ACTION_PORTS);\n        f.addAction(SMART_ACTION);')

ports_marker = '''            } else if (OrbotConstants.LOCAL_ACTION_PORTS.equals(action)) {'''
smart_branch = '''            } else if (SMART_ACTION.equals(action)) {
                String event = intent.getStringExtra(SmartConnect.EXTRA_EVENT);
                String smartDetail = intent.getStringExtra(SmartConnect.EXTRA_DETAIL);
                AiAccessLog.i(AiAccessActivity.this, "SMARTCONNECT_" + String.valueOf(event), String.valueOf(smartDetail));
                if ("BOOTSTRAP_COMPLETE".equals(event)) {
                    onTorBootstrapped("SMARTCONNECT");
                } else if ("TRANSPORT_EXHAUSTED".equals(event)) {
                    state = "ERROR";
                    detail = "Tor не смог пройти bootstrap · transport-пул исчерпан";
                } else if ("BOOTSTRAP_STALL".equals(event) || "TRANSPORT_SWITCH".equals(event) || "AUTOCONF_OK".equals(event)) {
                    state = "CONNECTING";
                    detail = "AutoRecovery · " + String.valueOf(smartDetail);
                }
            } else if (OrbotConstants.LOCAL_ACTION_PORTS.equals(action)) {'''
if ports_marker not in a:
    raise SystemExit("Activity ports marker not found")
a = a.replace(ports_marker, smart_branch, 1)

# Never probe merely because SOCKS ports exist: Orbot exposes them before bootstrap=100.
a = a.replace('if (socksPort > 0) scheduleProbe(1800);', 'if (socksPort > 0) {\n                    if (torBootstrapped && routeProbeArmed) scheduleProbe(1500);\n                    else AiAccessLog.i(AiAccessActivity.this, "ROUTE_PROBE_GATE", "SOCKS ready; waiting for bootstrap=100");\n                }')

old_status_on = '''                if (TorService.STATUS_ON.equals(status)) {
                    state = "TOR_ON";
                    detail = "Tor подключён · проверяю маршрут " + currentExit().toUpperCase();
                    scheduleProbe(1400);
                } else if (TorService.STATUS_OFF.equals(status)) {'''
new_status_on = '''                if (TorService.STATUS_ON.equals(status)) {
                    onTorBootstrapped("TOR_STATUS_ON");
                } else if (TorService.STATUS_OFF.equals(status)) {'''
if old_status_on not in a:
    raise SystemExit("Activity TOR_STATUS_ON block not found")
a = a.replace(old_status_on, new_status_on, 1)

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

# Hard safety gate: if official ChatGPT is not installed in this Android profile,
# do not start Orbot VPN. Otherwise upstream per-app routing can fall back to other apps.
power_marker = '''        exitIndex = 0;
        probeGeneration++;
        AiAccessPrefs.configure(this, currentExit());'''
power_guard = '''        try {
            getPackageManager().getPackageInfo(CHATGPT, 0);
            AiAccessLog.i(this, "CHATGPT_TARGET_CONFIRMED", CHATGPT);
        } catch (Throwable t) {
            state = "ERROR";
            detail = "Официальный ChatGPT не найден в этом профиле Android";
            AiAccessLog.e(this, "CHATGPT_TARGET_REQUIRED", detail + " package=" + CHATGPT, t);
            Toast.makeText(this, "Установите официальный ChatGPT в том же профиле Android.", Toast.LENGTH_LONG).show();
            render();
            return;
        }
        exitIndex = 0;
        probeGeneration++;
        torBootstrapped = false;
        routeProbeArmed = false;
        AiAccessPrefs.configure(this, currentExit());'''
if power_marker not in a:
    raise SystemExit("Activity power marker not found")
a = a.replace(power_marker, power_guard, 1)

# Reset bootstrap gate on each engine start/stop.
a = a.replace('            state = "CONNECTING";\n            detail = "SmartConnect · exit " + currentExit().toUpperCase();', '            torBootstrapped = false;\n            routeProbeArmed = false;\n            state = "CONNECTING";\n            detail = "SmartConnect · сначала bootstrap 100%";')
a = a.replace('        socksPort = -1;\n        render();', '        socksPort = -1;\n        torBootstrapped = false;\n        routeProbeArmed = false;\n        render();', 1)

# Add bootstrap completion gate before scheduleProbe().
schedule_marker = '    private void scheduleProbe(long delayMs) {'
on_ready = '''    private void onTorBootstrapped(String source) {
        if (torBootstrapped && routeProbeArmed) return;
        torBootstrapped = true;
        state = "TOR_ON";
        detail = "Tor bootstrap 100% · готовлю выход " + currentExit().toUpperCase();
        AiAccessLog.i(this, "BOOTSTRAP_GATE_OPEN", "source=" + source + " socks=" + socksPort + " exit=" + currentExit());
        if (!routeProbeArmed) {
            routeProbeArmed = true;
            // Only now may CMD_SET_EXIT toggle DisableNetwork and build a country-specific circuit.
            switchExit(currentExit());
            scheduleProbe(9000);
        }
        render();
    }

'''
if schedule_marker not in a:
    raise SystemExit("Activity scheduleProbe marker not found")
a = a.replace(schedule_marker, on_ready + schedule_marker, 1)

# Manual/automatic probes are forbidden before Tor reaches 100%.
probe_marker = '''    private void startProbe(boolean auto) {
        if (probing) return;
        if (socksPort <= 0) {'''
probe_gate = '''    private void startProbe(boolean auto) {
        if (probing) return;
        if (!torBootstrapped) {
            AiAccessLog.w(this, "ROUTE_PROBE_BLOCKED", "bootstrap<100; probe/exit switching forbidden");
            if (!auto) Toast.makeText(this, "Сначала Tor должен дойти до 100%", Toast.LENGTH_SHORT).show();
            return;
        }
        if (socksPort <= 0) {'''
if probe_marker not in a:
    raise SystemExit("Activity startProbe marker not found")
a = a.replace(probe_marker, probe_gate, 1)

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
a = a.replace('Движок: Tor/Orbot SmartConnect. Выходы тестируются автоматически.', 'Движок: Tor/Orbot BootstrapGate. До 100% запрещены OpenAI-probe и смена exit. ZIP-лог: MAX/Telegram/Downloads.')
activity.write_text(a, encoding="utf-8")

# Upstream callback previously only displayed an error when SmartConnect exhausted all
# transports, leaving Tor alive in the background. Stop it for real so retries start clean.
service = java_dir / "service/OrbotService.java"
svc = service.read_text(encoding="utf-8")
old_error = '''                    if (e != null) {
                        logNotice(getString(R.string.unable_to_start_tor) + " " + e.getLocalizedMessage());
                        stopTorOnError(e.getLocalizedMessage());
                    } else {
                        //     stopTorAsync(true);
                    }
'''
new_error = '''                    if (e != null) {
                        logNotice(getString(R.string.unable_to_start_tor) + " " + e.getLocalizedMessage());
                        stopTorOnError(e.getLocalizedMessage());
                        stopTorAsync(false);
                    } else {
                        // no-op
                    }
'''
if old_error not in svc:
    raise SystemExit("OrbotService SmartConnect error callback not found")
svc = svc.replace(old_error, new_error, 1)
service.write_text(svc, encoding="utf-8")

print("AI Access v0.2.4 BootstrapGate overlay applied")
print("Orbot source:", root)
print("ApplicationId: com.bmw1975487.aione.routefix4")
print("Target: com.openai.chatgpt")
print("Bootstrap gate: no probe/exit switch before 100%")
print("Recovery: RU MOAT + adaptive watchdog + clean stop on exhaustion")
print("Logs: ZIP to MAX + Telegram + direct Downloads MediaStore")
