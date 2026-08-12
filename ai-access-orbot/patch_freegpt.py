from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# Stable product identity. No more routefix package names.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 208', 'val orbotBaseVersionCode = 100')
s = s.replace('applicationId = "com.bmw1975487.aione.routefix8"', 'applicationId = "com.freegpt.access"')
s = s.replace('versionName = "0.2.8-twoscreen"', 'versionName = "1.0.0-rc1"')
s = s.replace('AI_Access_One_v0.2.8_TWO_SCREEN', 'FreeGPT')
gradle.write_text(s, encoding="utf-8")

manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:label="AI Access One"', 'android:label="FreeGPT"')
m = m.replace('android:label="AI Access One"', 'android:label="FreeGPT"')
# Replace launcher icon only if the upstream attributes are present.
m = m.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/freegpt_icon"')
m = m.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/freegpt_icon"')
manifest.write_text(m, encoding="utf-8")

# Lightweight native icon matching the approved neon-blue/purple family.
drawable = app / "src/main/res/drawable"
drawable.mkdir(parents=True, exist_ok=True)
(drawable / "freegpt_icon.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#071328" android:pathData="M8,8h92v92h-92z"/>
    <path android:fillColor="#2EDCFF" android:pathData="M54,16 L87,35 L87,73 L54,92 L21,73 L21,35 Z M54,28 L32,41 L32,67 L54,80 L76,67 L76,41 Z"/>
    <path android:fillColor="#776BFF" android:pathData="M36,58 C42,40 50,34 58,34 C72,34 80,46 80,58 C80,70 70,78 60,78 C50,78 44,72 40,66 L49,60 C52,66 56,68 61,68 C66,68 70,64 70,58 C70,51 65,45 58,45 C52,45 48,50 45,61 Z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M50,51h8v20h-8z M50,39h8v8h-8z"/>
</vector>''', encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# SharedPreferences/trial state + selected service.
if 'private static final long TRIAL_MS' not in a:
    a = a.replace('private static final String SMART_ACTION = SmartConnect.AI_ACTION;',
                  'private static final String SMART_ACTION = SmartConnect.AI_ACTION;\n    private static final long TRIAL_MS = 5L * 24L * 60L * 60L * 1000L;\n    private static final String PREFS_APP = "freegpt_product";', 1)
if 'private String selectedServiceName' not in a:
    a = a.replace('private boolean settingsMode = false;',
                  'private boolean settingsMode = false;\n    private String selectedServiceName = "ChatGPT";\n    private String selectedServiceUrl = "https://chatgpt.com/";\n    private String screen = "select";', 1)

# Initialise trial before first UI.
a = a.replace('setContentView(buildUi());', 'initTrial();\n        setContentView(buildUi());', 1)

ui_start = a.find('    private View buildUi() {')
ui_end = a.find('    private void onPower() {', ui_start)
if ui_start < 0 or ui_end < 0:
    raise SystemExit('FreeGPT UI markers not found')

new_ui = r'''    private void initTrial() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (!p.contains("first_run_ms")) p.edit().putLong("first_run_ms", System.currentTimeMillis()).apply();
        if (trialExpired() && !p.getBoolean("unlocked_forever", false)) screen = "pay";
        else if (!p.getBoolean("onboarding_done", false)) screen = "welcome";
        else screen = "select";
        AiAccessLog.i(this, "TRIAL_STATE", "remainingMs=" + trialRemainingMs() + " expired=" + trialExpired());
    }

    private long trialRemainingMs() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (p.getBoolean("unlocked_forever", false)) return Long.MAX_VALUE;
        long first = p.getLong("first_run_ms", System.currentTimeMillis());
        return Math.max(0L, TRIAL_MS - (System.currentTimeMillis() - first));
    }

    private boolean trialExpired() { return trialRemainingMs() == 0L; }

    private View buildUi() {
        if ("pay".equals(screen)) return buildPayScreen();
        if ("welcome".equals(screen)) return buildWelcomeScreen();
        if ("settings".equals(screen)) return buildSettingsScreen();
        if ("products".equals(screen)) return buildProductsScreen();
        return buildSelectScreen();
    }

    private LinearLayout baseScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(Color.rgb(2, 8, 22));
        return root;
    }

    private TextView productTitle(String t, int size) {
        TextView v = text(t, size, TEXT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private View buildWelcomeScreen() {
        settingsMode = false;
        LinearLayout root = baseScreen();
        root.addView(productTitle("FreeGPT", 38), lp(-1, -2, 0, 44, 0, 10));
        TextView hero = text("AI", 86, Color.rgb(74, 218, 255), Typeface.BOLD);
        hero.setGravity(Gravity.CENTER);
        hero.setBackground(round(Color.rgb(7, 29, 63), 34, Color.rgb(99, 99, 255), 2));
        root.addView(hero, lp(dp(250), dp(250), 0, 20, 0, 34));
        root.addView(productTitle("Нейросети. Просто и стабильно.", 24), lp(-1, -2, 0, 0, 0, 14));
        TextView desc = text("Быстрый доступ к популярным нейросетям прямо в браузере.\nНе является универсальным VPN — маршрут используется только выбранным браузером.", 14, MUTED, Typeface.NORMAL);
        desc.setGravity(Gravity.CENTER);
        root.addView(desc, lp(-1, -2, 10, 0, 10, 24));
        TextView trial = text("✦  5 дней бесплатно", 20, Color.rgb(78, 231, 255), Typeface.BOLD);
        trial.setGravity(Gravity.CENTER);
        trial.setBackground(round(CARD, 24, Color.rgb(65, 155, 255), 1));
        root.addView(trial, lp(-1, 64, 22, 0, 22, 24));
        Button go = button("ПРИСТУПИТЬ", BLUE);
        go.setTextSize(22);
        go.setOnClickListener(v -> {
            getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply();
            showScreen("select");
        });
        root.addView(go, lp(-1, 82, 0, 0, 0, 0));
        return root;
    }

    private View buildSelectScreen() {
        settingsMode = false;
        stateView = null; detailView = null; routeView = null; logView = null; powerButton = null;
        probeButton = null; shareButton = null; telegramButton = null; saveButton = null;
        LinearLayout root = baseScreen();
        root.addView(productTitle("FreeGPT", 24), lp(-1, -2, 0, 0, 0, 22));
        root.addView(productTitle("Выберите нейросеть", 32), lp(-1, -2, 0, 0, 0, 8));
        TextView sub = text("Что открыть сегодня?", 16, MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1, -2, 0, 0, 0, 24));

        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row1, lp(-1, 0, 0, 0, 0, 12)); row1.getLayoutParams().height = dp(210);
        addAiCard(row1, "ChatGPT", "OpenAI", "https://chatgpt.com/", Color.rgb(20, 111, 219));
        addAiCard(row1, "Claude", "Anthropic", "https://claude.ai/", Color.rgb(90, 72, 220));
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row2, lp(-1, 0, 0, 0, 0, 16)); row2.getLayoutParams().height = dp(210);
        addAiCard(row2, "Gemini", "Google", "https://gemini.google.com/", Color.rgb(25, 137, 219));
        addAiCard(row2, "Grok", "xAI", "https://grok.com/", Color.rgb(82, 72, 211));

        Button settings = button("⚙  НАСТРОЙКИ", CARD);
        settings.setOnClickListener(v -> showScreen("settings"));
        root.addView(settings, lp(-1, 58, 28, 0, 28, 10));
        Button products = button("ДРУГИЕ ПРИЛОЖЕНИЯ", CARD2);
        products.setOnClickListener(v -> showScreen("products"));
        root.addView(products, lp(-1, 52, 50, 0, 50, 0));
        return root;
    }

    private void addAiCard(LinearLayout row, String name, String vendor, String url, int accent) {
        Button b = button(name + "\n" + vendor, CARD);
        b.setTextSize(18);
        b.setBackground(round(CARD, 22, accent, 2));
        b.setOnClickListener(v -> startSelectedService(name, url));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(6), 0, dp(6), 0);
        row.addView(b, lp);
    }

    private void startSelectedService(String name, String url) {
        if (trialExpired() && !getSharedPreferences(PREFS_APP, MODE_PRIVATE).getBoolean("unlocked_forever", false)) {
            showScreen("pay"); return;
        }
        selectedServiceName = name;
        selectedServiceUrl = url;
        AiAccessLog.i(this, "AI_SERVICE_SELECTED", "name=" + name + " url=" + url);
        if ("READY".equals(state)) {
            launchChatGpt();
            return;
        }
        if (!"OFF".equals(state) && !"ERROR".equals(state)) {
            Toast.makeText(this, "Подключение уже выполняется", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Подключаю " + name + "…", Toast.LENGTH_SHORT).show();
        onPower();
    }

    private View buildSettingsScreen() {
        settingsMode = true;
        ScrollView outer = new ScrollView(this); outer.setFillViewport(true); outer.setBackgroundColor(Color.rgb(2, 8, 22));
        LinearLayout root = baseScreen(); outer.addView(root, new ScrollView.LayoutParams(-1, -1));
        Button back = button("←  НАЗАД", CARD2); back.setOnClickListener(v -> showScreen("select"));
        root.addView(back, lp(-1, 52, 0, 0, 0, 18));
        root.addView(productTitle("Настройки", 34), lp(-1, -2, 0, 0, 0, 20));

        LinearLayout statusCard = new LinearLayout(this); statusCard.setOrientation(LinearLayout.VERTICAL); statusCard.setPadding(dp(18),dp(18),dp(18),dp(18));
        statusCard.setBackground(round(CARD, 20, Color.rgb(67, 120, 255), 1));
        root.addView(statusCard, lp(-1, -2, 0, 0, 0, 14));
        stateView = text("●  ГОТОВО", 20, GREEN, Typeface.BOLD); statusCard.addView(stateView);
        detailView = text(detail, 13, MUTED, Typeface.NORMAL); detailView.setPadding(0,dp(8),0,0); statusCard.addView(detailView);
        TextView browser = text("Браузер: " + (targetLabel == null || targetLabel.isEmpty() ? "автоматически" : targetLabel) + "\nМаршрут: автоматически\nПодключение: " + AiAccessPrefs.selectedTransport(), 13, TEXT, Typeface.NORMAL);
        browser.setPadding(0,dp(10),0,0); statusCard.addView(browser);

        probeButton = button("ПРОВЕРИТЬ МАРШРУТ", CARD); probeButton.setOnClickListener(v -> startProbe(false)); root.addView(probeButton, lp(-1,54,0,0,0,8));
        telegramButton = button("ОТПРАВИТЬ ЛОГ В TELEGRAM", CARD); telegramButton.setOnClickListener(v -> shareTelegram()); root.addView(telegramButton, lp(-1,54,0,0,0,8));
        shareButton = button("ОТПРАВИТЬ ЛОГ В MAX", CARD); shareButton.setOnClickListener(v -> shareLog()); root.addView(shareButton, lp(-1,54,0,0,0,8));
        saveButton = button("СОХРАНИТЬ ZIP", CARD); saveButton.setOnClickListener(v -> saveToDownloads()); root.addView(saveButton, lp(-1,54,0,0,0,8));
        Button products = button("ДРУГИЕ ПРИЛОЖЕНИЯ", CARD2); products.setOnClickListener(v -> showScreen("products")); root.addView(products, lp(-1,54,0,0,0,14));
        if (!"OFF".equals(state)) { Button stop = button("ОСТАНОВИТЬ СОЕДИНЕНИЕ", CARD2); stop.setOnClickListener(v -> stopRoute()); root.addView(stop, lp(-1,54,0,0,0,14)); }

        routeView = text("Маршрут: ожидание.", 12, TEXT, Typeface.NORMAL); routeView.setPadding(dp(12),dp(12),dp(12),dp(12)); routeView.setBackground(round(CARD2,14,Color.rgb(28,62,98),1)); root.addView(routeView, lp(-1,-2,0,0,0,14));
        logView = text("", 10, Color.rgb(184,216,244), Typeface.NORMAL); logView.setTypeface(Typeface.MONOSPACE); logView.setPadding(dp(10),dp(10),dp(10),dp(10));
        ScrollView ls = new ScrollView(this); ls.setBackground(round(Color.rgb(2,10,23),14,Color.rgb(28,62,98),1)); ls.addView(logView); root.addView(ls, lp(-1,320,0,0,0,0));
        return outer;
    }

    private View buildProductsScreen() {
        settingsMode = false;
        LinearLayout root = baseScreen();
        Button back = button("←  НАЗАД", CARD2); back.setOnClickListener(v -> showScreen("select")); root.addView(back, lp(-1,52,0,0,0,24));
        root.addView(productTitle("Другие приложения", 34), lp(-1,-2,0,0,0,8));
        TextView sub = text("Один принцип. Разные сервисы.", 17, MUTED, Typeface.NORMAL); sub.setGravity(Gravity.CENTER); root.addView(sub, lp(-1,-2,0,0,0,26));
        Button mess = button("МЕССЕНДЖЕРЫ\nСтабильная работа сервисов общения\n\nПОДРОБНЕЕ", CARD); mess.setTextSize(17); mess.setOnClickListener(v -> Toast.makeText(this,"Отдельное приложение для мессенджеров — скоро",Toast.LENGTH_LONG).show()); root.addView(mess, lp(-1,220,0,0,0,20));
        Button video = button("ВИДЕОХОСТИНГИ\nУдобный доступ к видеосервисам\n\nПОДРОБНЕЕ", CARD); video.setTextSize(17); video.setOnClickListener(v -> Toast.makeText(this,"Отдельное приложение для видеохостингов — скоро",Toast.LENGTH_LONG).show()); root.addView(video, lp(-1,220,0,0,0,0));
        return root;
    }

    private View buildPayScreen() {
        settingsMode = false;
        LinearLayout root = baseScreen();
        root.addView(productTitle("FreeGPT", 24), lp(-1,-2,0,24,0,34));
        TextView orb = text("AI", 72, Color.rgb(91,220,255), Typeface.BOLD); orb.setGravity(Gravity.CENTER); orb.setBackground(round(CARD,34,Color.rgb(92,90,255),2)); root.addView(orb, lp(dp(220),dp(220),0,0,0,30));
        root.addView(productTitle("Пробный период завершён", 32), lp(-1,-2,0,0,0,12));
        TextView thanks = text("Спасибо, что попробовали FreeGPT.", 16, MUTED, Typeface.NORMAL); thanks.setGravity(Gravity.CENTER); root.addView(thanks, lp(-1,-2,0,0,0,22));
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER); card.setPadding(dp(18),dp(18),dp(18),dp(18)); card.setBackground(round(CARD,24,Color.rgb(95,90,255),2)); root.addView(card, lp(-1,-2,12,0,12,20));
        TextView access = productTitle("Вечный доступ", 22); card.addView(access); TextView price = productTitle("300 ₽", 64); price.setTextColor(Color.rgb(86,220,255)); card.addView(price); TextView terms = text("Один платёж\nБез подписки\nБез ежемесячных списаний", 16, TEXT, Typeface.NORMAL); terms.setGravity(Gravity.CENTER); card.addView(terms);
        Button buy = button("КУПИТЬ НАВСЕГДА", BLUE); buy.setTextSize(20); buy.setOnClickListener(v -> { AiAccessLog.i(this,"PAYMENT_REQUEST","amount=300 RUB lifetime=true provider=not_configured"); Toast.makeText(this,"Платёжный модуль будет подключён отдельно",Toast.LENGTH_LONG).show(); }); root.addView(buy, lp(-1,76,0,0,0,12));
        Button later = button("ПОЗЖЕ", CARD2); later.setOnClickListener(v -> finish()); root.addView(later, lp(-1,54,48,0,48,0));
        return root;
    }

    private void showScreen(String name) {
        screen = name;
        setContentView(buildUi());
        render();
        AiAccessLog.i(this, "UI_SCREEN", name);
    }

    @Override public void onBackPressed() {
        if ("settings".equals(screen) || "products".equals(screen)) { showScreen("select"); return; }
        if ("select".equals(screen) && !trialExpired()) { super.onBackPressed(); return; }
        super.onBackPressed();
    }

'''
a = a[:ui_start] + new_ui + a[ui_end:]

# Open whichever official AI website the user selected. Existing route acceptance remains
# conservative and is refined by physical logs for each provider later.
launch_start = a.find('    private void launchChatGpt() {')
launch_end = a.find('    private Uri createZipUri(', launch_start)
if launch_start < 0 or launch_end < 0:
    raise SystemExit('launch method markers not found')
new_launch = r'''    private void launchChatGpt() {
        try {
            if (targetPackage == null || targetPackage.isEmpty()) {
                AiAccessLog.w(this, "AI_WEB_OPEN_FAIL", "browser target is empty service=" + selectedServiceName);
                return;
            }
            String url = selectedServiceUrl == null || selectedServiceUrl.isEmpty() ? "https://chatgpt.com/" : selectedServiceUrl;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            open.addCategory(Intent.CATEGORY_BROWSABLE);
            open.setPackage(targetPackage);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (open.resolveActivity(getPackageManager()) == null) {
                AiAccessLog.w(this, "AI_WEB_OPEN_FAIL", "browser cannot handle URL package=" + targetPackage + " url=" + url);
                return;
            }
            AiAccessLog.i(this, "AI_WEB_OPEN", "service=" + selectedServiceName + " url=" + url + " browserPackage=" + targetPackage);
            startActivity(open);
        } catch (Throwable t) {
            AiAccessLog.e(this, "AI_WEB_OPEN_FAIL", String.valueOf(t.getMessage()), t);
        }
    }

'''
a = a[:launch_start] + new_launch + a[launch_end:]

# Render is valid even when no technical views exist on consumer screens.
render_start = a.find('    private void render() {')
render_end = a.find('    private TextView text(', render_start)
if render_start < 0 or render_end < 0:
    raise SystemExit('render markers not found')
new_render = r'''    private void render() {
        AiAccessLog.setState(state, detail);
        if (detailView != null) detailView.setText(detail);
        if (stateView != null) {
            if ("READY".equals(state)) { stateView.setText("●  ГОТОВО"); stateView.setTextColor(GREEN); }
            else if ("ERROR".equals(state)) { stateView.setText("●  ОШИБКА"); stateView.setTextColor(RED); }
            else if ("OFF".equals(state)) { stateView.setText("●  ВЫКЛ"); stateView.setTextColor(MUTED); }
            else { stateView.setText("●  ПОДКЛЮЧЕНИЕ"); stateView.setTextColor(AMBER); }
        }
        if (logView != null) logView.setText(AiAccessLog.tail(this, 18000));
    }

'''
a = a[:render_start] + new_render + a[render_end:]

# Product/version markers in logs.
a = a.replace('0.2.8-twoscreen', '1.0.0-rc1')
a = a.replace('AI ACCESS ONE', 'FreeGPT')
activity.write_text(a, encoding="utf-8")

log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8").replace('0.2.8-twoscreen', '1.0.0-rc1')
log_file.write_text(log_text, encoding="utf-8")

print('FreeGPT patch applied')
print('applicationId=com.freegpt.access')
print('version=1.0.0-rc1')
print('screens=welcome,select,settings,products,pay')
print('services=ChatGPT,Claude,Gemini,Grok')
print('trial=5 days')
print('payment=UI only, provider not configured')