from pathlib import Path
import re, shutil
ROOT=Path('ByeByeDPI'); APP=ROOT/'app'
g=APP/'build.gradle.kts'; s=g.read_text()
s=s.replace('applicationId = "io.github.romanvht.byedpi"','applicationId = "com.bmw1975487.youtubeaccess"')
s=s.replace('versionCode = 1770','versionCode = 100').replace('versionName = "1.7.7"','versionName = "0.1.0"')
s=s.replace('compileSdk = 36','compileSdk = 36\n    ndkVersion = "27.2.12479018"'); g.write_text(s)
for f in APP.glob('src/main/res/values*/strings.xml'):
    s=f.read_text(); s=re.sub(r'<string name="app_name">.*?</string>','<string name="app_name">YouTube Access</string>',s)
    s=re.sub(r'<string name="notification_title">.*?</string>','<string name="notification_title">YouTube Access</string>',s)
    s=re.sub(r'<string name="vpn_notification_content">.*?</string>','<string name="vpn_notification_content">YouTube Access работает</string>',s); f.write_text(s)
f=APP/'src/main/java/io/github/romanvht/byedpi/services/ByeDpiVpnService.kt'; s=f.read_text()
start=s.index('        val preferences = getPreferences()\n        val listType = preferences.getStringNotNull("applist_type", "disable")'); end=s.index('\n        return builder',start)
block='''        try {\n            builder.addAllowedApplication("com.google.android.youtube")\n            Log.i(TAG, "APP_ONLY enabled for com.google.android.youtube")\n        } catch (e: Exception) {\n            Log.e(TAG, "Official YouTube package is not installed", e)\n            throw IllegalStateException("Official YouTube package is not installed", e)\n        }\n'''
s=s[:start]+block+s[end:]; f.write_text(s)
f=APP/'src/main/AndroidManifest.xml'; s=f.read_text()
if '<queries>' not in s: s=s.replace('<uses-feature android:name="android.software.leanback"','<queries>\n        <package android:name="com.google.android.youtube" />\n    </queries>\n\n    <uses-feature android:name="android.software.leanback"')
s=s.replace('android:name=".activities.SettingsActivity"\n            android:label="@string/title_settings"\n            android:exported="true"','android:name=".activities.SettingsActivity"\n            android:label="@string/title_settings"\n            android:exported="false"'); f.write_text(s)
shutil.copyfile('youtube-access/MainActivity.kt',APP/'src/main/java/io/github/romanvht/byedpi/activities/MainActivity.kt')
(ROOT/'YOUTUBE_ACCESS_SOURCE_INFO.txt').write_text('''YouTube Access v0.1.0\nBase: romanvht/ByeByeDPI v1.7.7 (GPL-3.0)\nNetwork engine: hufrea/byedpi + heiher/hev-socks5-tunnel.\nChanges: official YouTube-only VpnService routing, SmartConnect SOCKS probes, automatic strategy rotation, one-button UI, log export.\nThis patched tree is the Corresponding Source used for the APK.\n''')
print('YouTube Access patch applied')
