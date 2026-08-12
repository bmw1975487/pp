from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC4 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 102' not in s:
    raise SystemExit('RC3 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 102', 'val orbotBaseVersionCode = 103', 1)
if 'versionName = "1.0.0-rc3-live-status"' not in s:
    raise SystemExit('RC3 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc3-live-status"', 'versionName = "1.0.0-rc4-provider-log"', 1)
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Live technical log shown inside the exact settings artwork log panel.
field_marker = '    private int liveBootstrapPercent = 0;'
if field_marker not in a:
    raise SystemExit('RC3 liveBootstrapPercent marker not found')
a = a.replace(field_marker, field_marker + '\n    private TextView liveTechnicalLog;\n    private ScrollView liveTechnicalLogScroll;', 1)

clear_marker = '''        liveStatusProgress = null;
        liveStatusAction = null;

        FrameLayout root = new FrameLayout(this);'''
clear_repl = '''        liveStatusProgress = null;
        liveStatusAction = null;
        liveTechnicalLog = null;
        liveTechnicalLogScroll = null;

        FrameLayout root = new FrameLayout(this);'''
if clear_marker not in a:
    raise SystemExit('RC3 dynamic clear marker not found')
a = a.replace(clear_marker, clear_repl, 1)

# The connection status card is useful on the selector, but it must not cover the technical log in settings.
old_status_attach = '''        if ("select".equals(screenId) || "settings".equals(screenId)) {
            attachLiveStatusPanel(root);
        }

        root.setOnTouchListener((v, ev) -> {'''
new_status_attach = '''        if ("select".equals(screenId)) {
            attachLiveStatusPanel(root);
        }
        if ("settings".equals(screenId)) {
            attachLiveTechnicalLog(root);
        }

        root.setOnTouchListener((v, ev) -> {'''
if old_status_attach not in a:
    raise SystemExit('RC3 status attach marker not found')
a = a.replace(old_status_attach, new_status_attach, 1)

# Insert a real log overlay precisely into the artwork's Technical log box.
hit_marker = '    private boolean hit(float x, float y, float l, float t, float r, float b) {'
live_log_methods = r'''    private void attachLiveTechnicalLog(FrameLayout root) {
        liveTechnicalLogScroll = new ScrollView(this);
        liveTechnicalLogScroll.setFillViewport(true);
        liveTechnicalLogScroll.setBackground(round(Color.argb(238, 3, 14, 31), 18, Color.rgb(53, 158, 255), 1));
        liveTechnicalLogScroll.setPadding(dp(10), dp(8), dp(10), dp(8));

        liveTechnicalLog = text("", 9, Color.rgb(174, 203, 230), Typeface.NORMAL);
        liveTechnicalLog.setTypeface(Typeface.MONOSPACE);
        liveTechnicalLog.setLineSpacing(0, 1.08f);
        liveTechnicalLog.setTextIsSelectable(true);
        liveTechnicalLogScroll.addView(liveTechnicalLog, new ScrollView.LayoutParams(-1, -2));
        root.addView(liveTechnicalLogScroll, new FrameLayout.LayoutParams(1, 1));

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            float vw = v.getWidth();
            float vh = v.getHeight();
            if (vw <= 0f || vh <= 0f || liveTechnicalLogScroll == null) return;
            float scale = Math.min(vw / 540f, vh / 960f);
            float drawW = 540f * scale;
            float drawH = 960f * scale;
            float left = (vw - drawW) / 2f;
            float top = (vh - drawH) / 2f;
            int x1 = Math.round(left + 34f * scale);
            int y1 = Math.round(top + 700f * scale);
            int x2 = Math.round(left + 506f * scale);
            int y2 = Math.round(top + 922f * scale);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, x2 - x1), Math.max(1, y2 - y1));
            lp.leftMargin = x1;
            lp.topMargin = y1;
            liveTechnicalLogScroll.setLayoutParams(lp);
        });
    }

    private String compactLiveLog() {
        String raw = AiAccessLog.tail(this, 9000);
        if (raw == null || raw.isEmpty()) return "Лог пока пуст.";
        String[] lines = raw.split("\\n");
        StringBuilder out = new StringBuilder();
        int start = Math.max(0, lines.length - 22);
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            int p = line.indexOf("] ");
            if (p >= 0 && p + 2 < line.length()) line = line.substring(p + 2);
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private String providerHost() {
        if ("Claude".equals(selectedServiceName)) return "claude.ai";
        if ("Gemini".equals(selectedServiceName)) return "gemini.google.com";
        if ("Grok".equals(selectedServiceName)) return "grok.com";
        return "chatgpt.com";
    }

    private String providerEventName() {
        if (selectedServiceName == null || selectedServiceName.isEmpty()) return "CHATGPT";
        return selectedServiceName.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private boolean providerHttpAccepted(int code) {
        // 2xx/3xx means normal response; 4xx still proves DNS/TLS/HTTP reached the selected provider.
        // Browser-side cookies or anti-bot checks can then continue interactively.
        return code >= 200 && code < 500;
    }

'''
if hit_marker not in a:
    raise SystemExit('hit helper marker not found')
a = a.replace(hit_marker, live_log_methods + hit_marker, 1)

# Replace OpenAI-only route acceptance with a provider-specific route test.
probe_start = a.find('    private void startProbe(boolean auto) {')
probe_end = a.find('    private void switchExit(String cc) {', probe_start)
if probe_start < 0 or probe_end < 0:
    raise SystemExit('startProbe/switchExit markers not found')
new_probe = r'''    private void startProbe(boolean auto) {
        if (probing) return;
        if (socksPort <= 0) {
            AiAccessLog.w(this, "ROUTE_PROBE_SKIPPED", "SOCKS port not ready service=" + selectedServiceName);
            if (!auto) Toast.makeText(this, "SOCKS ещё не готов", Toast.LENGTH_SHORT).show();
            return;
        }
        probing = true;
        if (probeButton != null) {
            probeButton.setEnabled(false);
            probeButton.setText("ПРОВЕРЯЮ…");
        }
        final int generation = probeGeneration;
        final int port = socksPort;
        final String exit = currentExit();
        final String service = selectedServiceName == null ? "ChatGPT" : selectedServiceName;
        final String host = providerHost();
        final String eventName = providerEventName();
        new Thread(() -> {
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_PROBE_START", "service=" + service + " host=" + host + " exit=" + exit + " socks=" + port);
            SocksProbe.Result trace = SocksProbe.fetch(port, "www.cloudflare.com", "/cdn-cgi/trace", 12000, 4096);
            String ip = SocksProbe.traceValue(trace.body, "ip");
            String loc = SocksProbe.traceValue(trace.body, "loc");
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_EXIT_TRACE", "service=" + service + " configured=" + exit + " actual=" + loc + " ip=" + ip + " result=" + trace.summary());

            SocksProbe.Result provider = SocksProbe.fetch(port, host, "/", 18000, 4096);
            AiAccessLog.i(AiAccessActivity.this,
                    providerHttpAccepted(provider.httpCode) ? "ROUTED_" + eventName + "_HTTP" : "ROUTED_" + eventName + "_FAIL",
                    "host=" + host + " " + provider.summary());

            // Keep the known OpenAI check for ChatGPT diagnostics, but do not use it to approve other providers.
            SocksProbe.Result api = null;
            if ("ChatGPT".equals(service)) {
                api = SocksProbe.fetch(port, "api.openai.com", "/v1/models", 14000, 2048);
                AiAccessLog.i(AiAccessActivity.this, api.okHttp() ? "ROUTED_OPENAI_HTTP" : "ROUTED_OPENAI_FAIL", api.summary());
            }

            boolean accepted = providerHttpAccepted(provider.httpCode);
            if ("ChatGPT".equals(service) && api != null) {
                boolean apiAccepted = api.httpCode == 401 || providerHttpAccepted(api.httpCode);
                accepted = accepted || apiAccepted;
            }
            final SocksProbe.Result apiFinal = api;
            String summary = "Сервис: " + service +
                    "\nHost: " + host +
                    "\nExit: " + exit.toUpperCase() + (loc.isEmpty() ? "" : " / " + loc) +
                    "\nIP: " + (ip.isEmpty() ? "unknown" : ip) +
                    "\nHTTP: " + provider.summary() +
                    (apiFinal == null ? "" : "\nOpenAI API: " + apiFinal.summary());

            final boolean acceptedFinal = accepted;
            main.post(() -> {
                probing = false;
                if (probeButton != null) {
                    probeButton.setEnabled(true);
                    probeButton.setText("ПРОВЕРИТЬ МАРШРУТ");
                }
                if (generation != probeGeneration) return;
                if (routeView != null) routeView.setText(summary);
                if (acceptedFinal) {
                    state = "READY";
                    detail = service + " доступен · " + exit.toUpperCase() + (loc.isEmpty() ? "" : " / " + loc);
                    AiAccessLog.i(AiAccessActivity.this, "ROUTE_ACCEPTED",
                            "service=" + service + " host=" + host + " exit=" + exit + " loc=" + loc + " ip=" + ip + " http=" + provider.httpCode);
                    launchChatGpt();
                } else {
                    AiAccessLog.w(AiAccessActivity.this, "ROUTE_REJECTED",
                            "service=" + service + " host=" + host + " exit=" + exit + " loc=" + loc + " http=" + provider.httpCode);
                    tryNextExitForProvider(host, provider.httpCode);
                }
                render();
            });
        }, "RouteProbe-" + eventName).start();
    }

    private void tryNextExitForProvider(String host, int code) {
        if (exitIndex + 1 >= EXITS.length) {
            state = "ERROR";
            detail = selectedServiceName + ": все Tor-exit проверены · " + host + " HTTP=" + code;
            AiAccessLog.w(this, "ROUTE_POOL_EXHAUSTED", detail);
            return;
        }
        exitIndex++;
        String next = currentExit();
        state = "CONNECTING";
        detail = selectedServiceName + ": пробую exit " + next.toUpperCase();
        AiAccessLog.i(this, "PROVIDER_EXIT_NEXT", "service=" + selectedServiceName + " host=" + host + " previousHttp=" + code + " next=" + next);
        switchExit(next);
        scheduleProbe(11000);
    }

'''
a = a[:probe_start] + new_probe + a[probe_end:]

# Harden browser opening for every selected provider and log every stage.
launch_start = a.find('    private void launchChatGpt() {')
launch_end = a.find('    private Uri createZipUri(', launch_start)
if launch_start < 0 or launch_end < 0:
    raise SystemExit('launch method markers not found')
new_launch = r'''    private void launchChatGpt() {
        String service = selectedServiceName == null || selectedServiceName.isEmpty() ? "ChatGPT" : selectedServiceName;
        String url = selectedServiceUrl == null || selectedServiceUrl.isEmpty() ? "https://chatgpt.com/" : selectedServiceUrl;
        AiAccessLog.i(this, "AI_WEB_OPEN_REQUEST", "service=" + service + " url=" + url + " targetBrowser=" + targetPackage);
        try {
            if (targetPackage == null || targetPackage.isEmpty()) {
                String detected = discoverBrowserTarget();
                if (detected != null) targetPackage = detected;
            }
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            open.addCategory(Intent.CATEGORY_BROWSABLE);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (targetPackage != null && !targetPackage.isEmpty()) open.setPackage(targetPackage);
            try {
                startActivity(open);
                AiAccessLog.i(this, "AI_WEB_OPEN", "service=" + service + " url=" + url + " browserPackage=" + targetPackage);
                return;
            } catch (android.content.ActivityNotFoundException first) {
                AiAccessLog.w(this, "AI_WEB_BROWSER_PACKAGE_FAIL", "service=" + service + " url=" + url + " browserPackage=" + targetPackage + " err=" + first.getMessage());
            }

            Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            fallback.addCategory(Intent.CATEGORY_BROWSABLE);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(fallback);
            AiAccessLog.i(this, "AI_WEB_OPEN_FALLBACK", "service=" + service + " url=" + url);
        } catch (Throwable t) {
            AiAccessLog.e(this, "AI_WEB_OPEN_FAIL", "service=" + service + " url=" + url + " err=" + String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Не удалось открыть " + service + ". Ошибка записана в лог.", Toast.LENGTH_LONG).show();
        }
    }

'''
a = a[:launch_start] + new_launch + a[launch_end:]

# Render the real technical log on every 750 ms refresh.
render_marker = '        renderLiveStatus();\n    }'
render_repl = '''        renderLiveStatus();
        if (liveTechnicalLog != null) {
            liveTechnicalLog.setText(compactLiveLog());
            if (liveTechnicalLogScroll != null) liveTechnicalLogScroll.post(() -> liveTechnicalLogScroll.fullScroll(View.FOCUS_DOWN));
        }
    }'''
if render_marker not in a:
    raise SystemExit('RC3 renderLiveStatus marker not found')
a = a.replace(render_marker, render_repl, 1)

# Add tap diagnostics before every service start so physical logs show whether the hitbox fired.
start_marker = '''        selectedServiceName = name;
        selectedServiceUrl = url;
        AiAccessLog.i(this, "AI_SERVICE_SELECTED", "name=" + name + " url=" + url);'''
start_repl = '''        selectedServiceName = name;
        selectedServiceUrl = url;
        AiAccessLog.i(this, "AI_SERVICE_SELECTED", "name=" + name + " url=" + url + " currentState=" + state + " browser=" + targetPackage);'''
if start_marker not in a:
    raise SystemExit('service selection marker not found')
a = a.replace(start_marker, start_repl, 1)

activity.write_text(a, encoding="utf-8")

# Diagnostic version and provider-aware ZIP filtering.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc3-live-status' not in lt:
    raise SystemExit('RC3 log version marker not found')
lt = lt.replace('1.0.0-rc3-live-status', '1.0.0-rc4-provider-log')
lt = lt.replace('"ROUTE_", "EXIT_", "OPENAI", "CHATGPT", "TRANSPORT_", "SMARTCONNECT", "BOOTSTRAP_"',
                '"ROUTE_", "EXIT_", "OPENAI", "CHATGPT", "CLAUDE", "GEMINI", "GROK", "AI_WEB_", "AI_SERVICE_", "TRANSPORT_", "SMARTCONNECT", "BOOTSTRAP_"')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC4 provider-route + live-log patch applied')
print('versionName=1.0.0-rc4-provider-log')
print('providers=ChatGPT,Claude,Gemini,Grok individually probed through SOCKS')
print('settings=real live technical log overlay')
print('browser=open hardened with package fallback and explicit diagnostics')
