from pathlib import Path
import base64
import io
import sys
import zipfile

root = Path(sys.argv[1]).resolve()
assets_dir = Path(sys.argv[2]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# ---- Product version ----
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 100', 'val orbotBaseVersionCode = 101')
s = s.replace('versionName = "1.0.0-rc1"', 'versionName = "1.0.0-rc2"')
gradle.write_text(s, encoding="utf-8")

# ---- Decode EXACT user-approved visual assets ----
parts = sorted(assets_dir.glob("part*.txt"))
if not parts:
    raise SystemExit("FreeGPT visual asset parts are missing")
encoded = "".join(p.read_text(encoding="utf-8").strip() for p in parts)
try:
    payload = base64.b64decode(encoded, validate=True)
except Exception as e:
    raise SystemExit(f"Invalid FreeGPT asset base64: {e}")

required = {
    "01_welcome.webp": "freegpt_01_welcome.webp",
    "02_select.webp": "freegpt_02_select.webp",
    "03_settings.webp": "freegpt_03_settings.webp",
    "04_products.webp": "freegpt_04_products.webp",
    "05_pay.webp": "freegpt_05_pay.webp",
    "ic_launcher.webp": "freegpt_icon.webp",
}

drawable = app / "src/main/res/drawable-nodpi"
drawable.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(io.BytesIO(payload), "r") as z:
    names = set(z.namelist())
    missing = [n for n in required if n not in names]
    if missing:
        raise SystemExit("Missing approved FreeGPT assets: " + ", ".join(missing))
    for src_name, dst_name in required.items():
        data = z.read(src_name)
        if len(data) < 1000:
            raise SystemExit(f"Asset too small/corrupt: {src_name}")
        (drawable / dst_name).write_bytes(data)

# Remove the temporary vector placeholder from RC1 so only the approved icon remains.
placeholder = app / "src/main/res/drawable/freegpt_icon.xml"
if placeholder.exists():
    placeholder.unlink()

# Manifest already points to @drawable/freegpt_icon from patch_freegpt.py.
manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
if '@drawable/freegpt_icon' not in m:
    m = m.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/freegpt_icon"')
    m = m.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/freegpt_icon"')
manifest.write_text(m, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# UI imports used only for invisible overlays over the exact images.
if 'import android.widget.FrameLayout;' not in a:
    a = a.replace('import android.widget.Button;', 'import android.widget.Button;\nimport android.widget.FrameLayout;\nimport android.widget.ImageView;', 1)

# Fullscreen/immersive mode: keep the artwork dominant. FIT_CENTER preserves the exact 9:16 image.
immersive = '''        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);\n'''
if 'SYSTEM_UI_FLAG_IMMERSIVE_STICKY' not in a:
    a = a.replace('        getWindow().setNavigationBarColor(NAVY);\n', '        getWindow().setNavigationBarColor(Color.BLACK);\n' + immersive, 1)

# Replace visible, programmatically-drawn screens with the exact approved artwork.
# Only transparent hitboxes are placed over the already-drawn buttons.

def replace_method(text, start_sig, end_sig, body):
    start = text.find(start_sig)
    end = text.find(end_sig, start)
    if start < 0 or end < 0:
        raise SystemExit(f"Method markers not found: {start_sig} -> {end_sig}")
    return text[:start] + body + text[end:]

helpers_and_welcome = r'''    private FrameLayout visualScreen(int drawableRes) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setImageResource(drawableRes);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private void addHitbox(FrameLayout root, float l, float t, float r, float b, String description, Runnable action) {
        View hit = new View(this);
        hit.setBackgroundColor(Color.TRANSPARENT);
        hit.setClickable(true);
        hit.setFocusable(true);
        hit.setContentDescription(description);
        root.addView(hit, new FrameLayout.LayoutParams(1, 1));
        hit.setOnClickListener(v -> action.run());
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                positionHitbox(root, hit, l, t, r, b));
        root.post(() -> positionHitbox(root, hit, l, t, r, b));
    }

    private void positionHitbox(FrameLayout root, View hit, float l, float t, float r, float b) {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        final float srcW = 540f;
        final float srcH = 960f;
        float scale = Math.min(w / srcW, h / srcH);
        float ox = (w - srcW * scale) / 2f;
        float oy = (h - srcH * scale) / 2f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hit.getLayoutParams();
        lp.width = Math.max(1, Math.round((r - l) * scale));
        lp.height = Math.max(1, Math.round((b - t) * scale));
        lp.leftMargin = Math.round(ox + l * scale);
        lp.topMargin = Math.round(oy + t * scale);
        hit.setLayoutParams(lp);
    }

    private void clearVisibleWidgetRefs() {
        stateView = null;
        detailView = null;
        routeView = null;
        logView = null;
        powerButton = null;
        probeButton = null;
        shareButton = null;
        telegramButton = null;
        saveButton = null;
    }

    private View buildWelcomeScreen() {
        settingsMode = false;
        clearVisibleWidgetRefs();
        FrameLayout root = visualScreen(R.drawable.freegpt_01_welcome);
        // Exact artwork button: ПРИСТУПИТЬ
        addHitbox(root, 28, 806, 512, 928, "Приступить", () -> {
            getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply();
            showScreen("select");
        });
        return root;
    }

'''
a = replace_method(a, '    private View buildWelcomeScreen() {', '    private View buildSelectScreen() {', helpers_and_welcome)

select_body = r'''    private View buildSelectScreen() {
        settingsMode = false;
        clearVisibleWidgetRefs();
        FrameLayout root = visualScreen(R.drawable.freegpt_02_select);
        // Four exact AI cards.
        addHitbox(root, 39, 247, 262, 533, "ChatGPT", () -> startSelectedService("ChatGPT", "https://chatgpt.com/"));
        addHitbox(root, 278, 247, 501, 533, "Claude", () -> startSelectedService("Claude", "https://claude.ai/"));
        addHitbox(root, 39, 545, 262, 811, "Gemini", () -> startSelectedService("Gemini", "https://gemini.google.com/"));
        addHitbox(root, 278, 545, 501, 811, "Grok", () -> startSelectedService("Grok", "https://grok.com/"));
        // Exact Settings button.
        addHitbox(root, 116, 846, 424, 918, "Настройки", () -> showScreen("settings"));
        // The supplied artwork has no visible 'other products' button. Tapping the FreeGPT logo opens it.
        addHitbox(root, 205, 15, 335, 112, "Другие приложения", () -> showScreen("products"));
        return root;
    }

'''
a = replace_method(a, '    private View buildSelectScreen() {', '    private void addAiCard(', select_body)

settings_body = r'''    private View buildSettingsScreen() {
        settingsMode = true;
        clearVisibleWidgetRefs();
        FrameLayout root = visualScreen(R.drawable.freegpt_03_settings);
        addHitbox(root, 8, 6, 76, 82, "Назад", () -> showScreen("select"));
        addHitbox(root, 31, 341, 509, 415, "Проверить маршрут", () -> startProbe(false));
        addHitbox(root, 31, 425, 509, 500, "Отправить лог в Telegram", () -> shareTelegram());
        addHitbox(root, 31, 509, 509, 585, "Отправить лог в MAX", () -> shareLog());
        addHitbox(root, 31, 593, 509, 667, "Сохранить ZIP", () -> saveToDownloads());
        return root;
    }

'''
a = replace_method(a, '    private View buildSettingsScreen() {', '    private View buildProductsScreen() {', settings_body)

products_body = r'''    private View buildProductsScreen() {
        settingsMode = false;
        clearVisibleWidgetRefs();
        FrameLayout root = visualScreen(R.drawable.freegpt_04_products);
        // Logo returns to AI selection.
        addHitbox(root, 205, 20, 335, 116, "Назад к нейросетям", () -> showScreen("select"));
        addHitbox(root, 286, 457, 470, 513, "Мессенджеры подробнее", () ->
                Toast.makeText(this, "Отдельное приложение для мессенджеров — скоро", Toast.LENGTH_LONG).show());
        addHitbox(root, 286, 748, 470, 807, "Видеохостинги подробнее", () ->
                Toast.makeText(this, "Отдельное приложение для видеохостингов — скоро", Toast.LENGTH_LONG).show());
        return root;
    }

'''
a = replace_method(a, '    private View buildProductsScreen() {', '    private View buildPayScreen() {', products_body)

pay_body = r'''    private View buildPayScreen() {
        settingsMode = false;
        clearVisibleWidgetRefs();
        FrameLayout root = visualScreen(R.drawable.freegpt_05_pay);
        addHitbox(root, 39, 800, 501, 894, "Купить навсегда", () -> {
            AiAccessLog.i(this, "PAYMENT_REQUEST", "amount=300 RUB lifetime=true provider=not_configured");
            Toast.makeText(this, "Платёжный модуль будет подключён отдельно", Toast.LENGTH_LONG).show();
        });
        addHitbox(root, 145, 900, 395, 958, "Позже", this::finish);
        return root;
    }

'''
a = replace_method(a, '    private View buildPayScreen() {', '    private void showScreen(', pay_body)

# Guard the settings save action: the visual screen uses an invisible generic View, not a Button field.
a = a.replace('        saveButton.setEnabled(false);', '        if (saveButton != null) saveButton.setEnabled(false);')
a = a.replace('        saveButton.setText("СОХРАНЯЮ…");', '        if (saveButton != null) saveButton.setText("СОХРАНЯЮ…");')
a = a.replace('                    saveButton.setEnabled(true);', '                    if (saveButton != null) saveButton.setEnabled(true);')
a = a.replace('                    saveButton.setText("СОХРАНИТЬ ZIP В DOWNLOADS");', '                    if (saveButton != null) saveButton.setText("СОХРАНИТЬ ZIP В DOWNLOADS");')

# ---- Generic per-service route probe ----
probe_start = a.find('    private void startProbe(boolean auto) {')
probe_end = a.find('    private void tryNextExit(', probe_start)
if probe_start < 0 or probe_end < 0:
    raise SystemExit("startProbe/tryNextExit markers not found")
new_probe = r'''    private void startProbe(boolean auto) {
        if (probing) return;
        if (!torBootstrapped) {
            AiAccessLog.w(this, "ROUTE_PROBE_BLOCKED", "bootstrap<100; probe/exit switching forbidden");
            if (!auto) Toast.makeText(this, "Сначала соединение должно быть готово", Toast.LENGTH_SHORT).show();
            return;
        }
        if (socksPort <= 0) {
            AiAccessLog.w(this, "ROUTE_PROBE_SKIPPED", "SOCKS port not ready");
            if (!auto) Toast.makeText(this, "Маршрут ещё не готов", Toast.LENGTH_SHORT).show();
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
        final String serviceName = selectedServiceName == null ? "ChatGPT" : selectedServiceName;
        final String serviceUrl = selectedServiceUrl == null ? "https://chatgpt.com/" : selectedServiceUrl;
        String parsedHost = Uri.parse(serviceUrl).getHost();
        final String serviceHost = (parsedHost == null || parsedHost.isEmpty()) ? "chatgpt.com" : parsedHost;

        new Thread(() -> {
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_PROBE_START", "service=" + serviceName + " host=" + serviceHost + " exit=" + exit + " socks=" + port);
            SocksProbe.Result trace = SocksProbe.fetch(port, "www.cloudflare.com", "/cdn-cgi/trace", 12000, 4096);
            String ip = SocksProbe.traceValue(trace.body, "ip");
            String loc = SocksProbe.traceValue(trace.body, "loc");
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_EXIT_TRACE", "configured=" + exit + " actual=" + loc + " ip=" + ip + " result=" + trace.summary());

            SocksProbe.Result service = SocksProbe.fetch(port, serviceHost, "/", 16000, 2048);
            AiAccessLog.i(AiAccessActivity.this, service.okHttp() ? "AI_SERVICE_PROBE_HTTP" : "AI_SERVICE_PROBE_FAIL",
                    "service=" + serviceName + " " + service.summary());

            boolean accepted = service.httpCode >= 200 && service.httpCode < 400;
            String summary = "service=" + serviceName + " exit=" + exit.toUpperCase() + (loc.isEmpty() ? "" : " / " + loc) +
                    " IP=" + (ip.isEmpty() ? "unknown" : ip) + " " + service.summary();

            main.post(() -> {
                probing = false;
                if (probeButton != null) {
                    probeButton.setEnabled(true);
                    probeButton.setText("ПРОВЕРИТЬ МАРШРУТ");
                }
                if (generation != probeGeneration) return;
                if (routeView != null) routeView.setText(summary);
                if (accepted) {
                    state = "READY";
                    detail = serviceName + " готов · " + exit.toUpperCase() + (loc.isEmpty() ? "" : " / " + loc);
                    AiAccessLog.i(AiAccessActivity.this, "ROUTE_ACCEPTED", "service=" + serviceName + " exit=" + exit + " loc=" + loc + " ip=" + ip + " http=" + service.httpCode);
                    launchChatGpt();
                } else {
                    AiAccessLog.w(AiAccessActivity.this, "ROUTE_REJECTED", "service=" + serviceName + " exit=" + exit + " loc=" + loc + " http=" + service.httpCode);
                    tryNextExit(-1, service.httpCode);
                }
                render();
            });
        }, "RouteProbe").start();
    }

'''
a = a[:probe_start] + new_probe + a[probe_end:]

# Make pool-exhausted message generic (not hardcoded OpenAI/ChatGPT).
a = a.replace('detail = "Все Tor-exit проверены · последний API=" + apiCode + " ChatGPT=" + chatCode;',
              'detail = "Все маршруты проверены · " + selectedServiceName + " HTTP=" + chatCode;')

# Coherent version marker in diagnostics.
a = a.replace('1.0.0-rc1', '1.0.0-rc2')
activity.write_text(a, encoding="utf-8")

log_file = java_dir / "AiAccessLog.java"
log_text = log_file.read_text(encoding="utf-8").replace('1.0.0-rc1', '1.0.0-rc2')
log_file.write_text(log_text, encoding="utf-8")

print("FreeGPT 1.0.0-rc2 exact-visual patch applied")
print("UI source: approved 5 WebP screens + approved WebP launcher icon")
print("Visible programmatic UI overlays: NONE")
print("Interaction overlays: transparent hitboxes only")
print("Generic AI probes: ChatGPT / Claude / Gemini / Grok")
