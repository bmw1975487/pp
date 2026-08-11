from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# v0.2.6 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 205', 'val orbotBaseVersionCode = 206')
s = s.replace('applicationId = "com.bmw1975487.aione.routefix5"', 'applicationId = "com.bmw1975487.aione.routefix6"')
s = s.replace('versionName = "0.2.5-noprofile"', 'versionName = "0.2.6-autotarget"')
s = s.replace('AI_Access_One_v0.2.5_NOPROFILE', 'AI_Access_One_v0.2.6_AUTOTARGET')
gradle.write_text(s, encoding="utf-8")

# Make launcher apps queryable so we can discover a ChatGPT WebAPK/repackaged launcher
# without QUERY_ALL_PACKAGES. Exact official com.openai.chatgpt remains first choice.
manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
launcher_query = '''        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
'''
if launcher_query not in m:
    q = m.find('<queries>')
    if q < 0:
        raise SystemExit('Manifest <queries> not found')
    qend = q + len('<queries>')
    m = m[:qend] + '\n' + launcher_query + m[qend:]
manifest.write_text(m, encoding="utf-8")

# Dynamic VPN target preference.
prefs = java_dir / "AiAccessPrefs.kt"
p = prefs.read_text(encoding="utf-8")
old_sig = 'fun configure(context: Context, exitCountry: String = "nl") {'
if old_sig not in p:
    raise SystemExit('AiAccessPrefs configure signature not found')
p = p.replace(old_sig, 'fun configure(context: Context, targetPackage: String, exitCountry: String = "nl") {', 1)
p = p.replace('Prefs.torifiedApps = CHATGPT_PACKAGE', 'Prefs.torifiedApps = targetPackage', 1)
insert_before = '    @JvmStatic\n    fun selectedTransport(): String = Prefs.transport.id\n'
if insert_before not in p:
    raise SystemExit('AiAccessPrefs selectedTransport marker not found')
p = p.replace(insert_before, '    @JvmStatic\n    fun targetPackage(): String = Prefs.torifiedApps\n\n' + insert_before, 1)
prefs.write_text(p, encoding="utf-8")

# Launcher: discover actual ChatGPT launcher package, use it for VPN + launch,
# and prevent duplicate START sequences from transient OFF broadcasts / double taps.
activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")
a = a.replace('import android.content.IntentFilter;', 'import android.content.IntentFilter;\nimport android.content.pm.PackageManager;\nimport android.content.pm.ResolveInfo;')
a = a.replace('import android.os.Looper;', 'import android.os.Looper;\nimport android.os.SystemClock;')
a = a.replace('import java.io.File;', 'import java.io.File;\nimport java.util.List;\nimport java.util.Locale;')
a = a.replace('private int probeGeneration = 0;', 'private int probeGeneration = 0;\n    private String targetPackage = null;\n    private String targetLabel = "";\n    private long lastPowerTapMs = 0L;\n    private boolean stopRequested = false;', 1)

# Replace package check with strict, limited launcher autodiscovery.
method_start = a.find('    private void checkChatGptInstalled() {')
method_end = a.find('    private View buildUi() {', method_start)
if method_start < 0 or method_end < 0:
    raise SystemExit('checkChatGptInstalled block not found')
new_detect = '''    private void checkChatGptInstalled() {
        String detected = discoverChatGptTarget();
        if (detected != null) {
            targetPackage = detected;
            AiAccessLog.i(this, "CHATGPT_TARGET_READY", "package=" + targetPackage + " label=" + targetLabel);
        } else {
            targetPackage = null;
            targetLabel = "";
            AiAccessLog.w(this, "CHATGPT_PACKAGE_NOT_FOUND", "official package and ChatGPT launcher candidates absent");
        }
    }

    private String discoverChatGptTarget() {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(CHATGPT, 0);
            targetLabel = "ChatGPT";
            AiAccessLog.i(this, "CHATGPT_TARGET_EXACT", CHATGPT);
            return CHATGPT;
        } catch (Throwable ignored) {
            AiAccessLog.i(this, "CHATGPT_TARGET_EXACT_MISSING", CHATGPT);
        }

        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(launcher, 0);
            ResolveInfo best = null;
            int bestScore = 0;
            for (ResolveInfo ri : list) {
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName())) continue;
                CharSequence cs = ri.loadLabel(pm);
                String label = cs == null ? "" : cs.toString().trim();
                String ll = label.toLowerCase(Locale.ROOT);
                String pp = pkg.toLowerCase(Locale.ROOT);
                int score = 0;
                if ("chatgpt".equals(ll)) score += 100;
                else if (ll.contains("chatgpt")) score += 80;
                if (ll.contains("openai")) score += 45;
                if (pp.contains("openai")) score += 70;
                if (pp.startsWith("org.chromium.webapk.") && "chatgpt".equals(ll)) score += 60;
                if (score >= 70) {
                    AiAccessLog.i(this, "CHATGPT_CANDIDATE", "package=" + pkg + " label=" + label + " score=" + score);
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = ri;
                }
            }
            if (best != null && bestScore >= 70) {
                String pkg = best.activityInfo.packageName;
                CharSequence cs = best.loadLabel(pm);
                targetLabel = cs == null ? "ChatGPT" : cs.toString();
                AiAccessLog.i(this, "CHATGPT_TARGET_DISCOVERED", "package=" + pkg + " label=" + targetLabel + " score=" + bestScore);
                return pkg;
            }
        } catch (Throwable t) {
            AiAccessLog.e(this, "CHATGPT_DISCOVERY_FAIL", String.valueOf(t.getMessage()), t);
        }
        return null;
    }

'''
a = a[:method_start] + new_detect + a[method_end:]

# Debounce rapid taps before any state mutation.
power_start = a.find('    private void onPower() {')
if power_start < 0:
    raise SystemExit('onPower not found')
needle = '        AiAccessLog.i(this, "POWER_BUTTON", "state=" + state);\n'
idx = a.find(needle, power_start)
if idx < 0:
    raise SystemExit('POWER_BUTTON marker not found')
debounce = '''        long now = SystemClock.elapsedRealtime();
        if (now - lastPowerTapMs < 1800L) {
            AiAccessLog.w(this, "POWER_DEBOUNCED", "state=" + state + " deltaMs=" + (now - lastPowerTapMs));
            return;
        }
        lastPowerTapMs = now;
'''
a = a[:idx + len(needle)] + debounce + a[idx + len(needle):]

# Replace NoProfile hardcoded target-mode line with autodetection and a truthful no-client error.
old_mode = '        AiAccessLog.i(this, "CHATGPT_TARGET_MODE", "strict package routing; no Android profile requirement; target=" + CHATGPT);\n'
new_mode = '''        String detectedTarget = discoverChatGptTarget();
        if (detectedTarget == null) {
            state = "ERROR";
            detail = "Клиент ChatGPT не найден среди установленных приложений";
            AiAccessLog.w(this, "CHATGPT_TARGET_MISSING", "official=" + CHATGPT + " launcherCandidates=none");
            Toast.makeText(this, "Клиент ChatGPT не найден на телефоне", Toast.LENGTH_LONG).show();
            render();
            return;
        }
        targetPackage = detectedTarget;
        AiAccessLog.i(this, "CHATGPT_TARGET_MODE", "strict package routing; target=" + targetPackage + " label=" + targetLabel);
'''
if old_mode not in a:
    raise SystemExit('NoProfile CHATGPT_TARGET_MODE line not found')
a = a.replace(old_mode, new_mode, 1)

# Pass the discovered target through prefs and logs.
a = a.replace('AiAccessPrefs.configure(this, currentExit());', 'AiAccessPrefs.configure(this, targetPackage, currentExit());')
a = a.replace('"allowedApp=" + CHATGPT + " smartConnect="', '"allowedApp=" + targetPackage + " smartConnect="')

# Launch the discovered actual package.
a = a.replace('getPackageManager().getLaunchIntentForPackage(CHATGPT)', 'getPackageManager().getLaunchIntentForPackage(targetPackage)')
a = a.replace('AiAccessLog.i(this, "CHATGPT_LAUNCH", CHATGPT);', 'AiAccessLog.i(this, "CHATGPT_LAUNCH", "package=" + targetPackage + " label=" + targetLabel);')

# START/STOP state hygiene: transient OFF during startup must not invite a second START.
a = a.replace('            torBootstrapped = false;\n            routeProbeArmed = false;\n            state = "CONNECTING";', '            stopRequested = false;\n            torBootstrapped = false;\n            routeProbeArmed = false;\n            state = "CONNECTING";', 1)
a = a.replace('    private void stopRoute() {\n        probeGeneration++;', '    private void stopRoute() {\n        stopRequested = true;\n        probeGeneration++;', 1)
old_off = '''                } else if (TorService.STATUS_OFF.equals(status)) {
                    if (!"READY".equals(state) && !"ERROR".equals(state)) {
                        state = "OFF";
                        detail = "Tor остановлен";
                    }
                }
'''
new_off = '''                } else if (TorService.STATUS_OFF.equals(status)) {
                    if (stopRequested) {
                        stopRequested = false;
                        state = "OFF";
                        detail = "Tor остановлен";
                    } else if ("CONNECTING".equals(state) || "PREPARING".equals(state) || "SERVICE_STARTED".equals(state)) {
                        AiAccessLog.i(AiAccessActivity.this, "TOR_STATUS_OFF_IGNORED", "startup in progress");
                    } else if (!"READY".equals(state) && !"ERROR".equals(state)) {
                        state = "OFF";
                        detail = "Tor остановлен";
                    }
                }
'''
if old_off not in a:
    raise SystemExit('TOR_STATUS_OFF block not found')
a = a.replace(old_off, new_off, 1)
activity.write_text(a, encoding="utf-8")

# Dynamic strict target at VPN builder level; NEVER whole-device fallback.
vpn = app / "src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java"
v = vpn.read_text(encoding="utf-8")
if 'import org.torproject.android.AiAccessPrefs;' not in v:
    v = v.replace('import org.torproject.android.service.Notifications;', 'import org.torproject.android.AiAccessPrefs;\nimport org.torproject.android.service.Notifications;', 1)
old_method = '''    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {
        final String targetPackage = "com.openai.chatgpt";
        builder.addAllowedApplication(targetPackage);
        Log.i(TAG, "AI_ACCESS_STRICT_ROUTE allowedApplication=" + targetPackage + " fullDeviceFallback=false");
    }
'''
new_method = '''    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {
        final String targetPackage = AiAccessPrefs.targetPackage();
        if (targetPackage == null || targetPackage.isBlank()) {
            throw new NameNotFoundException("AI Access target package is empty");
        }
        builder.addAllowedApplication(targetPackage);
        Log.i(TAG, "AI_ACCESS_STRICT_ROUTE allowedApplication=" + targetPackage + " fullDeviceFallback=false dynamicTarget=true");
    }
'''
if old_method not in v:
    raise SystemExit('v0.2.5 strict route method not found')
v = v.replace(old_method, new_method, 1)
vpn.write_text(v, encoding="utf-8")

# Coherent ZIP/device version.
log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8").replace('0.2.5-noprofile', '0.2.6-autotarget')
log_file.write_text(log_text, encoding="utf-8")

print('AI Access v0.2.6 AutoTarget patch applied')
print('ApplicationId: com.bmw1975487.aione.routefix6')
print('Target: exact official package first; otherwise limited launcher autodetection')
print('Whole-device fallback: DISABLED')
print('Transient startup OFF: ignored')
print('Power tap debounce: 1800ms')
