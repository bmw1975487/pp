from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# v0.2.5 identity: this installs separately from earlier test APKs.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 204', 'val orbotBaseVersionCode = 205')
s = s.replace('applicationId = "com.bmw1975487.aione.routefix4"', 'applicationId = "com.bmw1975487.aione.routefix5"')
s = s.replace('versionName = "0.2.4-bootstrapgate"', 'versionName = "0.2.5-noprofile"')
s = s.replace('AI_Access_One_v0.2.4_BOOTSTRAP_GATE', 'AI_Access_One_v0.2.5_NOPROFILE')
gradle.write_text(s, encoding="utf-8")

# Remove the UI/startup requirement that ChatGPT must be visible in a particular Android profile.
# Routing safety is enforced below at the VpnService.Builder layer instead.
activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")
gate_start = a.find('        try {\n            getPackageManager().getPackageInfo(CHATGPT, 0);\n            AiAccessLog.i(this, "CHATGPT_TARGET_CONFIRMED", CHATGPT);')
if gate_start >= 0:
    gate_end = a.find('        exitIndex = 0;', gate_start)
    if gate_end < 0:
        raise SystemExit("Could not find end of Android-profile gate")
    replacement = '        AiAccessLog.i(this, "CHATGPT_TARGET_MODE", "strict package routing; no Android profile requirement; target=" + CHATGPT);\n'
    a = a[:gate_start] + replacement + a[gate_end:]

# Remove any remaining user-facing wording that suggests moving/installing ChatGPT into another profile.
a = a.replace('Официальный ChatGPT не найден в этом профиле Android', 'Официальный ChatGPT пока не виден системе')
a = a.replace('Установите официальный ChatGPT в том же профиле Android.', 'Используется обычное установленное приложение ChatGPT.')
activity.write_text(a, encoding="utf-8")

# Keep ZIP/device version coherent.
log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8")
log_text = log_text.replace('0.2.4-bootstrapgate', '0.2.5-noprofile')
log_file.write_text(log_text, encoding="utf-8")

# Critical safety fix: upstream Orbot falls back to whole-device VPN when no individual
# app is resolved. AI Access One must NEVER do that. Hard-pin only the official ChatGPT
# package directly in VpnService.Builder. If Android truly cannot resolve that installed
# package, addAllowedApplication throws NameNotFoundException and the VPN is not established.
vpn = app / "src/main/java/org/torproject/android/service/vpn/OrbotVpnManager.java"
v = vpn.read_text(encoding="utf-8")
method_start = v.find('    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {')
if method_start < 0:
    raise SystemExit("OrbotVpnManager doAppBasedRouting method not found")
method_end = v.find('\n    /**\n     * @noinspection BooleanMethodIsAlwaysInverted', method_start)
if method_end < 0:
    raise SystemExit("OrbotVpnManager doAppBasedRouting end marker not found")
new_method = '''    private void doAppBasedRouting(VpnService.Builder builder) throws NameNotFoundException {
        final String targetPackage = "com.openai.chatgpt";
        builder.addAllowedApplication(targetPackage);
        Log.i(TAG, "AI_ACCESS_STRICT_ROUTE allowedApplication=" + targetPackage + " fullDeviceFallback=false");
    }
'''
v = v[:method_start] + new_method + v[method_end:]
vpn.write_text(v, encoding="utf-8")

print("AI Access v0.2.5 NoProfile patch applied")
print("ApplicationId: com.bmw1975487.aione.routefix5")
print("Target package: com.openai.chatgpt")
print("Android profile gate: REMOVED")
print("Whole-device fallback: DISABLED")
