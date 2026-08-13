from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC5 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 103' not in s:
    raise SystemExit('RC4 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 103', 'val orbotBaseVersionCode = 104', 1)
if 'versionName = "1.0.0-rc4-service-probe-log"' not in s:
    raise SystemExit('RC4 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc4-service-probe-log"', 'versionName = "1.0.0-rc5-compact-session"', 1)
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# User-facing progress/session state. This is intentionally separate from technical Tor progress.
field_marker = '    private long liveStatusReadyUntilMs = 0L;'
fields = '''    private long liveStatusReadyUntilMs = 0L;
    private long uxStartedAtMs = 0L;
    private int uxLastPercent = 0;
    private String activeServiceName = null;'''
if field_marker not in a:
    raise SystemExit('RC4 field marker not found')
a = a.replace(field_marker, fields, 1)

# Compact connection panel: much smaller action button, large overall percentage, no technical wording.
panel_start = a.find('    private void attachLiveStatusPanel(FrameLayout root) {')
panel_end = a.find('    private void renderLiveStatus() {', panel_start)
if panel_start < 0 or panel_end < 0:
    raise SystemExit('RC4 attachLiveStatusPanel/renderLiveStatus markers not found')
new_panel = r'''    private void attachLiveStatusPanel(FrameLayout root) {
        liveStatusPanel = new LinearLayout(this);
        liveStatusPanel.setOrientation(LinearLayout.VERTICAL);
        liveStatusPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        liveStatusPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        liveStatusPanel.setBackground(round(Color.argb(238, 3, 15, 35), 18, Color.rgb(56, 192, 255), 1));
        liveStatusPanel.setElevation(dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        liveStatusPanel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        liveStatusTitle = text("", 15, TEXT, Typeface.BOLD);
        liveStatusTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(liveStatusTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        liveStatusPercent = text("0%", 30, Color.rgb(89, 221, 255), Typeface.BOLD);
        liveStatusPercent.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(liveStatusPercent, new LinearLayout.LayoutParams(dp(86), -2));

        liveStatusProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        liveStatusProgress.setMax(100);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(7));
        progressLp.setMargins(0, dp(4), 0, dp(6));
        liveStatusPanel.addView(liveStatusProgress, progressLp);

        liveStatusDetail = text("", 12, Color.rgb(202, 224, 244), Typeface.NORMAL);
        liveStatusDetail.setGravity(Gravity.CENTER);
        liveStatusPanel.addView(liveStatusDetail, new LinearLayout.LayoutParams(-1, -2));

        liveStatusAction = button("ОСТАНОВИТЬ", CARD2);
        liveStatusAction.setTextSize(12);
        liveStatusAction.setOnClickListener(v -> stopRoute());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(176), dp(38));
        actionLp.gravity = Gravity.CENTER_HORIZONTAL;
        actionLp.setMargins(0, dp(7), 0, 0);
        liveStatusPanel.addView(liveStatusAction, actionLp);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        panelLp.setMargins(dp(34), dp(16), dp(34), dp(18));
        root.addView(liveStatusPanel, panelLp);
        liveStatusPanel.setVisibility(View.GONE);
    }

    private int userProgressPercent() {
        if ("OFF".equals(state)) {
            uxLastPercent = 0;
            return 0;
        }
        if ("READY".equals(state)) {
            uxLastPercent = 100;
            return 100;
        }
        if (uxStartedAtMs <= 0L) uxStartedAtMs = SystemClock.elapsedRealtime();
        long elapsedSec = Math.max(0L, (SystemClock.elapsedRealtime() - uxStartedAtMs) / 1000L);
        int timed = (int)Math.min(94L, 5L + (elapsedSec * 3L) / 4L); // ~95% at about two minutes.
        int stage = 5;
        if ("PREPARING".equals(state)) stage = 9;
        else if ("CONNECTING".equals(state)) stage = Math.min(82, 16 + Math.max(0, liveBootstrapPercent) * 2 / 3);
        else if ("TOR_ON".equals(state) || probing) stage = 88;
        else if ("ERROR".equals(state)) stage = Math.max(uxLastPercent, 12);
        int next = Math.max(uxLastPercent, Math.max(timed, stage));
        if (!"READY".equals(state)) next = Math.min(next, 96);
        uxLastPercent = next;
        return next;
    }

    private String userStatusText(int percent) {
        if ("READY".equals(state)) return "Соединение готово";
        if ("ERROR".equals(state)) return "Не удалось завершить подключение";
        if (percent < 15) return "Подготавливаю соединение";
        if (percent < 30) return "Проверяю доступ к интернету";
        if (percent < 50) return "Настраиваю подключение";
        if (percent < 70) return "Ищу оптимальный путь";
        if (percent < 85) return "Проверяю стабильность";
        if (percent < 96) return "Почти готово";
        return "Завершаю подключение";
    }

'''
a = a[:panel_start] + new_panel + a[panel_end:]

# Replace technical live panel text with the user-facing two-minute progress model.
render_start = a.find('    private void renderLiveStatus() {')
render_end = a.find('    private boolean hit(', render_start)
if render_start < 0 or render_end < 0:
    raise SystemExit('RC4 renderLiveStatus/hit markers not found')
new_render_live = r'''    private void renderLiveStatus() {
        if (liveStatusPanel == null) return;

        boolean visible = !"OFF".equals(state);
        liveStatusPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;

        String service = activeServiceName != null ? activeServiceName : selectedServiceName;
        if (service == null || service.isEmpty()) service = "Сервис";
        int percent = userProgressPercent();

        liveStatusTitle.setText(service.toUpperCase(Locale.ROOT));
        liveStatusPercent.setText(percent + "%");
        liveStatusProgress.setIndeterminate(false);
        liveStatusProgress.setProgress(percent);
        liveStatusDetail.setText(userStatusText(percent));

        if ("READY".equals(state)) {
            liveStatusTitle.setTextColor(GREEN);
            liveStatusPercent.setTextColor(GREEN);
            liveStatusAction.setText("ОТКЛЮЧИТЬ");
            liveStatusAction.setOnClickListener(v -> stopRoute());
        } else if ("ERROR".equals(state)) {
            liveStatusTitle.setTextColor(RED);
            liveStatusPercent.setTextColor(RED);
            liveStatusAction.setText("ПОВТОРИТЬ");
            liveStatusAction.setOnClickListener(v -> {
                state = "OFF";
                uxStartedAtMs = 0L;
                uxLastPercent = 0;
                startSelectedService(selectedServiceName, selectedServiceUrl);
            });
        } else {
            liveStatusTitle.setTextColor(TEXT);
            liveStatusPercent.setTextColor(Color.rgb(89, 221, 255));
            liveStatusAction.setText("ОСТАНОВИТЬ");
            liveStatusAction.setOnClickListener(v -> stopRoute());
        }
    }

'''
a = a[:render_start] + new_render_live + a[render_end:]

# Replace service selection logic with one explicit active session. Other cards cannot silently hijack it.
service_start = a.find('    private void startSelectedService(String name, String url) {')
service_end = a.find('    private String[] buildServiceExitCandidates(', service_start)
if service_start < 0 or service_end < 0:
    raise SystemExit('RC4 startSelectedService/buildServiceExitCandidates markers not found')
new_service = r'''    private void startSelectedService(String name, String url) {
        if (trialExpired() && !getSharedPreferences(PREFS_APP, MODE_PRIVATE).getBoolean("unlocked_forever", false)) {
            showScreen("pay");
            return;
        }

        if (activeServiceName != null && !activeServiceName.equals(name) && !"OFF".equals(state) && !"ERROR".equals(state)) {
            Toast.makeText(this, "Сначала отключите " + activeServiceName, Toast.LENGTH_SHORT).show();
            AiAccessLog.i(this, "SERVICE_SELECTION_BLOCKED", "active=" + activeServiceName + " requested=" + name);
            return;
        }

        if (activeServiceName != null && activeServiceName.equals(name) && "READY".equals(state)) {
            selectedServiceName = name;
            selectedServiceUrl = url;
            AiAccessLog.i(this, "ACTIVE_SERVICE_REOPEN", "service=" + name);
            launchChatGpt();
            return;
        }

        if (!"OFF".equals(state) && !"ERROR".equals(state) && selectedServiceName != null && !selectedServiceName.equals(name)) {
            Toast.makeText(this, "Сейчас подключается " + selectedServiceName + ". Нажмите «Остановить», чтобы выбрать другой сервис.", Toast.LENGTH_LONG).show();
            AiAccessLog.i(this, "SERVICE_SELECTION_BLOCKED", "connecting=" + selectedServiceName + " requested=" + name);
            return;
        }

        selectedServiceName = name;
        selectedServiceUrl = url;
        activeServiceName = null;
        uxStartedAtMs = SystemClock.elapsedRealtime();
        uxLastPercent = 1;
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
            detail = "provider check " + name;
            render();
            startProbe(false);
            return;
        }

        if (!"OFF".equals(state) && !"ERROR".equals(state)) {
            detail = "connection in progress";
            AiAccessLog.i(this, "SERVICE_PROBE_QUEUED", "state=" + state + " service=" + name);
            render();
            return;
        }

        Toast.makeText(this, "Подключаю " + name + "…", Toast.LENGTH_SHORT).show();
        onPower();
    }

'''
a = a[:service_start] + new_service + a[service_end:]

# Mark the selected service as active only after its own probe succeeds.
accepted_marker = '''                    state = "READY";
                    detail = service + " доступен · " + exit.toUpperCase() + " · HTTP " + page.httpCode;'''
accepted_repl = '''                    state = "READY";
                    activeServiceName = service;
                    uxLastPercent = 100;
                    detail = service + " доступен · " + exit.toUpperCase() + " · HTTP " + page.httpCode;'''
if accepted_marker not in a:
    raise SystemExit('RC4 accepted-service marker not found')
a = a.replace(accepted_marker, accepted_repl, 1)

# Starting and stopping reset the simple UX timer/session cleanly.
start_marker = '''            liveBootstrapPercent = 0;
            state = "CONNECTING";'''
start_repl = '''            liveBootstrapPercent = 0;
            if (uxStartedAtMs <= 0L) uxStartedAtMs = SystemClock.elapsedRealtime();
            uxLastPercent = Math.max(uxLastPercent, 3);
            state = "CONNECTING";'''
if start_marker not in a:
    raise SystemExit('RC3/4 engine-start marker not found')
a = a.replace(start_marker, start_repl, 1)

stop_marker = '''        socksPort = -1;
        liveBootstrapPercent = 0;
        torBootstrapped = false;'''
stop_repl = '''        socksPort = -1;
        liveBootstrapPercent = 0;
        uxStartedAtMs = 0L;
        uxLastPercent = 0;
        activeServiceName = null;
        serviceProbePending = false;
        torBootstrapped = false;'''
if stop_marker not in a:
    raise SystemExit('RC3/4 stop marker not found')
a = a.replace(stop_marker, stop_repl, 1)

# Settings stays technical and now also shows which session is active.
settings_marker = '''            settingsRuntimeView.setText("Состояние: " + stateLabel +
                    "\\nБраузер: " + (targetLabel == null || targetLabel.isEmpty() ? "автоматически" : targetLabel) +
                    "\\nСервис: " + selectedServiceName +'''
settings_repl = '''            settingsRuntimeView.setText("Состояние: " + stateLabel +
                    "\\nБраузер: " + (targetLabel == null || targetLabel.isEmpty() ? "автоматически" : targetLabel) +
                    "\\nСервис: " + (activeServiceName != null ? activeServiceName + " · АКТИВЕН" : selectedServiceName) +'''
if settings_marker not in a:
    raise SystemExit('RC4 settings service marker not found')
a = a.replace(settings_marker, settings_repl, 1)

activity.write_text(a, encoding="utf-8")

# Diagnostic version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc4-service-probe-log' not in lt:
    raise SystemExit('RC4 log version marker not found')
lt = lt.replace('1.0.0-rc4-service-probe-log', '1.0.0-rc5-compact-session')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC5 compact-session UX patch applied')
print('versionName=1.0.0-rc5-compact-session')
print('UX=large global percent, neutral statuses, compact STOP/DISCONNECT button')
print('Session=one selected AI at a time; explicit disconnect before switching')
