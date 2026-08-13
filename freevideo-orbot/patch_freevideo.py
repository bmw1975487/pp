from pathlib import Path
import shutil, sys

root = Path(sys.argv[1]).resolve()
repo = Path(sys.argv[2]).resolve()
app = root / 'app'
java_dir = app / 'src/main/java/org/torproject/android'

gradle = app / 'build.gradle.kts'
s = gradle.read_text(encoding='utf-8')
s = s.replace('val orbotBaseVersionCode = 207', 'val orbotBaseVersionCode = 300')
s = s.replace('applicationId = "com.bmw1975487.aione.routefix7"', 'applicationId = "com.bmw1975487.youtubeaccess"')
s = s.replace('versionName = "0.2.7-browseronly"', 'versionName = "0.3.0-orbot"')
s = s.replace('AI_Access_One_v0.2.7_BROWSER_ONLY', 'FreeVideo_v0.3.0_ORBOT')
gradle.write_text(s, encoding='utf-8')

# UI JPG files are injected into assets/freevideo after compilation, before zipalign/signing.
# Android assets are not part of resources.arsc, so this avoids any Gradle image transformation
# and lets the final APK carry the user's original JPG bytes exactly.
shutil.copyfile(repo / 'freevideo-orbot/FreeVideoActivity.java', java_dir / 'AiAccessActivity.java')

manifest = app / 'src/main/AndroidManifest.xml'
m = manifest.read_text(encoding='utf-8')
m = m.replace('<package android:name="com.openai.chatgpt" />', '<package android:name="com.google.android.youtube" />')
if '<package android:name="com.google.android.youtube" />' not in m:
    m = m.replace('<queries>', '<queries>\n        <package android:name="com.google.android.youtube" />', 1)
m = m.replace('android:label="AI Access One"', 'android:label="FreeVideo"')
manifest.write_text(m, encoding='utf-8')

logf = java_dir / 'AiAccessLog.java'
l = logf.read_text(encoding='utf-8')
l = l.replace('public static final String VERSION = "0.2.7-browseronly";', 'public static final String VERSION = "0.3.0-orbot";')
l = l.replace('private static final String TAG = "AI-ACCESS-ROUTE";', 'private static final String TAG = "FREEVIDEO-ROUTE";')
l = l.replace('private static final String FILE = "ai-access-route.log";', 'private static final String FILE = "freevideo-route.log";')
l = l.replace('AI_Access_One_Log_', 'FreeVideo_Log_')
l = l.replace('AI ACCESS ONE ROUTE LOG', 'FREEVIDEO ROUTE LOG')
l = l.replace('containsAny(line, "ROUTE_", "EXIT_", "OPENAI", "CHATGPT", "TRANSPORT_", "SMARTCONNECT", "BOOTSTRAP_")', 'containsAny(line, "ROUTE_", "EXIT_", "YOUTUBE", "GOOGLEVIDEO", "TRANSPORT_", "SMARTCONNECT", "BOOTSTRAP_")')
logf.write_text(l, encoding='utf-8')

dlf = java_dir / 'LogDownloads.java'
d = dlf.read_text(encoding='utf-8')
d = d.replace('Environment.DIRECTORY_DOWNLOADS + "/AI Access One"', 'Environment.DIRECTORY_DOWNLOADS + "/FreeVideo"')
d = d.replace('Download/AI Access One/', 'Download/FreeVideo/')
dlf.write_text(d, encoding='utf-8')

vpn = app / 'src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java'
v = vpn.read_text(encoding='utf-8')
if 'AiAccessPrefs.targetPackage()' not in v or 'builder.addAllowedApplication(targetPackage)' not in v:
    raise SystemExit('Strict dynamic app routing marker missing')

assert 'com.bmw1975487.youtubeaccess' in gradle.read_text(encoding='utf-8')
assert '0.3.0-orbot' in gradle.read_text(encoding='utf-8')
assert 'com.google.android.youtube' in manifest.read_text(encoding='utf-8')
a = (java_dir / 'AiAccessActivity.java').read_text(encoding='utf-8')
for marker in ['YOUTUBE_APP_LAUNCH', 'YOUTUBE_PROBE_START', 'FREEVIDEO_ACTIVITY_CREATE', 'com.google.android.youtube']:
    assert marker in a, marker
assert 'https://www.youtube.com' not in a

(root / 'FREEVIDEO_SOURCE_INFO.txt').write_text('''FreeVideo v0.3.0 ORBOT\nBase: guardianproject/orbot-android commit 521d18aa3cd320f5762a57e0bcf9fcf0c8eba15a\nWorking route overlay lineage: AI Access One v0.2.7 BrowserOnly commit 5f2188aaaee61a6201813cc26362e30570ad2cd1\nTransport: Orbot / Tor / SmartConnect / Snowflake + HEV tun2socks\nVPN allowed application: com.google.android.youtube only\nNo web YouTube fallback is present.\nUI: FreeVideo screen JPGs are injected byte-for-byte into assets/freevideo after compilation and before signing.\nLive route state and technical log are overlaid inside the supplied Settings design.\n''', encoding='utf-8')

print('FreeVideo v0.3.0 Orbot patch applied')
print('Package: com.bmw1975487.youtubeaccess')
print('Target only: com.google.android.youtube')
print('Web fallback: DISABLED')
print('JPG assets: post-build injection required')
