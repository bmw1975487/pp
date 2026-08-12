from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# RC2 identity: same stable package, new version only.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
s = s.replace('val orbotBaseVersionCode = 100', 'val orbotBaseVersionCode = 101')
s = s.replace('versionName = "1.0.0-rc1"', 'versionName = "1.0.0-rc2-userscreens"')
gradle.write_text(s, encoding="utf-8")

# Use the exact user-provided WEBP icon, not the temporary vector from RC1.
manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('@drawable/freegpt_icon', '@drawable/freegpt_user_icon')
manifest.write_text(m, encoding="utf-8")

# Temporary RC1 vector is no longer used.
old_icon = app / "src/main/res/drawable/freegpt_icon.xml"
if old_icon.exists():
    old_icon.unlink()

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Imports for image-backed screens and touch hitboxes.
if 'import android.view.MotionEvent;' not in a:
    a = a.replace('import android.view.Gravity;', 'import android.view.Gravity;\nimport android.view.MotionEvent;', 1)
if 'import android.widget.FrameLayout;' not in a:
    a = a.replace('import android.widget.Button;', 'import android.widget.Button;\nimport android.widget.FrameLayout;\nimport android.widget.ImageView;', 1)

# Full-screen immersive canvas; the approved artwork itself remains untouched.
needle = '        getWindow().setNavigationBarColor(NAVY);'
immersive = '''        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);'''
if needle in a and 'SYSTEM_UI_FLAG_IMMERSIVE_STICKY' not in a:
    a = a.replace(needle, immersive, 1)

# Replace all programmatically drawn product screens with the user's exact full-screen WEBPs.
ui_start = a.find('    private View buildUi() {')
ui_end = a.find('    private void onPower() {', ui_start)
if ui_start < 0 or ui_end < 0:
    raise SystemExit('FreeGPT buildUi/onPower markers not found')

new_ui = r'''    private View buildUi() {
        if ("pay".equals(screen)) return buildArtworkScreen(R.drawable.freegpt_screen_pay, "pay");
        if ("welcome".equals(screen)) return buildArtworkScreen(R.drawable.freegpt_screen_welcome, "welcome");
        if ("settings".equals(screen)) return buildArtworkScreen(R.drawable.freegpt_screen_settings, "settings");
        if ("products".equals(screen)) return buildArtworkScreen(R.drawable.freegpt_screen_products, "products");
        return buildArtworkScreen(R.drawable.freegpt_screen_select, "select");
    }

    /**
     * Exact artwork screen. No Android text/cards/buttons are drawn over the image.
     * Touches are converted back into the original 540x960 artwork coordinate space,
     * so hitboxes stay aligned even on 20:9 phones where FIT_CENTER adds dark bars.
     */
    private View buildArtworkScreen(int drawableId, String screenId) {
        settingsMode = "settings".equals(screenId);
        stateView = null;
        detailView = null;
        routeView = null;
        logView = null;
        powerButton = null;
        probeButton = null;
        shareButton = null;
        telegramButton = null;
        saveButton = null;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(0, 5, 15));

        ImageView art = new ImageView(this);
        art.setImageResource(drawableId);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setAdjustViewBounds(false);
        root.addView(art, new FrameLayout.LayoutParams(-1, -1));

        root.setOnTouchListener((v, ev) -> {
            if (ev.getAction() != MotionEvent.ACTION_UP) return true;
            float vw = v.getWidth();
            float vh = v.getHeight();
            if (vw <= 0f || vh <= 0f) return true;
            float scale = Math.min(vw / 540f, vh / 960f);
            float drawW = 540f * scale;
            float drawH = 960f * scale;
            float left = (vw - drawW) / 2f;
            float top = (vh - drawH) / 2f;
            float dx = (ev.getX() - left) / scale;
            float dy = (ev.getY() - top) / scale;
            if (dx < 0f || dy < 0f || dx > 540f || dy > 960f) return true;
            handleArtworkTap(screenId, dx, dy);
            return true;
        });

        AiAccessLog.i(this, "UI_ARTWORK_SCREEN", "screen=" + screenId + " drawable=" + drawableId);
        return root;
    }

    private boolean hit(float x, float y, float l, float t, float r, float b) {
        return x >= l && x <= r && y >= t && y <= b;
    }

    private void handleArtworkTap(String screenId, float x, float y) {
        AiAccessLog.i(this, "UI_HIT", "screen=" + screenId + " x=" + Math.round(x) + " y=" + Math.round(y));

        if ("welcome".equals(screenId)) {
            // Approved large PРИСТУПИТЬ button.
            if (hit(x, y, 28, 806, 515, 938)) {
                getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply();
                showScreen("select");
            }
            return;
        }

        if ("select".equals(screenId)) {
            // Four AI cards exactly as drawn on 02_select.webp.
            if (hit(x, y, 38, 246, 262, 535)) {
                startSelectedService("ChatGPT", "https://chatgpt.com/");
            } else if (hit(x, y, 277, 246, 502, 535)) {
                startSelectedService("Claude", "https://claude.ai/");
            } else if (hit(x, y, 38, 545, 262, 812)) {
                startSelectedService("Gemini", "https://gemini.google.com/");
            } else if (hit(x, y, 277, 545, 502, 812)) {
                startSelectedService("Grok", "https://grok.com/");
            } else if (hit(x, y, 116, 842, 414, 925)) {
                showScreen("settings");
            } else if (hit(x, y, 205, 12, 340, 120)) {
                // Brand/logo acts as a quiet route to the developer-products screen.
                showScreen("products");
            }
            return;
        }

        if ("settings".equals(screenId)) {
            if (hit(x, y, 0, 0, 90, 95)) {
                showScreen("select");
            } else if (hit(x, y, 30, 342, 510, 415)) {
                startProbe(false);
            } else if (hit(x, y, 30, 425, 510, 500)) {
                shareTelegram();
            } else if (hit(x, y, 30, 510, 510, 583)) {
                shareLog();
            } else if (hit(x, y, 30, 592, 510, 667)) {
                saveToDownloads();
            }
            return;
        }

        if ("products".equals(screenId)) {
            if (hit(x, y, 200, 12, 340, 115)) {
                showScreen("select");
            } else if (hit(x, y, 282, 455, 470, 512)) {
                AiAccessLog.i(this, "PRODUCT_LINK", "messengers");
                Toast.makeText(this, "Приложение для мессенджеров — следующий продукт", Toast.LENGTH_SHORT).show();
            } else if (hit(x, y, 282, 748, 470, 808)) {
                AiAccessLog.i(this, "PRODUCT_LINK", "video");
                Toast.makeText(this, "Приложение для видеохостингов — следующий продукт", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if ("pay".equals(screenId)) {
            if (hit(x, y, 38, 797, 506, 899)) {
                AiAccessLog.i(this, "PAYMENT_REQUEST", "price=300 RUB lifetime=true provider=not-configured");
                Toast.makeText(this, "Платёжный модуль будет подключён отдельно", Toast.LENGTH_LONG).show();
            } else if (hit(x, y, 140, 900, 405, 960)) {
                AiAccessLog.i(this, "PAYMENT_LATER", "trial expired; closing app");
                finish();
            }
            return;
        }
    }

    private void startSelectedService(String name, String url) {
        if (trialExpired() && !getSharedPreferences(PREFS_APP, MODE_PRIVATE).getBoolean("unlocked_forever", false)) {
            showScreen("pay");
            return;
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

    private void showScreen(String next) {
        screen = next;
        setContentView(buildUi());
        render();
        AiAccessLog.i(this, "UI_SCREEN", next);
    }

    @Override public void onBackPressed() {
        if ("settings".equals(screen) || "products".equals(screen)) {
            showScreen("select");
            return;
        }
        if ("pay".equals(screen) || "welcome".equals(screen) || "select".equals(screen)) {
            finish();
            return;
        }
        super.onBackPressed();
    }

'''
a = a[:ui_start] + new_ui + a[ui_end:]
activity.write_text(a, encoding="utf-8")

# Coherent diagnostic version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
lt = lt.replace('1.0.0-rc1', '1.0.0-rc2-userscreens')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC2 exact user-screens patch applied')
print('applicationId=com.freegpt.access')
print('versionName=1.0.0-rc2-userscreens')
print('UI=5 exact WEBP screens + exact WEBP icon + normalized invisible hitboxes')
