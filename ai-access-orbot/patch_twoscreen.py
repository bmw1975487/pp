from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# v0.2.8 identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 207', 'val orbotBaseVersionCode = 208')
s = s.replace('applicationId = "com.bmw1975487.aione.routefix7"', 'applicationId = "com.bmw1975487.aione.routefix8"')
s = s.replace('versionName = "0.2.7-browseronly"', 'versionName = "0.2.8-twoscreen"')
s = s.replace('AI_Access_One_v0.2.7_BROWSER_ONLY', 'AI_Access_One_v0.2.8_TWO_SCREEN')
gradle.write_text(s, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Track which of the two UI screens is currently visible.
if 'private boolean settingsMode = false;' not in a:
    a = a.replace('private boolean routeProbeArmed = false;', 'private boolean routeProbeArmed = false;\n    private boolean settingsMode = false;', 1)

# Replace the old diagnostic-heavy single screen with two screens:
# 1) main = status + one large ChatGPT button + small settings icon
# 2) settings = diagnostics, route probe, ZIP sharing/saving and live log.
ui_start = a.find('    private View buildUi() {')
ui_end = a.find('    private void onPower() {', ui_start)
if ui_start < 0 or ui_end < 0:
    raise SystemExit('buildUi/onPower markers not found')

new_ui = r'''    private View buildUi() {
        return buildMainScreen();
    }

    private View buildMainScreen() {
        settingsMode = false;
        routeView = null;
        logView = null;
        probeButton = null;
        shareButton = null;
        telegramButton = null;
        saveButton = null;
        powerButton = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(28));
        root.setBackgroundColor(NAVY);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        top.addView(titleBox, titleLp);

        TextView title = text("AI ACCESS ONE", 27, TEXT, Typeface.BOLD);
        titleBox.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = text("ChatGPT в браузере · один запуск", 13, MUTED, Typeface.NORMAL);
        sub.setPadding(0, dp(3), 0, 0);
        titleBox.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        Button settings = button("⚙", CARD2);
        settings.setTextSize(22);
        settings.setOnClickListener(v -> showSettingsScreen());
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        gearLp.setMargins(dp(10), 0, 0, 0);
        top.addView(settings, gearLp);

        View spacer1 = new View(this);
        root.addView(spacer1, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setPadding(dp(18), dp(22), dp(18), dp(22));
        statusCard.setBackground(round(CARD, 20, Color.rgb(31, 70, 108), 1));
        root.addView(statusCard, lp(-1, -2, 0, 0, 0, 20));

        stateView = text("●  ВЫКЛ", 24, MUTED, Typeface.BOLD);
        stateView.setGravity(Gravity.CENTER);
        statusCard.addView(stateView, new LinearLayout.LayoutParams(-1, -2));
        detailView = text(detail, 13, MUTED, Typeface.NORMAL);
        detailView.setGravity(Gravity.CENTER);
        detailView.setPadding(0, dp(8), 0, 0);
        statusCard.addView(detailView, new LinearLayout.LayoutParams(-1, -2));

        powerButton = button("ВКЛЮЧИТЬ CHATGPT", BLUE);
        powerButton.setTextSize(21);
        powerButton.setOnClickListener(v -> onPower());
        root.addView(powerButton, lp(-1, 94, 0, 0, 0, 16));

        TextView hint = text("Нажмите один раз. После готовности маршрут проверится автоматически, затем Chrome откроет официальный chatgpt.com.", 12, MUTED, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, lp(-1, -2, 8, 0, 8, 0));

        View spacer2 = new View(this);
        root.addView(spacer2, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView foot = text("Диагностика и ZIP-лог — в настройках", 11, MUTED, Typeface.NORMAL);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private View buildSettingsScreen() {
        settingsMode = true;
        powerButton = null;

        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(NAVY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(26));
        outer.addView(root, new ScrollView.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, lp(-1, -2, 0, 0, 0, 18));

        Button back = button("‹", CARD2);
        back.setTextSize(28);
        back.setOnClickListener(v -> showMainScreen());
        top.addView(back, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView title = text("НАСТРОЙКИ", 24, TEXT, Typeface.BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.setBackground(round(CARD, 18, Color.rgb(31, 70, 108), 1));
        root.addView(statusCard, lp(-1, -2, 0, 0, 0, 14));

        stateView = text("●  ВЫКЛ", 20, MUTED, Typeface.BOLD);
        stateView.setGravity(Gravity.CENTER);
        statusCard.addView(stateView, new LinearLayout.LayoutParams(-1, -2));
        detailView = text(detail, 12, MUTED, Typeface.NORMAL);
        detailView.setGravity(Gravity.CENTER);
        detailView.setPadding(0, dp(6), 0, 0);
        statusCard.addView(detailView, new LinearLayout.LayoutParams(-1, -2));

        TextView browser = text("Браузер: " + (targetLabel == null || targetLabel.isEmpty() ? "определяется автоматически" : targetLabel) +
                (targetPackage == null || targetPackage.isEmpty() ? "" : "  ·  " + targetPackage), 12, MUTED, Typeface.NORMAL);
        root.addView(browser, lp(-1, -2, 2, 0, 2, 12));

        probeButton = button("ПРОВЕРИТЬ МАРШРУТ", CARD);
        probeButton.setOnClickListener(v -> startProbe(false));
        root.addView(probeButton, lp(-1, 54, 0, 0, 0, 8));

        telegramButton = button("ОТПРАВИТЬ ZIP В TELEGRAM", CARD);
        telegramButton.setOnClickListener(v -> shareTelegram());
        root.addView(telegramButton, lp(-1, 54, 0, 0, 0, 8));

        shareButton = button("ОТПРАВИТЬ ZIP В MAX", CARD);
        shareButton.setOnClickListener(v -> shareLog());
        root.addView(shareButton, lp(-1, 54, 0, 0, 0, 8));

        saveButton = button("СОХРАНИТЬ ZIP В DOWNLOADS", CARD);
        saveButton.setOnClickListener(v -> saveToDownloads());
        root.addView(saveButton, lp(-1, 54, 0, 0, 0, 16));

        TextView routeLabel = text("МАРШРУТ", 12, MUTED, Typeface.BOLD);
        root.addView(routeLabel, lp(-1, -2, 0, 0, 0, 7));
        routeView = text("Ожидание запуска.", 12, TEXT, Typeface.NORMAL);
        routeView.setPadding(dp(12), dp(12), dp(12), dp(12));
        routeView.setBackground(round(CARD2, 14, Color.rgb(28, 62, 98), 1));
        root.addView(routeView, lp(-1, -2, 0, 0, 0, 14));

        TextView logLabel = text("ЖИВОЙ ЛОГ", 12, MUTED, Typeface.BOLD);
        root.addView(logLabel, lp(-1, -2, 0, 0, 0, 7));
        ScrollView logScroll = new ScrollView(this);
        logScroll.setBackground(round(Color.rgb(2, 10, 23), 14, Color.rgb(28, 62, 98), 1));
        logView = text("", 10, Color.rgb(184, 216, 244), Typeface.NORMAL);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(11), dp(11), dp(11), dp(11));
        logScroll.addView(logView, new ScrollView.LayoutParams(-1, -2));
        root.addView(logScroll, lp(-1, 280, 0, 0, 0, 12));

        TextView note = text("Служебный экран. На основном экране для обычной работы достаточно одной кнопки.", 11, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(-1, -2, 4, 0, 4, 0));
        return outer;
    }

    private void showMainScreen() {
        setContentView(buildMainScreen());
        render();
        AiAccessLog.i(this, "UI_SCREEN", "main");
    }

    private void showSettingsScreen() {
        setContentView(buildSettingsScreen());
        render();
        AiAccessLog.i(this, "UI_SCREEN", "settings");
    }

    @Override public void onBackPressed() {
        if (settingsMode) {
            showMainScreen();
            return;
        }
        super.onBackPressed();
    }

'''
a = a[:ui_start] + new_ui + a[ui_end:]

# Automated probe may run while the main screen is visible, where diagnostic views do not exist.
a = a.replace('        probeButton.setEnabled(false);\n        probeButton.setText("ПРОВЕРЯЮ…");',
              '        if (probeButton != null) {\n            probeButton.setEnabled(false);\n            probeButton.setText("ПРОВЕРЯЮ…");\n        }')
a = a.replace('                probeButton.setEnabled(true);\n                probeButton.setText("ПРОВЕРИТЬ МАРШРУТ");',
              '                if (probeButton != null) {\n                    probeButton.setEnabled(true);\n                    probeButton.setText("ПРОВЕРИТЬ МАРШРУТ");\n                }')
a = a.replace('                routeView.setText(summary);', '                if (routeView != null) routeView.setText(summary);')

# Render must work on both screens; only the main screen has powerButton.
render_start = a.find('    private void render() {')
render_end = a.find('    private TextView text(', render_start)
if render_start < 0 or render_end < 0:
    raise SystemExit('render method markers not found')
new_render = r'''    private void render() {
        AiAccessLog.setState(state, detail);
        if (detailView != null) detailView.setText(detail);

        if (stateView != null) {
            if ("READY".equals(state)) {
                stateView.setText("●  CHATGPT ГОТОВ");
                stateView.setTextColor(GREEN);
            } else if ("ERROR".equals(state)) {
                stateView.setText("●  ОШИБКА");
                stateView.setTextColor(RED);
            } else if ("OFF".equals(state)) {
                stateView.setText("●  ВЫКЛ");
                stateView.setTextColor(MUTED);
            } else {
                stateView.setText("●  ПОДКЛЮЧЕНИЕ");
                stateView.setTextColor(AMBER);
            }
        }

        if (powerButton != null) {
            if ("READY".equals(state)) powerButton.setText("ВЫКЛЮЧИТЬ");
            else if ("ERROR".equals(state)) powerButton.setText("ПОВТОРИТЬ");
            else if ("OFF".equals(state)) powerButton.setText("ВКЛЮЧИТЬ CHATGPT");
            else powerButton.setText("ОСТАНОВИТЬ");
        }

        if (logView != null) logView.setText(AiAccessLog.tail(this, 18000));
    }

'''
a = a[:render_start] + new_render + a[render_end:]
activity.write_text(a, encoding="utf-8")

# Prefer the transport proven by the physical v0.2.7 log. Remember any successful
# transport for later launches of the same installed build. AutoConf's suggestion remains
# the first fallback, so the app still adapts when Snowflake is unavailable.
smart = app / "src/main/java/org/torproject/android/service/circumvention/SmartConnect.kt"
t = smart.read_text(encoding="utf-8")
if 'private var autoSuggested: Transport? = null' not in t:
    t = t.replace('private val attempted = LinkedHashSet<Transport>()',
                  'private val attempted = LinkedHashSet<Transport>()\n    private var autoSuggested: Transport? = null', 1)

t = t.replace('        attempted.clear()\n        stopConnectionGuard()',
              '        attempted.clear()\n        autoSuggested = null\n        stopConnectionGuard()', 1)
old_select = '''            conf?.second?.let { if (it.isNotEmpty()) Prefs.bridgesList = it }
            Prefs.transport = conf?.first ?: Transport.SNOWFLAKE
            attempted.add(Prefs.transport)
'''
new_select = '''            conf?.second?.let { if (it.isNotEmpty()) Prefs.bridgesList = it }
            autoSuggested = conf?.first
            val remembered = loadLastGoodTransport(context)
            // Physical device log 2026-08-12 proved Snowflake on this route; use it as
            // the first-run default, then remember whichever transport actually succeeds.
            Prefs.transport = remembered ?: Transport.SNOWFLAKE
            attempted.add(Prefs.transport)
            notify("TRANSPORT_PREFERRED", "selected=${Prefs.transport.id} remembered=${remembered?.id ?: "none"} auto=${autoSuggested?.id ?: "none"}")
'''
if old_select not in t:
    raise SystemExit('SmartConnect selection block not found')
t = t.replace(old_select, new_select, 1)

t = t.replace('                        notify("BOOTSTRAP_COMPLETE", "transport=${Prefs.transport.id}")',
              '                        saveLastGoodTransport(context, Prefs.transport)\n                        notify("TRANSPORT_REMEMBERED", "transport=${Prefs.transport.id}")\n                        notify("BOOTSTRAP_COMPLETE", "transport=${Prefs.transport.id}")', 1)

next_marker = '    private fun nextTransport(): Transport? {\n'
helpers = r'''    private fun loadLastGoodTransport(context: Context): Transport? {
        return try {
            val id = context.getSharedPreferences("ai_access_route", Context.MODE_PRIVATE)
                .getString("last_good_transport", null) ?: return null
            val transport = Transport.fromId(id)
            if (transport == Transport.NONE) null else transport
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveLastGoodTransport(context: Context, transport: Transport) {
        try {
            context.getSharedPreferences("ai_access_route", Context.MODE_PRIVATE)
                .edit().putString("last_good_transport", transport.id).apply()
        } catch (_: Throwable) {}
    }

'''
if next_marker not in t:
    raise SystemExit('SmartConnect nextTransport marker not found')
t = t.replace(next_marker, helpers + next_marker, 1)

t = t.replace('    private fun nextTransport(): Transport? {\n        for (candidate in fallbackOrder) {',
              '    private fun nextTransport(): Transport? {\n        val suggested = autoSuggested\n        if (suggested != null && !attempted.contains(suggested) && !(suggested == Transport.CUSTOM && Prefs.bridgesList.isEmpty())) return suggested\n        for (candidate in fallbackOrder) {', 1)
smart.write_text(t, encoding="utf-8")

# Coherent ZIP/device version.
log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8").replace('0.2.7-browseronly', '0.2.8-twoscreen')
log_file.write_text(log_text, encoding="utf-8")

print('AI Access v0.2.8 TwoScreen patch applied')
print('ApplicationId: com.bmw1975487.aione.routefix8')
print('Main UI: one large ChatGPT button + settings icon')
print('Settings UI: diagnostics / route probe / Telegram / MAX / Downloads / live log')
print('Browser target: unchanged')
print('Preferred first transport: Snowflake; successful transport remembered')
