from pathlib import Path
import re, shutil

ROOT = Path("ByeByeDPI")
APP = ROOT / "app"

g = APP / "build.gradle.kts"
s = g.read_text()
s = s.replace('applicationId = "io.github.romanvht.byedpi"', 'applicationId = "com.bmw1975487.youtubeaccess"')
s = s.replace("versionCode = 1770", "versionCode = 101")
s = s.replace('versionName = "1.7.7"', 'versionName = "0.2.0"')
s = s.replace("compileSdk = 36", 'compileSdk = 36\n    ndkVersion = "27.2.12479018"')
g.write_text(s)

for f in APP.glob("src/main/res/values*/strings.xml"):
    s = f.read_text()
    s = re.sub(r'<string name="app_name">.*?</string>', '<string name="app_name">FreeVideo</string>', s)
    s = re.sub(r'<string name="notification_title">.*?</string>', '<string name="notification_title">FreeVideo</string>', s)
    s = re.sub(r'<string name="vpn_notification_content">.*?</string>', '<string name="vpn_notification_content">FreeVideo — видеомаршрут активен</string>', s)
    f.write_text(s)

icon = APP / "src/main/res/drawable/ic_freevideo_app.xml"
icon.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#090B1D" android:pathData="M54,2 A52,52 0,1 1,53.9 2" />
    <path android:fillColor="#6E4CFF" android:pathData="M20,31 L67,31 L67,77 L20,77 Z" />
    <path android:fillColor="#30CFFF" android:pathData="M67,41 L91,29 L91,79 L67,67 Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M38,41 L38,67 L58,54 Z" />
    <path android:fillColor="#7D78FF" android:fillAlpha="0.36" android:pathData="M15,24 L75,24 L75,84 L15,84 Z" />
</vector>
''')

f = APP / "src/main/java/io/github/romanvht/byedpi/services/ByeDpiVpnService.kt"
s = f.read_text()
start = s.index('        val preferences = getPreferences()\n        val listType = preferences.getStringNotNull("applist_type", "disable")')
end = s.index('\n        return builder', start)
block = '''        try {\n            builder.addAllowedApplication("com.google.android.youtube")\n            Log.i(TAG, "APP_ONLY enabled for com.google.android.youtube")\n        } catch (e: Exception) {\n            Log.e(TAG, "Official YouTube package is not installed", e)\n            throw IllegalStateException("Official YouTube package is not installed", e)\n        }\n'''
s = s[:start] + block + s[end:]
s = s.replace('builder.setSession("ByeDPI")', 'builder.setSession("FreeVideo")')
f.write_text(s)

f = APP / "src/main/AndroidManifest.xml"
s = f.read_text()
if '<queries>' not in s:
    s = s.replace('<uses-feature android:name="android.software.leanback"', '<queries>\n        <package android:name="com.google.android.youtube" />\n    </queries>\n\n    <uses-feature android:name="android.software.leanback"')
s = s.replace('android:logo="@mipmap/ic_launcher"', 'android:logo="@drawable/ic_freevideo_app"')
s = s.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/ic_freevideo_app"')
s = s.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/ic_freevideo_app"')
s = s.replace('android:name=".activities.SettingsActivity"\n            android:label="@string/title_settings"\n            android:exported="true"', 'android:name=".activities.SettingsActivity"\n            android:label="@string/title_settings"\n            android:exported="false"')
f.write_text(s)

shutil.copyfile("youtube-access/MainActivity.kt", APP / "src/main/java/io/github/romanvht/byedpi/activities/MainActivity.kt")

(ROOT / "FREEVIDEO_SOURCE_INFO.txt").write_text('''FreeVideo v0.2.0\nBase: romanvht/ByeByeDPI v1.7.7 (GPL-3.0)\nNetwork engine: hufrea/byedpi + heiher/hev-socks5-tunnel.\n\nChanges in v0.2.0:\n- FreeVideo interface based on the supplied six-screen visual reference.\n- official YouTube-only Android VpnService routing.\n- IPv4-first ByeDPI outbound connections (-I 0.0.0.0).\n- TCP-first profiles that reject QUIC/UDP and force YouTube to retry over TLS.\n- domain-scoped strategies for googlevideo.com / YouTube service domains.\n- GoogleVideo report_mapping health probe instead of probing API roots.\n- automatic strategy rotation and remembered last-successful route.\n- local log export.\n\nThis patched tree is the Corresponding Source used for the APK.\n''')
print("FreeVideo v0.2.0 patch applied")
