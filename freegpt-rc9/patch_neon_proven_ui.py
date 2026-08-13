from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# Neon RC9 identity: keep the proven RC7 networking path, replace only product/UI.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 106' not in s:
    raise SystemExit('RC7 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 106', 'val orbotBaseVersionCode = 109', 1)
if 'versionName = "1.0.0-rc7-strict-ready"' not in s:
    raise SystemExit('RC7 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc7-strict-ready"', 'versionName = "1.0.0-rc9-neon-proven"', 1)
gradle.write_text(s, encoding="utf-8")

manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:label="FreeGPT"', 'android:label="Neon"')
m = m.replace('@drawable/freegpt_user_icon', '@drawable/neon_icon')
manifest.write_text(m, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Normal Android status bar; no full-screen hiding. The in-app connection status bar is added below.
oncreate = '        super.onCreate(savedInstanceState);\n'
if oncreate in a and 'NEON_STATUS_BAR' not in a:
    a = a.replace(oncreate, oncreate + '''        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);\n        getWindow().setStatusBarColor(Color.rgb(2, 8, 22));\n        getWindow().setNavigationBarColor(Color.rgb(2, 8, 22));\n        AiAccessLog.i(this, "NEON_STATUS_BAR", "systemBars=visible");\n''', 1)

# Additional UI references.
field_marker = '    private long providerVerifiedAtMs = 0L;'
fields = '''    private long providerVerifiedAtMs = 0L;\n    private TextView neonPercentText;\n    private TextView neonStatusText;\n    private ProgressBar neonProgressBar;\n    private TextView neonChatDot;\n    private TextView neonGeminiDot;\n    private TextView neonTechState;\n    private TextView neonTechLog;'''
if field_marker not in a:
    raise SystemExit('RC7 providerVerifiedAtMs marker not found')
a = a.replace(field_marker, fields, 1)

# This product build is free and has no paywall/trial gate.
trial_start = a.find('    private void initTrial() {')
trial_end = a.find('    private long trialRemainingMs()', trial_start)
if trial_start < 0 or trial_end < 0:
    raise SystemExit('initTrial/trialRemainingMs markers not found')
a = a[:trial_start] + '''    private void initTrial() {\n        screen = "main";\n        AiAccessLog.i(this, "NEON_PRODUCT_MODE", "free=true screens=3 services=ChatGPT,Gemini");\n    }\n\n''' + a[trial_end:]

# Hard product guard: no Claude/Grok path can be started by this build.
service_sig = '    private void startSelectedService(String name, String url) {'
if service_sig not in a:
    raise SystemExit('startSelectedService marker not found')
a = a.replace(service_sig, service_sig + '''\n        if (!"ChatGPT".equalsIgnoreCase(name) && !"Gemini".equalsIgnoreCase(name)) {\n            AiAccessLog.w(this, "NEON_SERVICE_DENIED", "name=" + name);\n            Toast.makeText(this, "Доступны только ChatGPT и Gemini", Toast.LENGTH_SHORT).show();\n            return;\n        }''', 1)

# Replace the consumer UI completely. Networking methods below onPower() remain RC7-proven.
ui_start = a.find('    private View buildUi() {')
ui_end = a.find('    private void onPower() {', ui_start)
if ui_start < 0 or ui_end < 0:
    raise SystemExit('buildUi/onPower markers not found')

new_ui = r'''    private View buildUi() {
        if ("technical".equals(screen)) return buildNeonArtworkScreen("technical", R.drawable.neon_technical);
        if ("connection".equals(screen)) return buildNeonArtworkScreen("connection", R.drawable.neon_connection);
        return buildNeonArtworkScreen("main", R.drawable.neon_main);
    }

    private View buildNeonArtworkScreen(String screenId, int drawableId) {
        screen = screenId;
        settingsMode = "technical".equals(screenId);
        stateView = null;
        detailView = null;
        routeView = null;
        logView = null;
        powerButton = null;
        probeButton = null;
        shareButton = null;
        telegramButton = null;
        saveButton = null;
        neonPercentText = null;
        neonStatusText = null;
        neonProgressBar = null;
        neonChatDot = null;
        neonGeminiDot = null;
        neonTechState = null;
        neonTechLog = null;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(1, 6, 18));

        ImageView art = new ImageView(this);
        art.setImageResource(drawableId);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setAdjustViewBounds(false);
        root.addView(art, new FrameLayout.LayoutParams(-1, -1));

        if ("connection".equals(screenId)) attachNeonConnectionOverlay(root);
        if ("technical".equals(screenId)) attachNeonTechnicalOverlay(root);

        root.setOnTouchListener((v, ev) -> {
            if (ev.getAction() != android.view.MotionEvent.ACTION_UP) return true;
            float[] p = artworkPoint(root, ev.getX(), ev.getY());
            if (p == null) return true;
            handleNeonTap(screenId, p[0], p[1]);
            return true;
        });
        root.post(this::render);
        AiAccessLog.i(this, "NEON_UI_SCREEN", screenId);
        return root;
    }

    private float[] artworkPoint(FrameLayout root, float x, float y) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        float scale = Math.min(root.getWidth() / 941f, root.getHeight() / 1672f);
        float drawW = 941f * scale;
        float drawH = 1672f * scale;
        float ox = (root.getWidth() - drawW) / 2f;
        float oy = (root.getHeight() - drawH) / 2f;
        return new float[]{(x - ox) / scale, (y - oy) / scale};
    }

    private boolean neonHit(float x, float y, float l, float t, float r, float b) {
        return x >= l && x <= r && y >= t && y <= b;
    }

    private void handleNeonTap(String screenId, float x, float y) {
        if ("main".equals(screenId)) {
            if (neonHit(x, y, 55, 1260, 885, 1505)) showScreen("connection");
            return;
        }

        if ("connection".equals(screenId)) {
            if (neonHit(x, y, 45, 390, 458, 995)) {
                selectedServiceName = "ChatGPT";
                selectedServiceUrl = "https://chatgpt.com/";
                persistSelectedService();
                render();
                return;
            }
            if (neonHit(x, y, 480, 390, 895, 995)) {
                selectedServiceName = "Gemini";
                selectedServiceUrl = "https://gemini.google.com/";
                persistSelectedService();
                render();
                return;
            }
            if (neonHit(x, y, 65, 1010, 875, 1218)) {
                startSelectedService(selectedServiceName, selectedServiceUrl);
                return;
            }
            if (neonHit(x, y, 135, 1230, 805, 1370)) {
                disconnectNamedService("ChatGPT");
                return;
            }
            if (neonHit(x, y, 135, 1380, 805, 1520)) {
                disconnectNamedService("Gemini");
                return;
            }
            if (neonHit(x, y, 325, 55, 620, 180) || neonHit(x, y, 180, 1530, 765, 1635)) {
                showScreen("technical");
            }
            return;
        }

        if ("technical".equals(screenId)) {
            if (neonHit(x, y, 45, 525, 900, 665)) {
                if ("OFF".equals(state) || "ERROR".equals(state)) startSelectedService(selectedServiceName, selectedServiceUrl);
                else startProbe(false);
                return;
            }
            if (neonHit(x, y, 45, 670, 900, 805)) {
                shareTelegram();
                return;
            }
            if (neonHit(x, y, 45, 805, 900, 945)) {
                saveToDownloads();
                return;
            }
        }
    }

    private void disconnectNamedService(String service) {
        String current = activeServiceName != null ? activeServiceName : selectedServiceName;
        boolean running = !"OFF".equals(state);
        if (running && service.equalsIgnoreCase(current)) {
            stopRoute();
        } else {
            Toast.makeText(this, service + " не активен", Toast.LENGTH_SHORT).show();
        }
    }

    private void attachNeonConnectionOverlay(FrameLayout root) {
        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setGravity(Gravity.CENTER_VERTICAL);
        statusBox.setPadding(dp(12), dp(5), dp(12), dp(5));
        statusBox.setBackground(round(Color.argb(225, 2, 13, 31), 14, Color.rgb(32, 139, 255), 1));
        root.addView(statusBox, new FrameLayout.LayoutParams(1, 1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
        neonStatusText = text("Готов к запуску", 11, Color.rgb(220, 232, 248), Typeface.NORMAL);
        row.addView(neonStatusText, new LinearLayout.LayoutParams(0, -2, 1f));
        neonPercentText = text("0%", 15, Color.rgb(76, 218, 255), Typeface.BOLD);
        neonPercentText.setGravity(Gravity.END);
        row.addView(neonPercentText, new LinearLayout.LayoutParams(dp(62), -2));
        neonProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        neonProgressBar.setMax(100);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(5));
        pp.setMargins(0, dp(2), 0, 0);
        statusBox.addView(neonProgressBar, pp);

        neonChatDot = text("●", 17, Color.rgb(57, 226, 255), Typeface.BOLD);
        neonChatDot.setGravity(Gravity.CENTER);
        root.addView(neonChatDot, new FrameLayout.LayoutParams(1, 1));
        neonGeminiDot = text("●", 17, Color.rgb(144, 86, 255), Typeface.BOLD);
        neonGeminiDot.setGravity(Gravity.CENTER);
        root.addView(neonGeminiDot, new FrameLayout.LayoutParams(1, 1));

        root.post(() -> {
            positionNeonRect(root, statusBox, 92f, 1173f, 849f, 1233f);
            positionNeonRect(root, neonChatDot, 88f, 431f, 137f, 485f);
            positionNeonRect(root, neonGeminiDot, 519f, 431f, 568f, 485f);
            renderNeonUi();
        });
    }

    private void attachNeonTechnicalOverlay(FrameLayout root) {
        neonTechState = text("", 13, Color.rgb(210, 235, 250), Typeface.NORMAL);
        neonTechState.setPadding(dp(8), dp(7), dp(8), dp(7));
        neonTechState.setBackground(round(Color.argb(225, 2, 14, 32), 12, Color.rgb(38, 143, 255), 1));
        root.addView(neonTechState, new FrameLayout.LayoutParams(1, 1));

        neonTechLog = text("", 9, Color.rgb(195, 222, 244), Typeface.NORMAL);
        neonTechLog.setTypeface(Typeface.MONOSPACE);
        neonTechLog.setPadding(dp(8), dp(7), dp(8), dp(7));
        neonTechLog.setBackground(round(Color.argb(242, 1, 10, 24), 12, Color.rgb(40, 118, 190), 1));
        root.addView(neonTechLog, new FrameLayout.LayoutParams(1, 1));

        root.post(() -> {
            positionNeonRect(root, neonTechState, 270f, 300f, 835f, 485f);
            positionNeonRect(root, neonTechLog, 70f, 995f, 875f, 1545f);
            renderNeonUi();
        });
    }

    private void positionNeonRect(FrameLayout root, View child, float l, float t, float r, float b) {
        if (root == null || child == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        float scale = Math.min(root.getWidth() / 941f, root.getHeight() / 1672f);
        float drawW = 941f * scale;
        float drawH = 1672f * scale;
        float ox = (root.getWidth() - drawW) / 2f;
        float oy = (root.getHeight() - drawH) / 2f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
        lp.width = Math.max(1, Math.round((r - l) * scale));
        lp.height = Math.max(1, Math.round((b - t) * scale));
        lp.leftMargin = Math.round(ox + l * scale);
        lp.topMargin = Math.round(oy + t * scale);
        child.setLayoutParams(lp);
    }

    private String neonUserStatus(int percent) {
        if (providerVerified && activeServiceName != null) return activeServiceName + " активен";
        if ("ERROR".equals(state)) return "Не удалось подключить " + selectedServiceName;
        if ("OFF".equals(state)) return "Готов к запуску";
        if (percent < 15) return "Подготавливаю соединение";
        if (percent < 32) return "Проверяю доступ к интернету";
        if (percent < 55) return "Настраиваю подключение";
        if (percent < 76) return "Ищу оптимальный путь";
        if (percent < 94) return "Проверяю стабильность";
        if (percent < 100) return "Почти готово";
        return "Готово";
    }

    private void renderNeonUi() {
        if (neonChatDot != null) neonChatDot.setVisibility("ChatGPT".equalsIgnoreCase(selectedServiceName) ? View.VISIBLE : View.INVISIBLE);
        if (neonGeminiDot != null) neonGeminiDot.setVisibility("Gemini".equalsIgnoreCase(selectedServiceName) ? View.VISIBLE : View.INVISIBLE);

        int pct = userProgressPercent();
        if (neonProgressBar != null) neonProgressBar.setProgress(pct);
        if (neonPercentText != null) neonPercentText.setText(pct + "%");
        if (neonStatusText != null) neonStatusText.setText(neonUserStatus(pct));

        if (neonTechState != null) {
            String transport;
            try { transport = AiAccessPrefs.selectedTransport(); } catch (Throwable t) { transport = "auto"; }
            String service = activeServiceName != null ? activeServiceName : selectedServiceName;
            neonTechState.setText("Состояние: " + (providerVerified ? "Готово" : state) +
                    "\nСервис: " + service +
                    "\nМаршрут: автоматически" +
                    "\nПодключение: " + transport);
        }
        if (neonTechLog != null) {
            String raw = AiAccessLog.tail(this, 20000);
            if (raw == null || raw.isEmpty()) raw = "Лог пока пуст.";
            String[] lines = raw.split("\\n");
            int start = Math.max(0, lines.length - 17);
            StringBuilder out = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                String line = lines[i] == null ? "" : lines[i].trim();
                if (line.length() > 105) line = line.substring(0, 102) + "…";
                if (!line.isEmpty()) {
                    if (out.length() > 0) out.append('\n');
                    out.append(line);
                }
            }
            neonTechLog.setText(out.toString());
        }
    }

    private void showScreen(String name) {
        screen = name;
        setContentView(buildUi());
        render();
        AiAccessLog.i(this, "NEON_UI_NAV", name);
    }

    @Override public void onBackPressed() {
        if ("technical".equals(screen)) { showScreen("connection"); return; }
        if ("connection".equals(screen)) { showScreen("main"); return; }
        super.onBackPressed();
    }

'''
a = a[:ui_start] + new_ui + a[ui_end:]

# Consumer render: networking state is untouched; update only our overlays.
render_start = a.find('    private void render() {')
render_end = a.find('    private TextView text(', render_start)
if render_start < 0 or render_end < 0:
    raise SystemExit('render/text markers not found')
new_render = r'''    private void render() {
        AiAccessLog.setState(state, detail);
        renderNeonUi();
    }

'''
a = a[:render_start] + new_render + a[render_end:]

activity.write_text(a, encoding="utf-8")

# Coherent log version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc7-strict-ready' not in lt:
    raise SystemExit('RC7 log version marker not found')
lt = lt.replace('1.0.0-rc7-strict-ready', '1.0.0-rc9-neon-proven')
log_file.write_text(lt, encoding="utf-8")

print('Neon RC9 proven-browser UI patch applied')
print('version=1.0.0-rc9-neon-proven')
print('screens=main,connection,technical')
print('services=ChatGPT,Gemini only')
print('networking=RC7 external browser/proven route; RC8 WebView NOT applied')
