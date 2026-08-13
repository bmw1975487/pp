from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC6 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 104' not in s:
    raise SystemExit('RC5 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 104', 'val orbotBaseVersionCode = 105', 1)
if 'versionName = "1.0.0-rc5-compact-session"' not in s:
    raise SystemExit('RC5 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc5-compact-session"', 'versionName = "1.0.0-rc6-local-only"', 1)
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Local-only provider transport state. No private servers/endpoints are used.
field_marker = '    private String activeServiceName = null;'
fields = '''    private String activeServiceName = null;
    private String[] serviceTransportCandidates = {"snowflake", "snowflake_amp", "webtunnel", "obfs4", "meek", "dnstt", "direct"};
    private int serviceTransportAttempt = 0;
    private String requestedLocalTransport = "";
    private boolean localTransportRestarting = false;'''
if field_marker not in a:
    raise SystemExit('RC5 activeServiceName marker not found')
a = a.replace(field_marker, fields, 1)

# Service selection gets a per-service local transport plan and a hint for SmartConnect.
select_marker = '''        serviceExitCandidates = buildServiceExitCandidates(name, currentExit());
        serviceExitAttempt = 0;
        serviceProbePending = true;'''
select_repl = '''        serviceExitCandidates = buildServiceExitCandidates(name, currentExit());
        serviceExitAttempt = 0;
        serviceTransportCandidates = buildServiceTransportCandidates(name);
        serviceTransportAttempt = 0;
        requestedLocalTransport = serviceTransportCandidates.length > 0 ? serviceTransportCandidates[0] : "snowflake";
        setLocalTransportHint(requestedLocalTransport);
        serviceProbePending = true;'''
if select_marker not in a:
    raise SystemExit('RC5 service selection route marker not found')
a = a.replace(select_marker, select_repl, 1)

# Add local-only transport helpers before the existing service exit builder.
helper_marker = '    private String[] buildServiceExitCandidates(String service, String current) {'
helpers = r'''    private String[] buildServiceTransportCandidates(String service) {
        java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();
        String remembered = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getString("last_transport_" + serviceKey(service), "");
        if (remembered != null && !remembered.isEmpty()) order.add(remembered);

        // Public/local-only transports already bundled by Orbot. No private endpoint required.
        order.add("snowflake");
        order.add("snowflake_amp");
        order.add("webtunnel");
        order.add("obfs4");
        order.add("meek");
        order.add("dnstt");
        order.add("direct");
        return order.toArray(new String[0]);
    }

    private void setLocalTransportHint(String transport) {
        requestedLocalTransport = transport == null ? "" : transport;
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putString("next_transport_hint", requestedLocalTransport)
                .putString("next_transport_service", selectedServiceName == null ? "" : selectedServiceName)
                .apply();
        AiAccessLog.i(this, "LOCAL_TRANSPORT_HINT",
                "service=" + selectedServiceName + " transport=" + requestedLocalTransport + " localOnly=true");
    }

    private void rememberSuccessfulLocalRoute(String service, String exit) {
        String actualTransport;
        try { actualTransport = AiAccessPrefs.selectedTransport(); }
        catch (Throwable ignored) { actualTransport = requestedLocalTransport; }
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putString("last_transport_" + serviceKey(service), actualTransport == null ? "" : actualTransport)
                .putString("last_exit_" + serviceKey(service), exit == null ? "" : exit)
                .apply();
        AiAccessLog.i(this, "LOCAL_ROUTE_REMEMBERED",
                "service=" + service + " transport=" + actualTransport + " exit=" + exit + " localOnly=true");
    }

    private void restartForNextLocalTransport(SocksProbe.Result lastResult) {
        serviceTransportAttempt++;
        if (serviceTransportCandidates == null || serviceTransportAttempt >= serviceTransportCandidates.length) {
            state = "ERROR";
            detail = selectedServiceName + " не удалось подключить";
            AiAccessLog.w(this, "LOCAL_TRANSPORT_POOL_EXHAUSTED",
                    "service=" + selectedServiceName + " attempts=" + serviceTransportAttempt +
                            " last=" + (lastResult == null ? "none" : lastResult.summary()));
            render();
            return;
        }

        String nextTransport = serviceTransportCandidates[serviceTransportAttempt];
        setLocalTransportHint(nextTransport);
        localTransportRestarting = true;
        serviceProbePending = true;
        serviceExitCandidates = buildServiceExitCandidates(selectedServiceName, "nl");
        serviceExitAttempt = 0;
        setCurrentExit(serviceExitCandidates.length > 0 ? serviceExitCandidates[0] : "nl");
        probeGeneration++;
        probing = false;
        torBootstrapped = false;
        routeProbeArmed = false;
        socksPort = -1;
        liveBootstrapPercent = 0;
        state = "CONNECTING";
        detail = "Подбираю другой способ подключения";
        AiAccessLog.i(this, "LOCAL_TRANSPORT_SWITCH",
                "service=" + selectedServiceName + " transport=" + nextTransport +
                        " attempt=" + (serviceTransportAttempt + 1) + "/" + serviceTransportCandidates.length);
        render();

        try {
            Intent stop = new Intent(this, OrbotService.class)
                    .setAction(TorService.ACTION_STOP)
                    .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(stop); else startService(stop);
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOCAL_TRANSPORT_STOP_FAIL", String.valueOf(t.getMessage()), t);
        }

        final String serviceSnapshot = selectedServiceName;
        main.postDelayed(() -> {
            if (serviceSnapshot == null || !serviceSnapshot.equals(selectedServiceName) || !serviceProbePending) return;
            localTransportRestarting = false;
            state = "OFF";
            AiAccessLog.i(this, "LOCAL_TRANSPORT_RESTART", "service=" + serviceSnapshot + " transport=" + requestedLocalTransport);
            onPower();
        }, 1800L);
    }

'''
if helper_marker not in a:
    raise SystemExit('service exit builder marker not found')
a = a.replace(helper_marker, helpers + helper_marker, 1)

# Save the transport that really succeeded for this service.
remember_marker = '''                    getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                            .putString("last_exit_" + serviceKey(service), exit).apply();
                    AiAccessLog.i(AiAccessActivity.this, "SERVICE_ROUTE_ACCEPTED",'''
remember_repl = '''                    rememberSuccessfulLocalRoute(service, exit);
                    AiAccessLog.i(AiAccessActivity.this, "SERVICE_ROUTE_ACCEPTED",'''
if remember_marker not in a:
    raise SystemExit('RC4/5 successful exit memory marker not found')
a = a.replace(remember_marker, remember_repl, 1)

# When every country fails on the current transport, rotate the local transport instead of stopping.
exhaust_old = '''        if (serviceExitAttempt >= serviceExitCandidates.length) {
            state = "ERROR";
            detail = selectedServiceName + " не открылся через доступные маршруты · последний " + result.summary();
            AiAccessLog.w(this, "SERVICE_EXIT_POOL_EXHAUSTED",
                    "service=" + selectedServiceName + " attempts=" + serviceExitAttempt + " last=" + result.summary());
            return;
        }'''
exhaust_new = '''        if (serviceExitAttempt >= serviceExitCandidates.length) {
            AiAccessLog.w(this, "SERVICE_EXIT_POOL_EXHAUSTED",
                    "service=" + selectedServiceName + " transport=" + requestedLocalTransport +
                            " attempts=" + serviceExitAttempt + " last=" + result.summary());
            restartForNextLocalTransport(result);
            return;
        }'''
if exhaust_old not in a:
    raise SystemExit('RC4/5 exit pool exhausted block not found')
a = a.replace(exhaust_old, exhaust_new, 1)

# A normal user stop clears transport rotation state. Internal transport restart never calls stopRoute().
stop_marker = '''        activeServiceName = null;
        serviceProbePending = false;
        torBootstrapped = false;'''
stop_repl = '''        activeServiceName = null;
        serviceProbePending = false;
        localTransportRestarting = false;
        serviceTransportAttempt = 0;
        requestedLocalTransport = "";
        torBootstrapped = false;'''
if stop_marker not in a:
    raise SystemExit('RC5 stop-session marker not found')
a = a.replace(stop_marker, stop_repl, 1)

# Keep consumer-facing wording neutral even during transport rotation.
status_marker = '        if (percent < 70) return "Ищу оптимальный путь";'
status_repl = '''        if (percent < 70) return localTransportRestarting ? "Пробую другой способ" : "Ищу оптимальный путь";'''
if status_marker not in a:
    raise SystemExit('RC5 neutral status marker not found')
a = a.replace(status_marker, status_repl, 1)

activity.write_text(a, encoding="utf-8")

# SmartConnect: honor the service-specific local transport hint first, then its normal memory/AutoConf.
smart = app / "src/main/java/org/torproject/android/service/circumvention/SmartConnect.kt"
t = smart.read_text(encoding="utf-8")

# MEEK is a built-in public bridge transport and becomes part of the bounded fallback pool.
old_fallback = '''    private val fallbackOrder = listOf(
        Transport.SNOWFLAKE,
        Transport.SNOWFLAKE_AMP,
        Transport.WEBTUNNEL,
        Transport.DNSTT,
        Transport.CUSTOM,
        Transport.OBFS4,
        Transport.NONE
    )'''
new_fallback = '''    private val fallbackOrder = listOf(
        Transport.SNOWFLAKE,
        Transport.SNOWFLAKE_AMP,
        Transport.WEBTUNNEL,
        Transport.OBFS4,
        Transport.MEEK,
        Transport.DNSTT,
        Transport.CUSTOM,
        Transport.NONE
    )'''
if old_fallback not in t:
    raise SystemExit('SmartConnect fallbackOrder marker not found')
t = t.replace(old_fallback, new_fallback, 1)

old_pref = '            Prefs.transport = remembered ?: autoSuggested ?: Transport.SNOWFLAKE'
new_pref = '''            val productPrefs = context.getSharedPreferences("freegpt_product", Context.MODE_PRIVATE)
            val hintId = productPrefs.getString("next_transport_hint", "") ?: ""
            val hinted = if (hintId.isNotBlank()) Transport.fromId(hintId) else null
            Prefs.transport = hinted ?: remembered ?: autoSuggested ?: Transport.SNOWFLAKE
            notify("LOCAL_TRANSPORT_PLAN", "hint=$hintId selected=${Prefs.transport.id} localOnly=true")'''
if old_pref not in t:
    raise SystemExit('RC3 SmartConnect preferred transport marker not found')
t = t.replace(old_pref, new_pref, 1)
smart.write_text(t, encoding="utf-8")

# Coherent diagnostic version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc5-compact-session' not in lt:
    raise SystemExit('RC5 log version marker not found')
lt = lt.replace('1.0.0-rc5-compact-session', '1.0.0-rc6-local-only')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC6 LOCAL-ONLY patch applied')
print('versionName=1.0.0-rc6-local-only')
print('servers=NONE private endpoints=NONE backend=NONE')
print('local transports=snowflake,snowflake_amp,webtunnel,obfs4,meek,dnstt,direct')
print('provider memory=per-service exit + transport')
