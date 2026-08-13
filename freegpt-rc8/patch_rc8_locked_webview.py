from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
java_dir = app / "src/main/java/org/torproject/android"

# Identity.
gradle = app / "build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
if 'val orbotBaseVersionCode = 106' not in s:
    raise SystemExit('RC7 versionCode marker not found')
s = s.replace('val orbotBaseVersionCode = 106', 'val orbotBaseVersionCode = 107', 1)
if 'versionName = "1.0.0-rc7-strict-ready"' not in s:
    raise SystemExit('RC7 versionName marker not found')
s = s.replace('versionName = "1.0.0-rc7-strict-ready"', 'versionName = "1.0.0-rc8-locked-webview"', 1)
# ProxyController is the supported process-local proxy API for android.webkit.WebView.
if 'androidx.webkit:webkit:1.16.0' not in s:
    marker = 'dependencies {'
    if marker not in s:
        raise SystemExit('dependencies block not found')
    s = s.replace(marker, marker + '\n    implementation("androidx.webkit:webkit:1.16.0")', 1)
gradle.write_text(s, encoding="utf-8")

# IMPORTANT: no Android VPN routing in RC8. Orbot runs as a local proxy only.
prefs = java_dir / "AiAccessPrefs.kt"
p = prefs.read_text(encoding="utf-8")
if 'Prefs.putUseVpn(true)' not in p:
    raise SystemExit('AiAccessPrefs VPN=true marker not found')
p = p.replace('Prefs.putUseVpn(true)', 'Prefs.putUseVpn(false)', 1)
prefs.write_text(p, encoding="utf-8")

activity = java_dir / "AiAccessActivity.java"
a = activity.read_text(encoding="utf-8")

# Imports.
imports = {
    'import android.webkit.CookieManager;': 'import android.webkit.CookieManager;\n',
    'import android.webkit.WebChromeClient;': 'import android.webkit.WebChromeClient;\n',
    'import android.webkit.WebResourceRequest;': 'import android.webkit.WebResourceRequest;\n',
    'import android.webkit.WebSettings;': 'import android.webkit.WebSettings;\n',
    'import android.webkit.WebView;': 'import android.webkit.WebView;\n',
    'import android.webkit.WebViewClient;': 'import android.webkit.WebViewClient;\n',
    'import androidx.webkit.ProxyConfig;': 'import androidx.webkit.ProxyConfig;\n',
    'import androidx.webkit.ProxyController;': 'import androidx.webkit.ProxyController;\n',
    'import androidx.webkit.WebViewFeature;': 'import androidx.webkit.WebViewFeature;\n',
    'import java.util.concurrent.Executor;': 'import java.util.concurrent.Executor;\n',
}
insert_anchor = 'import android.widget.Toast;\n'
for key, line in imports.items():
    if key not in a:
        a = a.replace(insert_anchor, insert_anchor + line, 1)

# RC8 fields.
field_marker = '    private long providerVerifiedAtMs = 0L;'
fields = '''    private long providerVerifiedAtMs = 0L;
    private WebView restrictedWebView;
    private boolean webViewProxyApplied = false;
    private boolean restrictedBrowserVisible = false;'''
if field_marker not in a:
    raise SystemExit('RC7 field marker not found')
a = a.replace(field_marker, fields, 1)

# On startup, reject stale Claude/Grok sessions from older builds.
restore_marker = '''        if (savedName == null || savedName.isEmpty()) savedName = "ChatGPT";
        if (savedUrl == null || savedUrl.isEmpty()) savedUrl = serviceUrlForName(savedName);'''
restore_repl = '''        if (savedName == null || savedName.isEmpty()) savedName = "ChatGPT";
        if (!"ChatGPT".equalsIgnoreCase(savedName) && !"Gemini".equalsIgnoreCase(savedName)) {
            savedName = "ChatGPT";
            savedUrl = "https://chatgpt.com/";
            p.edit().putBoolean("provider_verified", false).remove("active_service_name").remove("active_service_url").apply();
        }
        if (savedUrl == null || savedUrl.isEmpty()) savedUrl = serviceUrlForName(savedName);'''
if restore_marker not in a:
    raise SystemExit('RC7 restore marker not found')
a = a.replace(restore_marker, restore_repl, 1)

# Hard service guard: only ChatGPT/Gemini may ever be selected.
start_marker = '    private void startSelectedService(String name, String url) {'
if start_marker not in a:
    raise SystemExit('startSelectedService marker not found')
a = a.replace(start_marker, '''    private void startSelectedService(String name, String url) {
        if (!"ChatGPT".equalsIgnoreCase(name) && !"Gemini".equalsIgnoreCase(name)) {
            AiAccessLog.w(this, "SERVICE_DENIED_RC8", "name=" + name + " reason=locked_to_chatgpt_gemini");
            Toast.makeText(this, "Доступны только ChatGPT и Gemini", Toast.LENGTH_SHORT).show();
            return;
        }''', 1)

# Replace power path: proxy-only Tor. No VpnService.prepare(), no browser-package routing.
power_start = a.find('    private void onPower() {')
power_end = a.find('    @Override protected void onActivityResult(', power_start)
if power_start < 0 or power_end < 0:
    raise SystemExit('onPower/onActivityResult markers not found')
new_power = r'''    private void onPower() {
        AiAccessLog.i(this, "POWER_BUTTON_RC8", "state=" + state + " service=" + selectedServiceName + " mode=proxy_only");
        long now = SystemClock.elapsedRealtime();
        if (now - lastPowerTapMs < 1200L) return;
        lastPowerTapMs = now;
        if (!"OFF".equals(state) && !"ERROR".equals(state)) {
            stopRoute();
            return;
        }
        stopRequested = false;
        targetPackage = getPackageName();
        targetLabel = "FreeGPT";
        setCurrentExit(serviceExitCandidates != null && serviceExitCandidates.length > 0 ? serviceExitCandidates[0] : "nl");
        serviceExitAttempt = 0;
        probeGeneration++;
        torBootstrapped = false;
        routeProbeArmed = false;
        liveBootstrapPercent = 0;
        if (uxStartedAtMs <= 0L) uxStartedAtMs = SystemClock.elapsedRealtime();
        uxLastPercent = Math.max(uxLastPercent, 3);
        AiAccessPrefs.configure(this, getPackageName(), currentExit());
        AiAccessLog.i(this, "RC8_PROXY_ONLY_CONFIG",
                "vpn=false webviewOnly=true package=" + getPackageName() + " exit=" + currentExit());
        state = "PREPARING";
        detail = "Подготавливаю соединение";
        render();
        startOrbot();
    }

'''
a = a[:power_start] + new_power + a[power_end:]

# The old VPN result callback is unreachable; leave it compiled for compatibility.

# Select screen: Claude/Grok cards are explicitly disabled. ChatGPT and Gemini remain active.
old_select = '''            if (hit(x, y, 38, 246, 262, 535)) {
                startSelectedService("ChatGPT", "https://chatgpt.com/");
            } else if (hit(x, y, 277, 246, 502, 535)) {
                startSelectedService("Claude", "https://claude.ai/");
            } else if (hit(x, y, 38, 545, 262, 812)) {
                startSelectedService("Gemini", "https://gemini.google.com/");
            } else if (hit(x, y, 277, 545, 502, 812)) {
                startSelectedService("Grok", "https://grok.com/");'''
new_select = '''            if (hit(x, y, 38, 246, 262, 535)) {
                startSelectedService("ChatGPT", "https://chatgpt.com/");
            } else if (hit(x, y, 277, 246, 502, 535)) {
                AiAccessLog.i(this, "SERVICE_DENIED_RC8", "name=Claude ui=true");
                Toast.makeText(this, "В этой версии доступны только ChatGPT и Gemini", Toast.LENGTH_SHORT).show();
            } else if (hit(x, y, 38, 545, 262, 812)) {
                startSelectedService("Gemini", "https://gemini.google.com/");
            } else if (hit(x, y, 277, 545, 502, 812)) {
                AiAccessLog.i(this, "SERVICE_DENIED_RC8", "name=Grok ui=true");
                Toast.makeText(this, "В этой версии доступны только ChatGPT и Gemini", Toast.LENGTH_SHORT).show();'''
if old_select not in a:
    raise SystemExit('RC2 select hitbox block not found')
a = a.replace(old_select, new_select, 1)

# Add visible locks over Claude/Grok on the existing artwork without changing the supplied asset bytes.
attach_marker = '''        if ("select".equals(screenId)) {
            attachLiveStatusPanel(root);'''
attach_repl = '''        if ("select".equals(screenId)) {
            attachLiveStatusPanel(root);
            attachRc8DisabledCards(root);'''
if attach_marker not in a:
    raise SystemExit('select attach marker not found')
a = a.replace(attach_marker, attach_repl, 1)

insert_marker = '    private void attachLiveStatusPanel(FrameLayout root) {'
locks = r'''    private void attachRc8DisabledCards(FrameLayout root) {
        TextView claudeLock = text("НЕДОСТУПНО", 13, MUTED, Typeface.BOLD);
        claudeLock.setGravity(Gravity.CENTER);
        claudeLock.setBackground(round(Color.argb(225, 3, 15, 35), 18, Color.rgb(60, 80, 115), 1));
        root.addView(claudeLock, new FrameLayout.LayoutParams(1, 1));
        TextView grokLock = text("НЕДОСТУПНО", 13, MUTED, Typeface.BOLD);
        grokLock.setGravity(Gravity.CENTER);
        grokLock.setBackground(round(Color.argb(225, 3, 15, 35), 18, Color.rgb(60, 80, 115), 1));
        root.addView(grokLock, new FrameLayout.LayoutParams(1, 1));
        root.post(() -> {
            positionArtworkRect(root, claudeLock, 277f, 246f, 502f, 535f);
            positionArtworkRect(root, grokLock, 277f, 545f, 502f, 812f);
        });
    }

'''
if insert_marker not in a:
    raise SystemExit('attachLiveStatusPanel marker not found')
a = a.replace(insert_marker, locks + insert_marker, 1)

# Restricted in-app browser helpers.
launch_start = a.find('    private void launchChatGpt() {')
launch_end = a.find('    private Uri createZipUri(', launch_start)
if launch_start < 0 or launch_end < 0:
    raise SystemExit('RC7 launch block markers not found')
new_launch = r'''    private boolean isAllowedServiceName(String service) {
        return "ChatGPT".equalsIgnoreCase(service) || "Gemini".equalsIgnoreCase(service);
    }

    private boolean hostEqualsOrSubdomain(String host, String domain) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        domain = domain.toLowerCase(Locale.ROOT);
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private boolean isAllowedTopLevelUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        String service = activeServiceName != null ? activeServiceName : selectedServiceName;
        if ("Gemini".equalsIgnoreCase(service)) {
            return hostEqualsOrSubdomain(host, "gemini.google.com") ||
                    hostEqualsOrSubdomain(host, "accounts.google.com");
        }
        if ("ChatGPT".equalsIgnoreCase(service)) {
            return hostEqualsOrSubdomain(host, "chatgpt.com") ||
                    hostEqualsOrSubdomain(host, "auth.openai.com") ||
                    hostEqualsOrSubdomain(host, "auth0.openai.com") ||
                    hostEqualsOrSubdomain(host, "accounts.google.com") ||
                    hostEqualsOrSubdomain(host, "appleid.apple.com") ||
                    hostEqualsOrSubdomain(host, "login.microsoftonline.com");
        }
        return false;
    }

    private void clearRestrictedWebViewProxy() {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(ContextCompat.getMainExecutor(this), () ->
                        AiAccessLog.i(this, "RC8_WEBVIEW_PROXY_CLEAR", "done=true"));
            }
        } catch (Throwable t) {
            AiAccessLog.e(this, "RC8_WEBVIEW_PROXY_CLEAR_FAIL", String.valueOf(t.getMessage()), t);
        }
        webViewProxyApplied = false;
    }

    private void destroyRestrictedWebView() {
        restrictedBrowserVisible = false;
        if (restrictedWebView != null) {
            try {
                restrictedWebView.stopLoading();
                restrictedWebView.loadUrl("about:blank");
                restrictedWebView.clearHistory();
                restrictedWebView.removeAllViews();
                restrictedWebView.destroy();
            } catch (Throwable ignored) {}
            restrictedWebView = null;
        }
        clearRestrictedWebViewProxy();
    }

    private void openRestrictedWebView() {
        final String service = activeServiceName != null ? activeServiceName : selectedServiceName;
        if (!providerVerified || !isAllowedServiceName(service)) {
            AiAccessLog.w(this, "RC8_WEBVIEW_OPEN_BLOCKED",
                    "service=" + service + " providerVerified=" + providerVerified);
            return;
        }
        if (socksPort <= 0) {
            browserOpenFailed = true;
            state = "ERROR";
            detail = "Не удалось открыть " + service;
            AiAccessLog.w(this, "RC8_WEBVIEW_OPEN_FAIL", "SOCKS not ready service=" + service);
            render();
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            browserOpenFailed = true;
            state = "ERROR";
            detail = "Встроенный режим не поддерживается на этом устройстве";
            AiAccessLog.w(this, "RC8_PROXY_UNSUPPORTED", "WebViewFeature.PROXY_OVERRIDE=false");
            render();
            return;
        }
        final String url = "Gemini".equalsIgnoreCase(service)
                ? "https://gemini.google.com/" : "https://chatgpt.com/";
        ProxyConfig proxyConfig = new ProxyConfig.Builder()
                .addProxyRule("socks://127.0.0.1:" + socksPort)
                .build();
        ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                ContextCompat.getMainExecutor(this),
                () -> {
                    webViewProxyApplied = true;
                    AiAccessLog.i(this, "RC8_WEBVIEW_PROXY_READY",
                            "service=" + service + " socks=127.0.0.1:" + socksPort + " vpn=false");
                    showRestrictedBrowser(service, url);
                });
    }

    private void showRestrictedBrowser(String service, String url) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(2, 8, 22));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(4, 16, 35));

        Button back = button("‹", CARD2);
        back.setTextSize(24);
        back.setOnClickListener(v -> {
            if (restrictedWebView != null && restrictedWebView.canGoBack()) restrictedWebView.goBack();
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(46), dp(42)));

        TextView title = text(service, 17, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button off = button("ОТКЛЮЧИТЬ", CARD2);
        off.setTextSize(11);
        off.setOnClickListener(v -> {
            destroyRestrictedWebView();
            stopRoute();
            showScreen("select");
        });
        top.addView(off, new LinearLayout.LayoutParams(dp(122), dp(42)));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP);
        root.addView(top, topLp);

        WebView web = new WebView(this);
        restrictedWebView = web;
        restrictedBrowserVisible = true;
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setSupportMultipleWindows(false);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            private boolean allow(Uri uri) {
                boolean ok = isAllowedTopLevelUrl(uri);
                if (!ok) {
                    AiAccessLog.w(AiAccessActivity.this, "RC8_NAV_BLOCKED",
                            "service=" + service + " url=" + String.valueOf(uri));
                    Toast.makeText(AiAccessActivity.this,
                            "Внешние сайты в FreeGPT недоступны", Toast.LENGTH_SHORT).show();
                }
                return ok;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request == null ? null : request.getUrl();
                return !allow(u);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String requested) {
                try { return !allow(Uri.parse(requested)); }
                catch (Throwable t) { return true; }
            }
            @Override public void onPageFinished(WebView view, String loadedUrl) {
                AiAccessLog.i(AiAccessActivity.this, "RC8_PAGE_FINISHED",
                        "service=" + service + " url=" + loadedUrl);
            }
        });

        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(-1, -1);
        webLp.topMargin = dp(54);
        root.addView(web, webLp);
        setContentView(root);
        screen = "restricted_browser";
        AiAccessLog.i(this, "RC8_RESTRICTED_BROWSER_OPEN",
                "service=" + service + " url=" + url + " addressBar=false externalNav=false");
        web.loadUrl(url);
    }

    private void launchChatGpt() {
        String service = activeServiceName != null ? activeServiceName : selectedServiceName;
        if (!providerVerified || !isAllowedServiceName(service)) {
            AiAccessLog.w(this, "AI_WEB_OPEN_BLOCKED",
                    "service=" + service + " reason=provider_not_verified_or_disallowed");
            if ("READY".equals(state)) state = "TOR_ON";
            uxLastPercent = Math.min(99, Math.max(uxLastPercent, 90));
            detail = "Проверяю " + service;
            render();
            return;
        }
        browserOpenIssued = true;
        browserOpenFailed = false;
        getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit()
                .putBoolean("browser_open_issued", true)
                .putBoolean("browser_open_failed", false)
                .putString("active_service_name", service)
                .putString("active_service_url", selectedServiceUrl)
                .apply();
        detail = service + " активен";
        AiAccessLog.i(this, "AI_WEB_OPEN",
                "service=" + service + " mode=restricted_webview vpn=false");
        render();
        openRestrictedWebView();
    }

'''
a = a[:launch_start] + new_launch + a[launch_end:]

# Stop always closes the locked browser/proxy first.
stop_marker = '    private void stopRoute() {\n'
if stop_marker not in a:
    raise SystemExit('stopRoute marker not found')
a = a.replace(stop_marker, '''    private void stopRoute() {
        if (restrictedWebView != null || webViewProxyApplied) destroyRestrictedWebView();
''', 1)

# Back button can never escape to an unrestricted browser.
back_start = a.find('    @Override public void onBackPressed() {')
back_end = a.find('    private void onPower() {', back_start)
if back_start >= 0 and back_end > back_start:
    old_back = a[back_start:back_end]
    new_back = r'''    @Override public void onBackPressed() {
        if (restrictedBrowserVisible && restrictedWebView != null) {
            if (restrictedWebView.canGoBack()) restrictedWebView.goBack();
            else {
                destroyRestrictedWebView();
                stopRoute();
                showScreen("select");
            }
            return;
        }
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
    a = a[:back_start] + new_back + a[back_end:]
else:
    raise SystemExit('onBackPressed/onPower markers not found')

activity.write_text(a, encoding="utf-8")

# Coherent log version.
log_file = java_dir / "AiAccessLog.java"
lt = log_file.read_text(encoding="utf-8")
if '1.0.0-rc7-strict-ready' not in lt:
    raise SystemExit('RC7 log version marker not found')
lt = lt.replace('1.0.0-rc7-strict-ready', '1.0.0-rc8-locked-webview')
log_file.write_text(lt, encoding="utf-8")

print('FreeGPT RC8 locked WebView patch applied')
print('versionName=1.0.0-rc8-locked-webview')
print('services=ChatGPT,Gemini only')
print('vpn=false')
print('browser=internal WebView via local Orbot SOCKS ProxyController')
print('navigation=allowlisted, no address bar, external sites blocked')
