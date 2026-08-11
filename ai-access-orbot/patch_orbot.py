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

# Pin our application identity/version and build arm64 only.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace("val orbotBaseVersionCode = 1795300400", "val orbotBaseVersionCode = 200")
s = s.replace('applicationId = namespace', 'applicationId = "com.bmw1975487.aione.route"')
s = s.replace('versionName = getVersionName().get()', 'versionName = "0.2.0-orbot-route"')
s = s.replace('applicationIdSuffix = ".debug"', '// AI Access: no debug suffix; separate applicationId already used')
s = s.replace('include("x86", "armeabi-v7a", "x86_64", "arm64-v8a")', 'include("arm64-v8a")')
s = s.replace('isUniversalApk = true', 'isUniversalApk = false')
s = s.replace('archivesName.set("Orbot-${android.defaultConfig.versionName}")', 'archivesName.set("AI_Access_One_v0.2.0_ROUTE")')
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
manifest.write_text(m, encoding="utf-8")

print("AI Access overlay applied")
print("Orbot source:", root)
print("ApplicationId: com.bmw1975487.aione.route")
print("Target: com.openai.chatgpt")
