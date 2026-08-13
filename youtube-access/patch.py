from pathlib import Path
import base64, re, shutil

ROOT = Path("ByeByeDPI")
APP = ROOT / "app"

g = APP / "build.gradle.kts"
s = g.read_text()
s = s.replace('applicationId = "io.github.romanvht.byedpi"', 'applicationId = "com.bmw1975487.youtubeaccess"')
s = s.replace("versionCode = 1770", "versionCode = 103")
s = s.replace('versionName = "1.7.7"', 'versionName = "0.2.2"')
s = s.replace("compileSdk = 36", 'compileSdk = 36\n    ndkVersion = "27.2.12479018"')
s = s.replace("isMinifyEnabled = true", "isMinifyEnabled = false")
s = s.replace("isShrinkResources = true", "isShrinkResources = false")
g.write_text(s)

for f in APP.glob("src/main/res/values*/strings.xml"):
    s = f.read_text()
    s = re.sub(r'<string name="app_name">.*?</string>', '<string name="app_name">FreeVideo</string>', s)
    s = re.sub(r'<string name="notification_title">.*?</string>', '<string name="notification_title">FreeVideo</string>', s)
    s = re.sub(r'<string name="vpn_notification_content">.*?</string>', '<string name="vpn_notification_content">FreeVideo — видеомаршрут активен</string>', s)
    f.write_text(s)

# Safe compile-time placeholder. Final packaging replaces this drawable with
# an optimized copy of the user's FreeVideo icon before zipalign/signing.
icon_b64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5UooooA//2Q=="
icon = APP / "src/main/res/drawable/freevideo_icon_jpg.jpg"
icon.write_bytes(base64.b64decode(icon_b64))

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
s = s.replace('android:logo="@mipmap/ic_launcher"', 'android:logo="@drawable/freevideo_icon_jpg"')
s = s.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/freevideo_icon_jpg"')
s = s.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/freevideo_icon_jpg"')
s = s.replace('android:name=".activities.SettingsActivity"\n            android:label="@string/title_settings"\n            android:exported="true"', 'android:name=".activities.SettingsActivity"\n            android:label="@string/title_settings"\n            android:exported="false"')
f.write_text(s)

shutil.copyfile("youtube-access/MainActivity.kt", APP / "src/main/java/io/github/romanvht/byedpi/activities/MainActivity.kt")

(ROOT / "FREEVIDEO_SOURCE_INFO.txt").write_text('''FreeVideo v0.2.2 SAFE START\nBase: romanvht/ByeByeDPI v1.7.7 (GPL-3.0)\nNetwork engine: hufrea/byedpi + heiher/hev-socks5-tunnel.\n\nCritical startup changes:\n- onCreate has no VPN, no DNS setup, no preference writes, no file writes and no receiver registration.\n- first frame is the supplied 02_FreeVideo_start.jpg only.\n- all network initialization is lazy after OPEN.\n- every network operation is wrapped so it cannot crash/block the UI.\n- Android VPN permission denial does not block opening official YouTube.\n- official YouTube startActivity is independent of route health.\n- supplied JPG screens are injected as APK assets before signing.\n''')
print("FreeVideo v0.2.2 SAFE START patch applied")
