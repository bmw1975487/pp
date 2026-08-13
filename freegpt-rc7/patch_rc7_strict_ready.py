from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC7 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 105' not in s:
    raise SystemExit('RC6 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 105', 'val orbotBaseVersionCode = 106', 1)
if 'versionName = "1.0.0-rc6-local-only"' not in s:
    raise SystemExit('RC6 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc6-local-only"', 'versionName = "1.0.0-rc7-strict-ready"', 1)
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Strict provider-ready state: Tor being up is NOT the same thing as the selected AI being ready.
field_marker = '    private boolean localTransportRestarting = false;'
fields = '''    private boolean localTransportRestarting = false;
    private boolean providerVerified = false;
    private boolean browserOpenIssued = false;
    private boolean browserOpenFailed = false;
    private long providerVerifiedAtMs = 0L;'''
if field_marker not in a:
    raise SystemExit('RC6 localTransportRestarting field marker not found')
a = a.replace(field_marker, fields, 1)

# Restore the selected/active service BEFORE drawing the screen, so Android activity recreation
# can never silently turn Grok/Claude/Gemini back into the ChatGPT default.
oncreate_marker = '''        initTrial();
        setContentView(buildUi());'''
oncreate_repl = '''        initTrial();
        restorePersistedServiceSession();
        setContentView(buildUi());'''
if oncreate_marker not in a:
    raise SystemExit('FreeGPT initTrial/setContentView marker not found')
a = a.replace(oncreate_marker, oncreate_repl, 1)

# Insert persistence helpers before trial initialization.
trial_marker = '    private void initTrial() {'
helpers = r'''    private void restorePersistedServiceSession() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        String savedName = p.getString("selected_service_name", "ChatGPT");
        String savedUrl = p.getString("selected_service_url", "https://chatgpt.com/");
        if (savedName == null || savedName.isEmpty()) savedName = "ChatGPT";
        if (savedUrl == null || savedUrl.isEmpty()) savedUrl = serviceUrlForName(savedName);
        selectedServiceName = savedName;
        selectedServiceUrl = savedUrl;

        providerVerified = p.getBoolean("provider_verified", false);
        browserOpenIssued = p.getBoolean("browser_open_issued", false);
        browserOpenFailed = p.getBoolean("browser_open_failed", false);
        providerVerifiedAtMs = p.getLong("provider_verified_at_ms", 0L);
        String active = p.getString("active_service_name", "");
        String activeUrl = p.getString("active_service_url", "");
        if (providerVerified && active != null && !active.isEmpty()) {
            activeServiceName = active;
            selectedServiceName = active;
            selectedServiceUrl = (activeUrl == null || activeUrl.isEmpty()) ? serviceUrlForName(active) : activeUrl;
        } else {
            activeServiceName = null;
            providerVerified = false;
        }
        AiAccessLog.i(this, "SERVICE_SESSION_RESTORE",
                "selected=" + selectedServiceName + " active=" + activeServiceName +
                        " verified=" + providerVerified + " browserIssued=" + browserOpenIssued);
    }

    private String serviceUrlForName(String name) {
        if ("Claude".equalsIgnoreCase(name)) return "https://claude.ai/";
        if ("Gemini".equalsIgnoreCase(name)) return "https://gemini.google.com/";
        if ("Grok".equalsIgnoreCase(name)) return "https://grok.com/";
        return "https://chatgpt.com/";
    }

    private void persistSelectedService() {
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putString("selected_service_name", selectedServiceName == null ? "ChatGPT" : selectedServiceName)
                .putString("selected_service_url", selectedServiceUrl == null ? "https://chatgpt.com/" : selectedServiceUrl)
                .apply();
    }

    private void clearVerifiedServiceSession(String reason) {
        providerVerified = false;
        browserOpenIssued = false;
        browserOpenFailed = false;
        providerVerifiedAtMs = 0L;
        activeServiceName = null;
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putBoolean("provider_verified", false)
                .putBoolean("browser_open_issued", false)
                .putBoolean("browser_open_failed", false)
                .putLong("provider_verified_at_ms", 0L)
                .remove("active_service_name")
                .remove("active_service_url")
                .apply();
        AiAccessLog.i(this, "VERIFIED_SERVICE_CLEAR", "reason=" + reason + " selected=" + selectedServiceName);
    }

    private void markProviderVerified(String service, String url) {
        providerVerified = true;
        browserOpenIssued = false;
        browserOpenFailed = false;
        providerVerifiedAtMs = System.currentTimeMillis();
        activeServiceName = service;
        selectedServiceName = service;
        selectedServiceUrl = (url == null || url.isEmpty()) ? serviceUrlForName(service) : url;
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putString("selected_service_name", selectedServiceName)
                .putString("selected_service_url", selectedServiceUrl)
                .putString("active_service_name", activeServiceName)
                .putString("active_service_url", selectedServiceUrl)
                .putBoolean("provider_verified", true)
                .putBoolean("browser_open_issued", false)
                .putBoolean("browser_open_failed", false)
                .putLong("provider_verified_at_ms", providerVerifiedAtMs)
                .apply();
        AiAccessLog.i(this, "PROVIDER_VERIFIED_STRICT",
                "service=" + service + " url=" + selectedServiceUrl + " percentAllowed=100");
    }

'''
if trial_marker not in a:
    raise SystemExit('initTrial marker not found')
a = a.replace(trial_marker, helpers + trial_marker, 1)

# Returning from Chrome may recreate the Activity. Tor STATUS_ON alone must never manufacture READY.
restore_old = '''                    if (statusRefreshRequested && "OFF".equals(state)) {
                        statusRefreshRequested = false;
                        torBootstrapped = true;
                        state = "READY";
                        detail = "Активный маршрут восстановлен";
                        AiAccessLog.i(AiAccessActivity.this, "ROUTE_STATE_RESTORED", "socks=" + socksPort + " exit=" + currentExit());
                        if (serviceProbePending && socksPort > 0) scheduleProbe(500);
                    } else {'''
restore_new = '''                    if (statusRefreshRequested && "OFF".equals(state)) {
                        statusRefreshRequested = false;
                        torBootstrapped = true;
                        routeProbeArmed = true;
                        if (providerVerified && activeServiceName != null && !activeServiceName.isEmpty()) {
                            state = "READY";
                            selectedServiceName = activeServiceName;
                            selectedServiceUrl = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                                    .getString("active_service_url", serviceUrlForName(activeServiceName));
                            detail = activeServiceName + " активен";
                            uxLastPercent = 100;
                            AiAccessLog.i(AiAccessActivity.this, "VERIFIED_SESSION_RESTORED",
                                    "service=" + activeServiceName + " socks=" + socksPort + " exit=" + currentExit());
                        } else {
                            // Network route exists, but the selected provider has not been proven.
                            state = "TOR_ON";
                            uxLastPercent = Math.min(99, Math.max(uxLastPercent, 88));
                            detail = "Проверяю выбранный сервис";
                            serviceProbePending = selectedServiceName != null && !selectedServiceName.isEmpty();
                            AiAccessLog.i(AiAccessActivity.this, "ROUTE_ONLY_RESTORED",
                                    "selected=" + selectedServiceName + " providerVerified=false socks=" + socksPort);
                            if (serviceProbePending && socksPort > 0) scheduleProbe(500);
                        }
                    } else {'''
if restore_old not in a:
    raise SystemExit('RC4 route state restore block not found')
a = a.replace(restore_old, restore_new, 1)

# Beginning a new provider selection invalidates any old 100% state and persists the exact service.
selection_marker = '''        selectedServiceName = name;
        selectedServiceUrl = url;
        activeServiceName = null;
        uxStartedAtMs = SystemClock.elapsedRealtime();'''
selection_repl = '''        selectedServiceName = name;
        selectedServiceUrl = url;
        clearVerifiedServiceSession("new_selection_" + name);
        persistSelectedService();
        uxStartedAtMs = SystemClock.elapsedRealtime();'''
if selection_marker not in a:
    raise SystemExit('RC5 service-selection state marker not found')
a = a.replace(selection_marker, selection_repl, 1)

# 100% is now a hard consequence of provider verification, never of Tor/READY alone.
progress_old = '''        if ("OFF".equals(state)) {
            uxLastPercent = 0;
            return 0;
        }
        if ("READY".equals(state)) {
            uxLastPercent = 100;
            return 100;
        }'''
progress_new = '''        if ("OFF".equals(state)) {
            uxLastPercent = 0;
            return 0;
        }
        if (providerVerified && activeServiceName != null && ("READY".equals(state) || browserOpenFailed)) {
            uxLastPercent = 100;
            return 100;
        }
        if ("READY".equals(state) && !providerVerified) {
            AiAccessLog.w(this, "FALSE_READY_BLOCKED", "selected=" + selectedServiceName + " state=READY providerVerified=false");
            state = "TOR_ON";
        }'''
if progress_old not in a:
    raise SystemExit('RC5 userProgressPercent READY block not found')
a = a.replace(progress_old, progress_new, 1)

# In-progress UI may reach 99%, but never 100 until SERVICE_ROUTE_ACCEPTED.
cap_old = '''        if (!"READY".equals(state)) next = Math.min(next, 96);'''
cap_new = '''        if (!providerVerified) next = Math.min(next, 99);'''
if cap_old not in a:
    raise SystemExit('RC5 progress cap marker not found')
a = a.replace(cap_old, cap_new, 1)

status_old = '''        if ("READY".equals(state)) return "Соединение готово";
        if ("ERROR".equals(state)) return "Не удалось завершить подключение";'''
status_new = '''        String service = activeServiceName != null ? activeServiceName : selectedServiceName;
        if (providerVerified && browserOpenFailed) return "Сервис готов · не удалось открыть браузер";
        if (providerVerified && "READY".equals(state)) {
            return browserOpenIssued ? service + " активен" : "Готово · открываю " + service;
        }
        if ("ERROR".equals(state)) return "Не удалось подключить " + service;'''
if status_old not in a:
    raise SystemExit('RC5 userStatusText READY/ERROR marker not found')
a = a.replace(status_old, status_new, 1)

# Provider probe success is the ONLY place that grants 100%/active status.
verified_old = '''                    state = "READY";
                    activeServiceName = service;
                    uxLastPercent = 100;
                    detail = service + " доступен · " + exit.toUpperCase() + " · HTTP " + page.httpCode;'''
verified_new = '''                    markProviderVerified(service, selectedServiceUrl);
                    state = "READY";
                    uxLastPercent = 100;
                    detail = service + " проверен · открываю";'''
if verified_old not in a:
    raise SystemExit('RC5 provider success marker not found')
a = a.replace(verified_old, verified_new, 1)

# Strict browser launch: correct persisted service only, and only after that service passed its own probe.
launch_start = a.find('    private void launchChatGpt() {')
launch_end = a.find('    private Uri createZipUri(', launch_start)
if launch_start < 0 or launch_end < 0:
    raise SystemExit('launchChatGpt block markers not found')
new_launch = r'''    private void launchChatGpt() {
        String service = activeServiceName != null ? activeServiceName : selectedServiceName;
        if (!providerVerified || activeServiceName == null || activeServiceName.isEmpty()) {
            AiAccessLog.w(this, "AI_WEB_OPEN_BLOCKED",
                    "service=" + service + " reason=provider_not_verified state=" + state);
            if ("READY".equals(state)) state = "TOR_ON";
            uxLastPercent = Math.min(99, Math.max(uxLastPercent, 90));
            detail = "Проверяю " + service;
            render();
            return;
        }
        try {
            if (targetPackage == null || targetPackage.isEmpty()) {
                browserOpenFailed = true;
                state = "ERROR";
                detail = "Не удалось открыть " + activeServiceName;
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("browser_open_failed", true).apply();
                AiAccessLog.w(this, "AI_WEB_OPEN_FAIL", "browser target is empty service=" + activeServiceName);
                render();
                return;
            }
            String url = selectedServiceUrl == null || selectedServiceUrl.isEmpty()
                    ? serviceUrlForName(activeServiceName) : selectedServiceUrl;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            open.addCategory(Intent.CATEGORY_BROWSABLE);
            open.setPackage(targetPackage);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (open.resolveActivity(getPackageManager()) == null) {
                browserOpenFailed = true;
                state = "ERROR";
                detail = "Не удалось открыть " + activeServiceName;
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("browser_open_failed", true).apply();
                AiAccessLog.w(this, "AI_WEB_OPEN_FAIL",
                        "browser cannot handle URL package=" + targetPackage + " service=" + activeServiceName + " url=" + url);
                render();
                return;
            }
            browserOpenIssued = true;
            browserOpenFailed = false;
            getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                    .putBoolean("browser_open_issued", true)
                    .putBoolean("browser_open_failed", false)
                    .putString("active_service_name", activeServiceName)
                    .putString("active_service_url", url)
                    .apply();
            detail = activeServiceName + " активен";
            AiAccessLog.i(this, "AI_WEB_OPEN",
                    "service=" + activeServiceName + " url=" + url + " browserPackage=" + targetPackage + " strictVerified=true");
            render();
            startActivity(open);
        } catch (Throwable t) {
            browserOpenFailed = true;
            state = "ERROR";
            detail = "Не удалось открыть " + activeServiceName;
            getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("browser_open_failed", true).apply();
            AiAccessLog.e(this, "AI_WEB_OPEN_FAIL", String.valueOf(t.getMessage()), t);
            render();
        }
    }

'''
a = a[:launch_start] + new_launch + a[launch_end:]

# READY action means disconnect only for a VERIFIED provider. Browser-open failures get a clear retry action.
ready_branch_old = '''        if ("READY".equals(state)) {
            liveStatusTitle.setTextColor(GREEN);
            liveStatusPercent.setTextColor(GREEN);
            liveStatusAction.setText("ОТКЛЮЧИТЬ");
            liveStatusAction.setOnClickListener(v -> stopRoute());
        } else if ("ERROR".equals(state)) {'''
ready_branch_new = '''        if (providerVerified && "READY".equals(state)) {
            liveStatusTitle.setTextColor(GREEN);
            liveStatusPercent.setTextColor(GREEN);
            liveStatusAction.setText("ОТКЛЮЧИТЬ");
            liveStatusAction.setOnClickListener(v -> stopRoute());
        } else if (providerVerified && browserOpenFailed && "ERROR".equals(state)) {
            liveStatusTitle.setTextColor(AMBER);
            liveStatusPercent.setTextColor(GREEN);
            liveStatusAction.setText("ОТКРЫТЬ СНОВА");
            liveStatusAction.setOnClickListener(v -> {
                state = "READY";
                browserOpenFailed = false;
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("browser_open_failed", false).apply();
                launchChatGpt();
            });
        } else if ("ERROR".equals(state)) {'''
if ready_branch_old not in a:
    raise SystemExit('RC5 renderLiveStatus READY branch not found')
a = a.replace(ready_branch_old, ready_branch_new, 1)

# Explicit user disconnect clears only the verified session. Keep the last selected service for convenience.
stop_marker = '''        requestedLocalTransport = "";
        torBootstrapped = false;'''
stop_repl = '''        requestedLocalTransport = "";
        clearVerifiedServiceSession("user_disconnect");
        torBootstrapped = false;'''
if stop_marker not in a:
    raise SystemExit('RC6 stop marker not found')
a = a.replace(stop_marker, stop_repl, 1)

activity.write_text(a, encoding="utf-8")

# Coherent diagnostic version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc6-local-only' not in lt:
    raise SystemExit('RC6 log version marker not found')
lt = lt.replace('1.0.0-rc6-local-only', '1.0.0-rc7-strict-ready')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC7 STRICT READY patch applied')
print('versionName=1.0.0-rc7-strict-ready')
print('100 percent=ONLY after selected provider SERVICE_ROUTE_ACCEPTED')
print('browser=open automatically after provider verification')
print('session=selected/active service persisted across Activity recreation')
print('servers=NONE local-only preserved')
