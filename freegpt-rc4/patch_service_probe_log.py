from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC4 identity: stable package, higher version only.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 102' not in s:
    raise SystemExit('RC3 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 102', 'val orbotBaseVersionCode = 103', 1)
if 'versionName = "1.0.0-rc3-live-status"' not in s:
    raise SystemExit('RC3 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc3-live-status"', 'versionName = "1.0.0-rc4-service-probe-log"', 1)
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# RC4 runtime fields: provider-specific route tests, state restoration, and real settings log.
field_marker = '    private int liveBootstrapPercent = 0;'
fields = '''    private int liveBootstrapPercent = 0;
    private TextView settingsRuntimeView;
    private TextView settingsLiveLogView;
    private boolean statusRefreshRequested = false;
    private boolean serviceProbePending = false;
    private String[] serviceExitCandidates = {"nl", "de", "fr", "gb", "us"};
    private int serviceExitAttempt = 0;
    private long liveStatusReadyUntilMs = 0L;'''
if field_marker not in a:
    raise SystemExit('RC3 liveBootstrapPercent field marker not found')
a = a.replace(field_marker, fields, 1)

# Request current Orbot state whenever the activity returns from Chrome or a share/save screen.
on_resume_old = '''    @Override protected void onResume() {
        super.onResume();
        AiAccessLog.i(this, "ACTIVITY_RESUME", "state=" + state);
    }
'''
on_resume_new = '''    @Override protected void onResume() {
        super.onResume();
        AiAccessLog.i(this, "ACTIVITY_RESUME", "state=" + state);
        requestOrbotStatus();
    }

    private void requestOrbotStatus() {
        try {
            statusRefreshRequested = true;
            Intent i = new Intent(this, OrbotService.class)
                    .setAction(OrbotConstants.CMD_ACTIVE)
                    .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true);
            ContextCompat.startForegroundService(this, i);
            AiAccessLog.i(this, "ORBOT_STATUS_REQUEST", "action=CMD_ACTIVE state=" + state);
        } catch (Throwable t) {
            statusRefreshRequested = false;
            AiAccessLog.e(this, "ORBOT_STATUS_REQUEST_FAIL", String.valueOf(t.getMessage()), t);
        }
    }
'''
if on_resume_old not in a:
    raise SystemExit('onResume block marker not found')
a = a.replace(on_resume_old, on_resume_new, 1)

# Restore a live route after Activity recreation instead of showing OFF while Tor is still running.
status_on_old = '''                if (TorService.STATUS_ON.equals(status)) {
                    onTorBootstrapped("TOR_STATUS_ON");
                } else if (TorService.STATUS_OFF.equals(status)) {'''
status_on_new = '''                if (TorService.STATUS_ON.equals(status)) {
                    if (statusRefreshRequested && "OFF".equals(state)) {
                        statusRefreshRequested = false;
                        torBootstrapped = true;
                        state = "READY";
                        detail = "Активный маршрут восстановлен";
                        AiAccessLog.i(AiAccessActivity.this, "ROUTE_STATE_RESTORED", "socks=" + socksPort + " exit=" + currentExit());
                        if (serviceProbePending && socksPort > 0) scheduleProbe(500);
                    } else {
                        statusRefreshRequested = false;
                        onTorBootstrapped("TOR_STATUS_ON");
                    }
                } else if (TorService.STATUS_OFF.equals(status)) {
                    statusRefreshRequested = false;'''
if status_on_old not in a:
    raise SystemExit('TOR STATUS_ON/OFF marker not found')
a = a.replace(status_on_old, status_on_new, 1)

# If a restored route reports its SOCKS port, continue the queued provider test immediately.
ports_old = '''                if (socksPort > 0) {
                    if (torBootstrapped && routeProbeArmed) scheduleProbe(1500);
                    else AiAccessLog.i(AiAccessActivity.this, "ROUTE_PROBE_GATE", "SOCKS ready; waiting for bootstrap=100");
                }'''
ports_new = '''                if (socksPort > 0) {
                    if (torBootstrapped && (routeProbeArmed || serviceProbePending)) scheduleProbe(700);
                    else AiAccessLog.i(AiAccessActivity.this, "ROUTE_PROBE_GATE", "SOCKS ready; waiting for bootstrap=100");
                }'''
if ports_old not in a:
    raise SystemExit('SOCKS/route gate marker not found')
a = a.replace(ports_old, ports_new, 1)

# Clear settings dynamic overlays with the rest of the screen references.
clear_old = '''        liveStatusProgress = null;
        liveStatusAction = null;

        FrameLayout root = new FrameLayout(this);'''
clear_new = '''        liveStatusProgress = null;
        liveStatusAction = null;
        settingsRuntimeView = null;
        settingsLiveLogView = null;

        FrameLayout root = new FrameLayout(this);'''
if clear_old not in a:
    raise SystemExit('RC3 dynamic reference clear marker not found')
a = a.replace(clear_old, clear_new, 1)

# Selection screen gets the connection panel; settings gets real state and real rolling log.
attach_old = '''        if ("select".equals(screenId) || "settings".equals(screenId)) {
            attachLiveStatusPanel(root);
        }
'''
attach_new = '''        if ("select".equals(screenId)) {
            attachLiveStatusPanel(root);
        } else if ("settings".equals(screenId)) {
            attachSettingsRuntime(root);
        }
'''
if attach_old not in a:
    raise SystemExit('RC3 panel attach marker not found')
a = a.replace(attach_old, attach_new, 1)

# Insert settings overlays immediately before the existing RC3 live status panel method.
panel_marker = '    private void attachLiveStatusPanel(FrameLayout root) {'
settings_methods = r'''    private void attachSettingsRuntime(FrameLayout root) {
        settingsRuntimeView = text("", 12, TEXT, Typeface.NORMAL);
        settingsRuntimeView.setPadding(dp(10), dp(8), dp(10), dp(8));
        settingsRuntimeView.setBackground(round(Color.argb(232, 3, 17, 39), 12, Color.rgb(39, 144, 219), 1));
        settingsRuntimeView.setLineSpacing(0f, 1.08f);
        root.addView(settingsRuntimeView, new FrameLayout.LayoutParams(1, 1));

        settingsLiveLogView = text("", 9, Color.rgb(179, 211, 241), Typeface.NORMAL);
        settingsLiveLogView.setTypeface(Typeface.MONOSPACE);
        settingsLiveLogView.setPadding(dp(9), dp(7), dp(9), dp(7));
        settingsLiveLogView.setBackground(round(Color.argb(242, 2, 13, 31), 12, Color.rgb(31, 92, 153), 1));
        settingsLiveLogView.setLineSpacing(0f, 1.02f);
        root.addView(settingsLiveLogView, new FrameLayout.LayoutParams(1, 1));

        View.OnLayoutChangeListener positioner = (v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            positionArtworkRect(root, settingsRuntimeView, 145f, 174f, 495f, 313f);
            positionArtworkRect(root, settingsLiveLogView, 45f, 714f, 495f, 921f);
        };
        root.addOnLayoutChangeListener(positioner);
        root.post(() -> {
            positionArtworkRect(root, settingsRuntimeView, 145f, 174f, 495f, 313f);
            positionArtworkRect(root, settingsLiveLogView, 45f, 714f, 495f, 921f);
            renderSettingsRuntime();
        });
    }

    private void positionArtworkRect(FrameLayout root, View child, float leftArt, float topArt, float rightArt, float bottomArt) {
        if (root == null || child == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        float scale = Math.min(root.getWidth() / 540f, root.getHeight() / 960f);
        float drawW = 540f * scale;
        float drawH = 960f * scale;
        float offsetX = (root.getWidth() - drawW) / 2f;
        float offsetY = (root.getHeight() - drawH) / 2f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
        lp.width = Math.max(1, Math.round((rightArt - leftArt) * scale));
        lp.height = Math.max(1, Math.round((bottomArt - topArt) * scale));
        lp.leftMargin = Math.round(offsetX + leftArt * scale);
        lp.topMargin = Math.round(offsetY + topArt * scale);
        child.setLayoutParams(lp);
    }

    private String compactLogTail() {
        String raw = AiAccessLog.tail(this, 14000);
        if (raw == null || raw.isEmpty()) return "Лог пока пуст.";
        String[] lines = raw.split("\\n");
        int start = Math.max(0, lines.length - 9);
        StringBuilder out = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.length() > 88) line = line.substring(0, 85) + "…";
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.length() == 0 ? "Лог пока пуст." : out.toString();
    }

    private void renderSettingsRuntime() {
        if (settingsRuntimeView == null && settingsLiveLogView == null) return;
        String transport;
        try { transport = AiAccessPrefs.selectedTransport(); }
        catch (Throwable ignored) { transport = "auto"; }
        String stateLabel;
        if ("READY".equals(state)) stateLabel = "Готово";
        else if ("ERROR".equals(state)) stateLabel = "Ошибка";
        else if ("OFF".equals(state)) stateLabel = "Выключено";
        else if (probing || "TOR_ON".equals(state)) stateLabel = "Проверка сервиса";
        else stateLabel = "Подключение " + Math.max(0, liveBootstrapPercent) + "%";

        if (settingsRuntimeView != null) {
            settingsRuntimeView.setText("Состояние: " + stateLabel +
                    "\nБраузер: " + (targetLabel == null || targetLabel.isEmpty() ? "автоматически" : targetLabel) +
                    "\nСервис: " + selectedServiceName +
                    "\nМаршрут: " + currentExit().toUpperCase() + " · " + transport);
        }
        if (settingsLiveLogView != null) settingsLiveLogView.setText(compactLogTail());
    }

'''
if panel_marker not in a:
    raise SystemExit('RC3 live panel method marker not found')
a = a.replace(panel_marker, settings_methods + panel_marker, 1)

# The green READY panel is useful briefly, but it must not permanently cover the AI cards.
visible_old = '        boolean visible = !"OFF".equals(state);'
visible_new = '''        boolean visible = !"OFF".equals(state) &&
                (!"READY".equals(state) || SystemClock.elapsedRealtime() < liveStatusReadyUntilMs);'''
if visible_old not in a:
    raise SystemExit('RC3 live panel visibility marker not found')
a = a.replace(visible_old, visible_new, 1)

# Selecting any provider now verifies that provider through SOCKS. A READY ChatGPT route is
# never blindly reused for Claude/Gemini/Grok.
service_start = a.find('    private void startSelectedService(String name, String url) {')
service_end = a.find('    private void showScreen(String next) {', service_start)
if service_start < 0 or service_end < 0:
    raise SystemExit('startSelectedService/showScreen markers not found')
new_service = r'''    private void startSelectedService(String name, String url) {
        if (trialExpired() && !getSharedPreferences(PREFS_APP, MODE_PRIVATE).getBoolean("unlocked_forever", false)) {
            showScreen("pay");
            return;
        }
        selectedServiceName = name;
        selectedServiceUrl = url;
        liveStatusReadyUntilMs = 0L;
        if (probing) {
            probeGeneration++;
            probing = false;
            AiAccessLog.i(this, "SERVICE_PROBE_CANCELLED", "newService=" + name);
        }
        serviceExitCandidates = buildServiceExitCandidates(name, currentExit());
        serviceExitAttempt = 0;
        serviceProbePending = true;
        AiAccessLog.i(this, "AI_SERVICE_SELECTED", "name=" + name + " url=" + url +
                " exits=" + android.text.TextUtils.join(",", serviceExitCandidates));

        if (torBootstrapped && socksPort > 0 && ("READY".equals(state) || "TOR_ON".equals(state))) {
            state = "TOR_ON";
            detail = "Проверяю доступ к " + name + " через " + currentExit().toUpperCase();
            render();
            startProbe(false);
            return;
        }

        if (!"OFF".equals(state) && !"ERROR".equals(state)) {
            detail = "Соединение поднимается · затем проверю " + name;
            AiAccessLog.i(this, "SERVICE_PROBE_QUEUED", "state=" + state + " service=" + name);
            render();
            return;
        }

        if (statusRefreshRequested) {
            detail = "Проверяю уже активный маршрут для " + name;
            render();
            main.postDelayed(() -> {
                if (serviceProbePending && "OFF".equals(state)) onPower();
            }, 1400);
            return;
        }

        Toast.makeText(this, "Подключаю " + name + "…", Toast.LENGTH_SHORT).show();
        onPower();
    }

    private String[] buildServiceExitCandidates(String service, String current) {
        java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();
        if (current != null && !current.isEmpty()) order.add(current.toLowerCase(Locale.ROOT));
        String remembered = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getString("last_exit_" + serviceKey(service), "");
        if (remembered != null && !remembered.isEmpty()) order.add(remembered.toLowerCase(Locale.ROOT));
        for (String cc : EXITS) order.add(cc);
        return order.toArray(new String[0]);
    }

    private String serviceKey(String service) {
        if (service == null) return "unknown";
        return service.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    private String selectedServiceHost() {
        if ("Claude".equalsIgnoreCase(selectedServiceName)) return "claude.ai";
        if ("Gemini".equalsIgnoreCase(selectedServiceName)) return "gemini.google.com";
        if ("Grok".equalsIgnoreCase(selectedServiceName)) return "grok.com";
        return "chatgpt.com";
    }

    private String selectedServicePath() {
        if ("Gemini".equalsIgnoreCase(selectedServiceName)) return "/app";
        return "/";
    }

    private boolean providerResponseAccepted(int code) {
        return code >= 200 && code < 400;
    }

'''
a = a[:service_start] + new_service + a[service_end:]

# Replace the ChatGPT-only probe and old exit retry with a provider-specific probe.
probe_start = a.find('    private void startProbe(boolean auto) {')
probe_end = a.find('    private void switchExit(String cc) {', probe_start)
if probe_start < 0 or probe_end < 0:
    raise SystemExit('startProbe/switchExit markers not found')
new_probe = r'''    private void startProbe(boolean auto) {
        if (probing) return;
        if (!torBootstrapped) {
            serviceProbePending = true;
            AiAccessLog.w(this, "SERVICE_PROBE_BLOCKED", "bootstrap<100 service=" + selectedServiceName);
            if (!auto) Toast.makeText(this, "Сначала маршрут должен дойти до 100%", Toast.LENGTH_SHORT).show();
            return;
        }
        if (socksPort <= 0) {
            serviceProbePending = true;
            AiAccessLog.w(this, "SERVICE_PROBE_WAIT_SOCKS", "service=" + selectedServiceName);
            if (!auto) Toast.makeText(this, "SOCKS ещё не готов", Toast.LENGTH_SHORT).show();
            requestOrbotStatus();
            return;
        }

        probing = true;
        serviceProbePending = false;
        state = "TOR_ON";
        String host = selectedServiceHost();
        String path = selectedServicePath();
        String service = selectedServiceName;
        String exit = currentExit();
        detail = "Проверяю " + service + " · " + exit.toUpperCase();
        if (probeButton != null) {
            probeButton.setEnabled(false);
            probeButton.setText("ПРОВЕРЯЮ " + service.toUpperCase() + "…");
        }
        render();

        final int generation = probeGeneration;
        final int port = socksPort;
        new Thread(() -> {
            AiAccessLog.i(AiAccessActivity.this, "SERVICE_PROBE_START",
                    "service=" + service + " host=" + host + " path=" + path + " exit=" + exit + " socks=" + port);
            SocksProbe.Result page = SocksProbe.fetch(port, host, path, 22000, 3072);
            boolean accepted = providerResponseAccepted(page.httpCode);
            AiAccessLog.i(AiAccessActivity.this, accepted ? "SERVICE_PROBE_OK" : "SERVICE_PROBE_FAIL",
                    "service=" + service + " exit=" + exit + " " + page.summary());

            main.post(() -> {
                probing = false;
                if (probeButton != null) {
                    probeButton.setEnabled(true);
                    probeButton.setText("ПРОВЕРИТЬ МАРШРУТ");
                }
                if (generation != probeGeneration) return;
                if (routeView != null) routeView.setText(service + " · " + exit.toUpperCase() + "\n" + page.summary());

                if (accepted) {
                    state = "READY";
                    detail = service + " доступен · " + exit.toUpperCase() + " · HTTP " + page.httpCode;
                    liveStatusReadyUntilMs = SystemClock.elapsedRealtime() + 2600L;
                    getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                            .putString("last_exit_" + serviceKey(service), exit).apply();
                    AiAccessLog.i(AiAccessActivity.this, "SERVICE_ROUTE_ACCEPTED",
                            "service=" + service + " exit=" + exit + " code=" + page.httpCode + " durationMs=" + page.durationMs);
                    render();
                    main.postDelayed(() -> launchChatGpt(), 450L);
                } else {
                    tryNextServiceExit(page);
                    render();
                }
            });
        }, "ServiceProbe-" + service).start();
    }

    private void tryNextServiceExit(SocksProbe.Result result) {
        if (serviceExitCandidates == null || serviceExitCandidates.length == 0) {
            serviceExitCandidates = buildServiceExitCandidates(selectedServiceName, currentExit());
            serviceExitAttempt = 0;
        }
        serviceExitAttempt++;
        if (serviceExitAttempt >= serviceExitCandidates.length) {
            state = "ERROR";
            detail = selectedServiceName + " не открылся через доступные маршруты · последний " + result.summary();
            AiAccessLog.w(this, "SERVICE_EXIT_POOL_EXHAUSTED",
                    "service=" + selectedServiceName + " attempts=" + serviceExitAttempt + " last=" + result.summary());
            return;
        }

        String next = serviceExitCandidates[serviceExitAttempt];
        setCurrentExit(next);
        state = "CONNECTING";
        detail = selectedServiceName + " не ответил · пробую " + next.toUpperCase();
        AiAccessLog.i(this, "SERVICE_EXIT_SWITCH",
                "service=" + selectedServiceName + " next=" + next + " attempt=" + (serviceExitAttempt + 1) +
                        "/" + serviceExitCandidates.length + " previous=" + result.summary());
        switchExit(next);
        serviceProbePending = true;
        scheduleProbe(12000);
    }

    private void setCurrentExit(String cc) {
        for (int i = 0; i < EXITS.length; i++) {
            if (EXITS[i].equalsIgnoreCase(cc)) {
                exitIndex = i;
                return;
            }
        }
    }

'''
a = a[:probe_start] + new_probe + a[probe_end:]

# Start a fresh engine with the first provider candidate instead of hard-resetting to NL.
power_exit_old = '''        exitIndex = 0;
        probeGeneration++;'''
power_exit_new = '''        setCurrentExit(serviceExitCandidates != null && serviceExitCandidates.length > 0 ? serviceExitCandidates[0] : "nl");
        serviceExitAttempt = 0;
        probeGeneration++;'''
if power_exit_old not in a:
    raise SystemExit('onPower exit reset marker not found')
a = a.replace(power_exit_old, power_exit_new, 1)

# The settings screen now gets both the normal render loop and the visible real log.
render_tail_old = '''        if (logView != null) logView.setText(AiAccessLog.tail(this, 18000));
        renderLiveStatus();
    }'''
render_tail_new = '''        if (logView != null) logView.setText(AiAccessLog.tail(this, 18000));
        renderLiveStatus();
        renderSettingsRuntime();
    }'''
if render_tail_old not in a:
    raise SystemExit('RC3 render tail marker not found')
a = a.replace(render_tail_old, render_tail_new, 1)

activity.write_text(a, encoding="utf-8")

# Coherent diagnostic version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc3-live-status' not in lt:
    raise SystemExit('RC3 log version marker not found')
lt = lt.replace('1.0.0-rc3-live-status', '1.0.0-rc4-service-probe-log')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC4 provider probe + live settings log patch applied')
print('versionName=1.0.0-rc4-service-probe-log')
print('providers=ChatGPT,Claude,Gemini,Grok each probed before browser open')
print('settings=real rolling log and restored active-route state')
