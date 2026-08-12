from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC3 identity: same stable applicationId; version only.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 101' not in s:
    raise SystemExit('RC2 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 101', 'val orbotBaseVersionCode = 102', 1)
if 'versionName = "1.0.0-rc2-userscreens"' not in s:
    raise SystemExit('RC2 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc2-userscreens"', 'versionName = "1.0.0-rc3-live-status"', 1)
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Progress widget for a visible connection panel over the unchanged user artwork.
if 'import android.widget.ProgressBar;' not in a:
    a = a.replace('import android.widget.ImageView;', 'import android.widget.ImageView;\nimport android.widget.ProgressBar;', 1)

field_marker = '    private String screen = "select";'
fields = '''    private String screen = "select";
    private LinearLayout liveStatusPanel;
    private TextView liveStatusTitle;
    private TextView liveStatusDetail;
    private TextView liveStatusPercent;
    private ProgressBar liveStatusProgress;
    private Button liveStatusAction;
    private int liveBootstrapPercent = 0;'''
if field_marker not in a:
    raise SystemExit('screen field marker not found')
a = a.replace(field_marker, fields, 1)

# Capture the real bootstrap percentage that already arrives in Orbot broadcasts.
percent_marker = 'detail = "Tor bootstrap " + percent + "% · " + currentExit().toUpperCase();'
percent_repl = '''detail = "Tor bootstrap " + percent + "% · " + currentExit().toUpperCase();
                    try { liveBootstrapPercent = Integer.parseInt(percent); } catch (Throwable ignored) {}'''
if percent_marker not in a:
    raise SystemExit('bootstrap detail marker not found')
a = a.replace(percent_marker, percent_repl, 1)

# A transport switch is a new attempt. Reset the visible percentage until the next Tor notice.
smart_log_marker = 'AiAccessLog.i(AiAccessActivity.this, "SMARTCONNECT_" + String.valueOf(event), String.valueOf(smartDetail));'
smart_log_repl = '''AiAccessLog.i(AiAccessActivity.this, "SMARTCONNECT_" + String.valueOf(event), String.valueOf(smartDetail));
                if ("TRANSPORT_SWITCH".equals(event) || "TRANSPORT_START".equals(event)) liveBootstrapPercent = 0;'''
if smart_log_marker not in a:
    raise SystemExit('SmartConnect event marker not found')
a = a.replace(smart_log_marker, smart_log_repl, 1)

# Reset progress for a fresh launch and after stop.
start_marker = '''            torBootstrapped = false;
            routeProbeArmed = false;
            state = "CONNECTING";'''
start_repl = '''            torBootstrapped = false;
            routeProbeArmed = false;
            liveBootstrapPercent = 0;
            state = "CONNECTING";'''
if start_marker not in a:
    raise SystemExit('start reset marker not found')
a = a.replace(start_marker, start_repl, 1)

stop_marker = '''        socksPort = -1;
        torBootstrapped = false;
        routeProbeArmed = false;'''
stop_repl = '''        socksPort = -1;
        liveBootstrapPercent = 0;
        torBootstrapped = false;
        routeProbeArmed = false;'''
if stop_marker not in a:
    raise SystemExit('stop reset marker not found')
a = a.replace(stop_marker, stop_repl, 1)

# Clear the previous screen's dynamic view references whenever the artwork screen is rebuilt.
clear_marker = '''        telegramButton = null;
        saveButton = null;

        FrameLayout root = new FrameLayout(this);'''
clear_repl = '''        telegramButton = null;
        saveButton = null;
        liveStatusPanel = null;
        liveStatusTitle = null;
        liveStatusDetail = null;
        liveStatusPercent = null;
        liveStatusProgress = null;
        liveStatusAction = null;

        FrameLayout root = new FrameLayout(this);'''
if clear_marker not in a:
    raise SystemExit('artwork clear marker not found')
a = a.replace(clear_marker, clear_repl, 1)

# Attach the live panel only to the operational screens. The five WEBP files themselves remain byte-identical.
art_add_marker = '        root.addView(art, new FrameLayout.LayoutParams(-1, -1));\n\n        root.setOnTouchListener((v, ev) -> {'
art_add_repl = '''        root.addView(art, new FrameLayout.LayoutParams(-1, -1));

        if ("select".equals(screenId) || "settings".equals(screenId)) {
            attachLiveStatusPanel(root);
        }

        root.setOnTouchListener((v, ev) -> {'''
if art_add_marker not in a:
    raise SystemExit('artwork add marker not found')
a = a.replace(art_add_marker, art_add_repl, 1)

# Insert the live status UI before the hitbox helper.
hit_marker = '    private boolean hit(float x, float y, float l, float t, float r, float b) {'
live_methods = r'''    private void attachLiveStatusPanel(FrameLayout root) {
        liveStatusPanel = new LinearLayout(this);
        liveStatusPanel.setOrientation(LinearLayout.VERTICAL);
        liveStatusPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        liveStatusPanel.setPadding(dp(18), dp(15), dp(18), dp(14));
        liveStatusPanel.setBackground(round(Color.argb(242, 3, 15, 35), 22, Color.rgb(56, 192, 255), 1));
        liveStatusPanel.setElevation(dp(12));

        liveStatusTitle = text("ПОДКЛЮЧЕНИЕ", 18, TEXT, Typeface.BOLD);
        liveStatusTitle.setGravity(Gravity.CENTER);
        liveStatusPanel.addView(liveStatusTitle, new LinearLayout.LayoutParams(-1, -2));

        liveStatusDetail = text("", 12, Color.rgb(189, 218, 244), Typeface.NORMAL);
        liveStatusDetail.setGravity(Gravity.CENTER);
        liveStatusDetail.setPadding(0, dp(6), 0, dp(8));
        liveStatusPanel.addView(liveStatusDetail, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        liveStatusPanel.addView(progressRow, new LinearLayout.LayoutParams(-1, dp(34)));

        liveStatusProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        liveStatusProgress.setMax(100);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(0, dp(10), 1f);
        progressLp.setMargins(0, 0, dp(12), 0);
        progressRow.addView(liveStatusProgress, progressLp);

        liveStatusPercent = text("0%", 13, Color.rgb(89, 221, 255), Typeface.BOLD);
        liveStatusPercent.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        progressRow.addView(liveStatusPercent, new LinearLayout.LayoutParams(dp(54), -1));

        liveStatusAction = button("ОСТАНОВИТЬ", CARD2);
        liveStatusAction.setTextSize(13);
        liveStatusAction.setOnClickListener(v -> {
            if ("ERROR".equals(state)) startSelectedService(selectedServiceName, selectedServiceUrl);
            else stopRoute();
        });
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(48));
        actionLp.setMargins(0, dp(7), 0, 0);
        liveStatusPanel.addView(liveStatusAction, actionLp);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        panelLp.setMargins(dp(18), dp(18), dp(18), dp(26));
        root.addView(liveStatusPanel, panelLp);
        liveStatusPanel.setVisibility(View.GONE);
    }

    private void renderLiveStatus() {
        if (liveStatusPanel == null) return;

        boolean visible = !"OFF".equals(state);
        liveStatusPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;

        String transport;
        try { transport = AiAccessPrefs.selectedTransport(); }
        catch (Throwable ignored) { transport = "auto"; }
        String service = selectedServiceName == null || selectedServiceName.isEmpty() ? "нейросеть" : selectedServiceName;

        if ("ERROR".equals(state)) {
            liveStatusTitle.setText("НЕ УДАЛОСЬ ПОДКЛЮЧИТЬ " + service.toUpperCase());
            liveStatusTitle.setTextColor(RED);
            liveStatusProgress.setIndeterminate(false);
            liveStatusProgress.setProgress(Math.max(0, Math.min(100, liveBootstrapPercent)));
            liveStatusPercent.setText(liveBootstrapPercent > 0 ? liveBootstrapPercent + "%" : "—");
            liveStatusAction.setText("ПОВТОРИТЬ");
        } else if ("READY".equals(state)) {
            liveStatusTitle.setText("ГОТОВО · ОТКРЫВАЮ " + service.toUpperCase());
            liveStatusTitle.setTextColor(GREEN);
            liveStatusProgress.setIndeterminate(false);
            liveStatusProgress.setProgress(100);
            liveStatusPercent.setText("100%");
            liveStatusAction.setText("ВЫКЛЮЧИТЬ");
        } else if ("TOR_ON".equals(state) || probing) {
            liveStatusTitle.setText("ПРОВЕРЯЮ ДОСТУП К " + service.toUpperCase());
            liveStatusTitle.setTextColor(Color.rgb(89, 221, 255));
            liveStatusProgress.setIndeterminate(true);
            liveStatusPercent.setText("100%");
            liveStatusAction.setText("ОСТАНОВИТЬ");
        } else if ("PREPARING".equals(state)) {
            liveStatusTitle.setText("ПОДГОТАВЛИВАЮ " + service.toUpperCase());
            liveStatusTitle.setTextColor(AMBER);
            liveStatusProgress.setIndeterminate(true);
            liveStatusPercent.setText("…");
            liveStatusAction.setText("ОТМЕНА");
        } else {
            liveStatusTitle.setText("ПОДКЛЮЧАЮ " + service.toUpperCase());
            liveStatusTitle.setTextColor(AMBER);
            if (liveBootstrapPercent > 0) {
                liveStatusProgress.setIndeterminate(false);
                liveStatusProgress.setProgress(Math.max(0, Math.min(100, liveBootstrapPercent)));
                liveStatusPercent.setText(liveBootstrapPercent + "%");
            } else {
                liveStatusProgress.setIndeterminate(true);
                liveStatusPercent.setText("…");
            }
            liveStatusAction.setText("ОСТАНОВИТЬ");
        }

        String d = detail == null ? "" : detail;
        liveStatusDetail.setText(d + "\nТранспорт: " + transport + " · браузер: " +
                (targetLabel == null || targetLabel.isEmpty() ? "автоматически" : targetLabel));
    }

'''
if hit_marker not in a:
    raise SystemExit('hit helper marker not found')
a = a.replace(hit_marker, live_methods + hit_marker, 1)

# The normal render loop now updates the visible panel every 750 ms and after every network event.
render_marker = '        if (logView != null) logView.setText(AiAccessLog.tail(this, 18000));\n    }'
render_repl = '''        if (logView != null) logView.setText(AiAccessLog.tail(this, 18000));
        renderLiveStatus();
    }'''
if render_marker not in a:
    raise SystemExit('render tail marker not found')
a = a.replace(render_marker, render_repl, 1)

activity.write_text(a, encoding="utf-8")

# Update diagnostic version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc2-userscreens' not in lt:
    raise SystemExit('RC2 log version marker not found')
lt = lt.replace('1.0.0-rc2-userscreens', '1.0.0-rc3-live-status')
log_file.write_text(lt, encoding="utf-8")

# Stability correction from the physical RC2 log:
# descriptor download at 50-58% was being killed after only 50 seconds. Keep a valid
# transport longer, and let AutoConf's recommendation run first on a fresh install.
smart = app / "src/main/java/org/torproject/android/service/circumvention/SmartConnect.kt"
t = smart.read_text(encoding="utf-8")
old_pref = 'Prefs.transport = remembered ?: Transport.SNOWFLAKE'
new_pref = 'Prefs.transport = remembered ?: autoSuggested ?: Transport.SNOWFLAKE'
if old_pref not in t:
    raise SystemExit('preferred transport marker not found')
t = t.replace(old_pref, new_pref, 1)
old_stall = '''    private fun stallSeconds(): Int = when {
        progress < 10 -> 20
        progress < 25 -> 30
        progress < 75 -> 50
        else -> 35
    }'''
new_stall = '''    private fun stallSeconds(): Int = when {
        progress < 10 -> 25
        progress < 25 -> 45
        progress < 50 -> 90
        progress < 75 -> 150
        else -> 60
    }'''
if old_stall not in t:
    raise SystemExit('stall timeout block not found')
t = t.replace(old_stall, new_stall, 1)
smart.write_text(t, encoding="utf-8")

print('FreeGPT RC3 live-status patch applied')
print('versionName=1.0.0-rc3-live-status')
print('UI=exact user WEBPs + visible runtime connection panel')
print('SmartConnect=AutoConf-first, 150s descriptor-stage watchdog')
