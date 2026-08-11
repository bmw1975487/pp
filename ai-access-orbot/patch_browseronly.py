from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# v0.2.7 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 206', 'val orbotBaseVersionCode = 207')
s = s.replace('applicationId = "com.bmw1975487.aione.routefix6"', 'applicationId = "com.bmw1975487.aione.routefix7"')
s = s.replace('versionName = "0.2.6-autotarget"', 'versionName = "0.2.7-browseronly"')
s = s.replace('AI_Access_One_v0.2.6_AUTOTARGET', 'AI_Access_One_v0.2.7_BROWSER_ONLY')
gradle.write_text(s, encoding="utf-8")

# Allow Android package visibility for HTTPS browser handlers only. No ChatGPT app scan.
manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
https_query = '''        <intent>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="https" />
        </intent>
'''
if https_query not in m:
    q = m.find('<queries>')
    if q < 0:
        raise SystemExit('Manifest <queries> not found')
    qend = q + len('<queries>')
    m = m[:qend] + '\n' + https_query + m[qend:]
manifest.write_text(m, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# The project no longer targets an installed ChatGPT app. It targets the browser that handles chatgpt.com.
a = a.replace('ChatGPT · автоматический маршрут', 'ChatGPT · официальный сайт через браузер')
a = a.replace('VPN направляет только официальное приложение ChatGPT.', 'VPN направляет только браузер, которым открывается официальный сайт ChatGPT.')
a = a.replace('ВКЛЮЧИТЬ CHATGPT', 'ОТКРЫТЬ CHATGPT')

# Replace ChatGPT app discovery with browser discovery for the official site.
method_start = a.find('    private void checkChatGptInstalled() {')
method_end = a.find('    private View buildUi() {', method_start)
if method_start < 0 or method_end < 0:
    raise SystemExit('ChatGPT target discovery block not found')
new_detect = '''    private void checkChatGptInstalled() {
        String detected = discoverBrowserTarget();
        if (detected != null) {
            targetPackage = detected;
            AiAccessLog.i(this, "BROWSER_TARGET_READY", "package=" + targetPackage + " label=" + targetLabel + " url=https://chatgpt.com/");
        } else {
            targetPackage = null;
            targetLabel = "";
            AiAccessLog.w(this, "BROWSER_TARGET_NOT_FOUND", "no HTTPS browser handler for https://chatgpt.com/");
        }
    }

    private String discoverBrowserTarget() {
        PackageManager pm = getPackageManager();
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"));
        view.addCategory(Intent.CATEGORY_BROWSABLE);

        try {
            ResolveInfo resolved = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null) {
                String pkg = resolved.activityInfo.packageName;
                if (pkg != null && !pkg.isEmpty() && !"android".equals(pkg) && !pkg.equals(getPackageName())) {
                    CharSequence cs = resolved.loadLabel(pm);
                    targetLabel = cs == null ? pkg : cs.toString();
                    AiAccessLog.i(this, "BROWSER_TARGET_DEFAULT", "package=" + pkg + " label=" + targetLabel);
                    return pkg;
                }
            }
        } catch (Throwable t) {
            AiAccessLog.e(this, "BROWSER_DEFAULT_RESOLVE_FAIL", String.valueOf(t.getMessage()), t);
        }

        try {
            List<ResolveInfo> list = pm.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY);
            ResolveInfo best = null;
            int bestScore = -1;
            for (ResolveInfo ri : list) {
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName()) || "android".equals(pkg)) continue;
                CharSequence cs = ri.loadLabel(pm);
                String label = cs == null ? "" : cs.toString().trim();
                String pp = pkg.toLowerCase(Locale.ROOT);
                String ll = label.toLowerCase(Locale.ROOT);
                int score = 1;
                if (pp.contains("chrome")) score += 50;
                if (pp.contains("sbrowser")) score += 48;
                if (pp.contains("firefox")) score += 46;
                if (pp.contains("browser")) score += 40;
                if (pp.contains("brave")) score += 38;
                if (pp.contains("opera")) score += 36;
                if (pp.contains("yandex")) score += 34;
                if (pp.contains("emm") || ll.contains("edge")) score += 32;
                AiAccessLog.i(this, "BROWSER_CANDIDATE", "package=" + pkg + " label=" + label + " score=" + score);
                if (score > bestScore) {
                    best = ri;
                    bestScore = score;
                }
            }
            if (best != null) {
                String pkg = best.activityInfo.packageName;
                CharSequence cs = best.loadLabel(pm);
                targetLabel = cs == null ? pkg : cs.toString();
                AiAccessLog.i(this, "BROWSER_TARGET_FALLBACK", "package=" + pkg + " label=" + targetLabel + " score=" + bestScore);
                return pkg;
            }
        } catch (Throwable t) {
            AiAccessLog.e(this, "BROWSER_DISCOVERY_FAIL", String.valueOf(t.getMessage()), t);
        }
        return null;
    }

'''
a = a[:method_start] + new_detect + a[method_end:]

# Power action: resolve browser, never look for an installed ChatGPT client.
a = a.replace('String detectedTarget = discoverChatGptTarget();', 'String detectedTarget = discoverBrowserTarget();')
a = a.replace('detail = "Клиент ChatGPT не найден среди установленных приложений";', 'detail = "Не найден браузер для открытия chatgpt.com";')
a = a.replace('AiAccessLog.w(this, "CHATGPT_TARGET_MISSING", "official=" + CHATGPT + " launcherCandidates=none");', 'AiAccessLog.w(this, "BROWSER_TARGET_MISSING", "url=https://chatgpt.com/ httpsHandlers=none");')
a = a.replace('Toast.makeText(this, "Клиент ChatGPT не найден на телефоне", Toast.LENGTH_LONG).show();', 'Toast.makeText(this, "Не найден браузер для открытия ChatGPT", Toast.LENGTH_LONG).show();')
a = a.replace('AiAccessLog.i(this, "CHATGPT_TARGET_MODE", "strict package routing; target=" + targetPackage + " label=" + targetLabel);', 'AiAccessLog.i(this, "BROWSER_TARGET_MODE", "strict browser routing; target=" + targetPackage + " label=" + targetLabel + " url=https://chatgpt.com/");')

# Route logs should describe the actual target accurately.
a = a.replace('"allowedApp=" + targetPackage + " smartConnect="', '"allowedBrowser=" + targetPackage + " smartConnect="')

# After a successful Tor/OpenAI route, open the official ChatGPT website in exactly the browser placed in VPN.
launch_start = a.find('    private void launchChatGpt() {')
launch_end = a.find('    private Uri createZipUri(', launch_start)
if launch_start < 0 or launch_end < 0:
    raise SystemExit('launchChatGpt block not found')
new_launch = '''    private void launchChatGpt() {
        try {
            if (targetPackage == null || targetPackage.isEmpty()) {
                AiAccessLog.w(this, "CHATGPT_WEB_OPEN_FAIL", "browser target is empty");
                return;
            }
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"));
            open.addCategory(Intent.CATEGORY_BROWSABLE);
            open.setPackage(targetPackage);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (open.resolveActivity(getPackageManager()) == null) {
                AiAccessLog.w(this, "CHATGPT_WEB_OPEN_FAIL", "browser cannot handle URL package=" + targetPackage);
                return;
            }
            AiAccessLog.i(this, "CHATGPT_WEB_OPEN", "url=https://chatgpt.com/ browserPackage=" + targetPackage + " label=" + targetLabel);
            startActivity(open);
        } catch (Throwable t) {
            AiAccessLog.e(this, "CHATGPT_WEB_OPEN_FAIL", String.valueOf(t.getMessage()), t);
        }
    }

'''
a = a[:launch_start] + new_launch + a[launch_end:]

# User-facing ready state is the website, not an Android ChatGPT app.
a = a.replace('stateView.setText("●  CHATGPT ГОТОВ")', 'stateView.setText("●  CHATGPT WEB ГОТОВ")')
a = a.replace('powerButton.setText("ВКЛЮЧИТЬ CHATGPT")', 'powerButton.setText("ОТКРЫТЬ CHATGPT")')
activity.write_text(a, encoding="utf-8")

# Coherent ZIP/device version.
log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8").replace('0.2.6-autotarget', '0.2.7-browseronly')
log_file.write_text(log_text, encoding="utf-8")

print('AI Access v0.2.7 BrowserOnly patch applied')
print('ApplicationId: com.bmw1975487.aione.routefix7')
print('Target: browser resolving https://chatgpt.com/')
print('Installed ChatGPT app discovery: DISABLED')
print('Whole-device fallback: DISABLED')
