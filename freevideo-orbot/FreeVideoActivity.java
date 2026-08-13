package org.torproject.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.torproject.android.service.OrbotConstants;
import org.torproject.android.service.OrbotService;
import org.torproject.android.service.circumvention.SmartConnect;
import org.torproject.jni.TorService;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class AiAccessActivity extends Activity {
    private static final int VPN_REQ = 4301;
    private static final String YOUTUBE = "com.google.android.youtube";
    private static final String MAX = "ru.oneme.app";
    private static final String[] TELEGRAM_PACKAGES = {"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"};
    private static final String SMART_ACTION = SmartConnect.AI_ACTION;
    private static final String[] EXITS = {"nl", "de", "fr", "gb", "us"};

    private static final int SCREEN_INTRO = 2;
    private static final int SCREEN_MAIN = 3;
    private static final int SCREEN_PAY = 4;
    private static final int SCREEN_SETTINGS = 5;
    private static final int SCREEN_OTHER = 6;

    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ImageView screenView;
    private TextView statusOverlay;
    private TextView settingsStateOverlay;
    private TextView logOverlay;

    private int screen = SCREEN_INTRO;
    private String state = "OFF";
    private String detail = "Готово";
    private int socksPort = -1;
    private int exitIndex = 0;
    private boolean probing = false;
    private boolean receiverRegistered = false;
    private boolean torBootstrapped = false;
    private boolean routeProbeArmed = false;
    private boolean stopRequested = false;
    private int probeGeneration = 0;
    private long lastOpenTapMs = 0L;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshOverlays();
            main.postDelayed(this, 650);
        }
    };

    private final BroadcastReceiver orbotReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (OrbotConstants.LOCAL_ACTION_LOG.equals(action)) {
                String line = intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_LOG);
                String percent = intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT);
                AiAccessLog.i(AiAccessActivity.this, "TOR_LOG", (percent == null ? "" : "bootstrap=" + percent + " ") + String.valueOf(line));
                if (percent != null) {
                    state = "CONNECTING";
                    detail = "Подключение к Snowflake · " + percent + "%";
                }
            } else if (SMART_ACTION.equals(action)) {
                String event = intent.getStringExtra(SmartConnect.EXTRA_EVENT);
                String smartDetail = intent.getStringExtra(SmartConnect.EXTRA_DETAIL);
                AiAccessLog.i(AiAccessActivity.this, "SMARTCONNECT_" + String.valueOf(event), String.valueOf(smartDetail));
                if ("BOOTSTRAP_COMPLETE".equals(event)) {
                    onTorBootstrapped("SMARTCONNECT");
                } else if ("TRANSPORT_EXHAUSTED".equals(event)) {
                    state = "ERROR";
                    detail = "Не удалось подключиться · сохраните ZIP-лог";
                } else if ("BOOTSTRAP_STALL".equals(event) || "TRANSPORT_SWITCH".equals(event) || "AUTOCONF_OK".equals(event)) {
                    state = "CONNECTING";
                    detail = "Автоподбор маршрута · " + String.valueOf(smartDetail);
                }
            } else if (OrbotConstants.LOCAL_ACTION_PORTS.equals(action)) {
                socksPort = intent.getIntExtra(OrbotConstants.EXTRA_SOCKS_PROXY_PORT, -1);
                int dns = intent.getIntExtra(OrbotConstants.EXTRA_DNS_PORT, -1);
                int trans = intent.getIntExtra(OrbotConstants.EXTRA_TRANS_PORT, -1);
                AiAccessLog.i(AiAccessActivity.this, "TOR_PORTS", "socks=" + socksPort + " dns=" + dns + " trans=" + trans);
                if (socksPort > 0 && torBootstrapped && routeProbeArmed) scheduleProbe(1200);
            } else if (OrbotConstants.LOCAL_ACTION_STATUS.equals(action)) {
                String status = intent.getStringExtra(TorService.EXTRA_STATUS);
                AiAccessLog.i(AiAccessActivity.this, "TOR_STATUS", "status=" + status);
                if (TorService.STATUS_ON.equals(status)) {
                    onTorBootstrapped("TOR_STATUS_ON");
                } else if (TorService.STATUS_OFF.equals(status)) {
                    if (stopRequested) {
                        stopRequested = false;
                        state = "OFF";
                        detail = "Отключено";
                    } else if (!"CONNECTING".equals(state) && !"PREPARING".equals(state) && !"READY".equals(state)) {
                        state = "OFF";
                        detail = "Маршрут остановлен";
                    }
                }
            }
            AiAccessLog.setState(state, detail);
            refreshOverlays();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashLogger();
        AiAccessLog.i(this, "FREEVIDEO_ACTIVITY_CREATE", "sdk=" + Build.VERSION.SDK_INT + " device=" + Build.MANUFACTURER + " " + Build.MODEL);
        buildImageUi();
        registerOrbotReceiver();
        int initial = getPreferences(android.content.Context.MODE_PRIVATE).getBoolean("freevideo_intro_seen", false) ? SCREEN_MAIN : SCREEN_INTRO;
        showScreen(initial);
        main.post(refresh);
        AiAccessLog.setState(state, detail);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        AiAccessLog.i(this, "FREEVIDEO_RESUME", "screen=" + screen + " state=" + state);
    }

    @Override protected void onDestroy() {
        probeGeneration++;
        main.removeCallbacks(refresh);
        if (receiverRegistered) {
            try { unregisterReceiver(orbotReceiver); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Deprecated("Deprecated in Java")
    @Override public void onBackPressed() {
        if (screen == SCREEN_SETTINGS || screen == SCREEN_OTHER || screen == SCREEN_PAY) {
            showScreen(SCREEN_MAIN);
            return;
        }
        super.onBackPressed();
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { AiAccessLog.e(getApplicationContext(), "FATAL_UNCAUGHT", "thread=" + thread.getName(), error); } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private void registerOrbotReceiver() {
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(OrbotConstants.LOCAL_ACTION_LOG);
            f.addAction(OrbotConstants.LOCAL_ACTION_STATUS);
            f.addAction(OrbotConstants.LOCAL_ACTION_PORTS);
            f.addAction(SMART_ACTION);
            ContextCompat.registerReceiver(this, orbotReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
            AiAccessLog.i(this, "TOR_RECEIVER_READY", "status/log/ports/smartconnect");
        } catch (Throwable t) {
            AiAccessLog.e(this, "TOR_RECEIVER_FAIL", String.valueOf(t.getMessage()), t);
        }
    }

    private void buildImageUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        screenView = new ImageView(this);
        screenView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenView.setBackgroundColor(Color.BLACK);
        screenView.setClickable(true);
        screenView.setFocusable(true);
        screenView.setOnTouchListener((v, event) -> handleTouch(event));
        screenView.setOnLongClickListener(v -> {
            if (screen == SCREEN_MAIN) {
                showScreen(SCREEN_SETTINGS);
                return true;
            }
            return false;
        });
        root.addView(screenView, new FrameLayout.LayoutParams(-1, -1));

        statusOverlay = overlayText(14, Gravity.CENTER);
        settingsStateOverlay = overlayText(13, Gravity.START);
        settingsStateOverlay.setTypeface(Typeface.DEFAULT_BOLD);
        logOverlay = overlayText(10, Gravity.START);
        logOverlay.setTypeface(Typeface.MONOSPACE);
        root.addView(statusOverlay);
        root.addView(settingsStateOverlay);
        root.addView(logOverlay);
        setContentView(root);
        hideSystemUi();
    }

    private TextView overlayText(int sp, int gravity) {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.rgb(208, 242, 255));
        tv.setTextSize(sp);
        tv.setGravity(gravity);
        tv.setPadding(dp(10), dp(7), dp(10), dp(7));
        tv.setBackground(new ColorDrawable(Color.argb(215, 2, 10, 26)));
        tv.setVisibility(View.GONE);
        return tv;
    }

    private void showScreen(int which) {
        screen = which;
        String asset;
        switch (which) {
            case SCREEN_INTRO: asset = "freevideo/02_FreeVideo_start.jpg"; break;
            case SCREEN_MAIN: asset = "freevideo/03_FreeVideo_videohosting.jpg"; break;
            case SCREEN_PAY: asset = "freevideo/04_FreeVideo_99rub.jpg"; break;
            case SCREEN_SETTINGS: asset = "freevideo/05_FreeVideo_settings.jpg"; break;
            case SCREEN_OTHER: asset = "freevideo/06_FreeVideo_other_apps.jpg"; break;
            default: asset = "freevideo/03_FreeVideo_videohosting.jpg";
        }
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(getAssets().open(asset));
            if (bitmap == null) throw new IllegalStateException("Bitmap decode returned null");
            screenView.setImageBitmap(bitmap);
            AiAccessLog.i(this, "UI_SCREEN", "screen=" + which + " asset=" + asset + " " + bitmap.getWidth() + "x" + bitmap.getHeight());
        } catch (Throwable t) {
            AiAccessLog.e(this, "UI_ASSET_FAIL", "asset=" + asset, t);
            screenView.setImageDrawable(null);
            Toast.makeText(this, "Ошибка загрузки экрана FreeVideo", Toast.LENGTH_LONG).show();
        }
        screenView.post(this::refreshOverlays);
    }

    private boolean handleTouch(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float[] p = imagePoint(event.getX(), event.getY());
        if (p == null) return true;
        float x = p[0], y = p[1];
        if (screen == SCREEN_INTRO) {
            if (y > 0.80f) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("freevideo_intro_seen", true).apply();
                showScreen(SCREEN_MAIN);
            }
        } else if (screen == SCREEN_MAIN) {
            if (y > 0.80f) onOpenPressed();
            else if (y < 0.17f) showScreen(SCREEN_SETTINGS);
        } else if (screen == SCREEN_SETTINGS) {
            if (x < 0.20f && y < 0.13f) showScreen(SCREEN_MAIN);
            else if (y >= 0.36f && y < 0.44f) startProbe(false);
            else if (y >= 0.44f && y < 0.52f) shareTelegram();
            else if (y >= 0.52f && y < 0.60f) shareMax();
            else if (y >= 0.60f && y < 0.69f) saveZip();
        } else if (screen == SCREEN_OTHER) {
            if (x < 0.22f && y < 0.18f) showScreen(SCREEN_MAIN);
        } else if (screen == SCREEN_PAY) {
            if (y > 0.90f) showScreen(SCREEN_MAIN);
        }
        return true;
    }

    private float[] imagePoint(float viewX, float viewY) {
        if (screenView.getDrawable() == null) return null;
        float[] values = new float[9];
        screenView.getImageMatrix().getValues(values);
        float sx = values[Matrix.MSCALE_X], sy = values[Matrix.MSCALE_Y];
        float tx = values[Matrix.MTRANS_X], ty = values[Matrix.MTRANS_Y];
        if (sx == 0f || sy == 0f) return null;
        float ix = (viewX - tx) / sx, iy = (viewY - ty) / sy;
        int iw = screenView.getDrawable().getIntrinsicWidth();
        int ih = screenView.getDrawable().getIntrinsicHeight();
        if (ix < 0 || iy < 0 || ix > iw || iy > ih) return null;
        return new float[]{ix / iw, iy / ih};
    }

    private void onOpenPressed() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastOpenTapMs < 1200L) return;
        lastOpenTapMs = now;
        AiAccessLog.i(this, "OPEN_PRESSED", "state=" + state + " youtubeInstalled=" + isYouTubeInstalled());
        if (!isYouTubeInstalled()) {
            state = "ERROR";
            detail = "Официальное приложение YouTube не установлено";
            AiAccessLog.w(this, "YOUTUBE_PACKAGE_MISSING", YOUTUBE);
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
            refreshOverlays();
            return;
        }
        if ("READY".equals(state)) {
            launchYouTubeApp();
            return;
        }
        if ("CONNECTING".equals(state) || "PREPARING".equals(state) || "TOR_ON".equals(state) || "TESTING".equals(state)) {
            Toast.makeText(this, "Маршрут ещё подключается. Дождитесь готовности.", Toast.LENGTH_SHORT).show();
            return;
        }
        startRoute();
    }

    private void startRoute() {
        exitIndex = 0;
        probeGeneration++;
        probing = false;
        torBootstrapped = false;
        routeProbeArmed = false;
        AiAccessPrefs.configure(this, YOUTUBE, currentExit());
        AiAccessLog.i(this, "ROUTE_CONFIG", "allowedApplication=" + YOUTUBE + " smartConnect=" + AiAccessPrefs.smartConnectEnabled());
        state = "PREPARING";
        detail = "Подготовка защищённого маршрута…";
        AiAccessLog.setState(state, detail);
        refreshOverlays();
        try {
            Intent prep = VpnService.prepare(this);
            if (prep != null) {
                AiAccessLog.i(this, "VPN_PERMISSION_UI", "Android confirmation required");
                startActivityForResult(prep, VPN_REQ);
            } else {
                startOrbot();
            }
        } catch (Throwable t) {
            fail("VPN_PREPARE", t);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VPN_REQ) return;
        AiAccessLog.i(this, "VPN_PERMISSION_RESULT", "resultCode=" + resultCode);
        if (resultCode == RESULT_OK) startOrbot();
        else {
            state = "ERROR";
            detail = "VPN-разрешение не выдано";
            AiAccessLog.w(this, "VPN_PERMISSION_DENIED", "result=" + resultCode);
            refreshOverlays();
        }
    }

    private void startOrbot() {
        try {
            stopRequested = false;
            state = "CONNECTING";
            detail = "Подключение к Snowflake…";
            Intent i = new Intent(this, OrbotService.class)
                    .setAction(TorService.ACTION_START)
                    .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true);
            AiAccessLog.i(this, "ENGINE_START", "OrbotService START target=" + YOUTUBE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            refreshOverlays();
        } catch (Throwable t) {
            fail("ENGINE_START", t);
        }
    }

    private void onTorBootstrapped(String source) {
        if (torBootstrapped && routeProbeArmed) return;
        torBootstrapped = true;
        state = "TOR_ON";
        detail = "Tor готов · проверяю YouTube через " + currentExit().toUpperCase(Locale.ROOT);
        AiAccessLog.i(this, "BOOTSTRAP_GATE_OPEN", "source=" + source + " socks=" + socksPort + " exit=" + currentExit());
        if (!routeProbeArmed) {
            routeProbeArmed = true;
            switchExit(currentExit());
            scheduleProbe(8000);
        }
        refreshOverlays();
    }

    private void scheduleProbe(long delayMs) {
        final int generation = probeGeneration;
        main.postDelayed(() -> {
            if (generation == probeGeneration && !"OFF".equals(state)) startProbe(true);
        }, delayMs);
    }

    private void startProbe(boolean auto) {
        if (probing) return;
        if (!torBootstrapped || socksPort <= 0) {
            AiAccessLog.w(this, "ROUTE_PROBE_BLOCKED", "bootstrap=" + torBootstrapped + " socks=" + socksPort);
            if (!auto) Toast.makeText(this, "Маршрут ещё не готов", Toast.LENGTH_SHORT).show();
            return;
        }
        probing = true;
        final int generation = probeGeneration;
        final int port = socksPort;
        final String exit = currentExit();
        state = "TESTING";
        detail = "Проверка YouTube · " + exit.toUpperCase(Locale.ROOT);
        refreshOverlays();

        new Thread(() -> {
            AiAccessLog.i(AiAccessActivity.this, "YOUTUBE_PROBE_START", "exit=" + exit + " socks=" + port);
            SocksProbe.Result trace = SocksProbe.fetch(port, "www.cloudflare.com", "/cdn-cgi/trace", 14000, 4096);
            String ip = SocksProbe.traceValue(trace.body, "ip");
            String loc = SocksProbe.traceValue(trace.body, "loc");
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_EXIT_TRACE", "configured=" + exit + " actual=" + loc + " ip=" + ip + " result=" + trace.summary());

            SocksProbe.Result yt = SocksProbe.fetch(port, "www.youtube.com", "/generate_204", 16000, 1024);
            AiAccessLog.i(AiAccessActivity.this, yt.okHttp() ? "YOUTUBE_HTTP" : "YOUTUBE_FAIL", yt.summary());
            SocksProbe.Result gv = SocksProbe.fetch(port, "redirector.googlevideo.com", "/report_mapping", 16000, 2048);
            AiAccessLog.i(AiAccessActivity.this, gv.okHttp() ? "GOOGLEVIDEO_HTTP" : "GOOGLEVIDEO_FAIL", gv.summary());

            boolean ytOk = yt.httpCode == 204 || (yt.httpCode >= 200 && yt.httpCode < 400);
            boolean gvOk = gv.httpCode >= 200 && gv.httpCode < 500;
            boolean accepted = ytOk && gvOk;

            main.post(() -> {
                probing = false;
                if (generation != probeGeneration) return;
                if (accepted) {
                    state = "READY";
                    detail = "Готово · YouTube через " + exit.toUpperCase(Locale.ROOT) + (loc.isEmpty() ? "" : " / " + loc);
                    AiAccessLog.i(AiAccessActivity.this, "ROUTE_ACCEPTED", "exit=" + exit + " loc=" + loc + " ip=" + ip + " yt=" + yt.httpCode + " gv=" + gv.httpCode);
                    AiAccessLog.setState(state, detail);
                    refreshOverlays();
                    launchYouTubeApp();
                } else {
                    AiAccessLog.w(AiAccessActivity.this, "ROUTE_REJECTED", "exit=" + exit + " yt=" + yt.httpCode + " gv=" + gv.httpCode);
                    tryNextExit(yt.httpCode, gv.httpCode);
                }
            });
        }, "YouTubeRouteProbe").start();
    }

    private void tryNextExit(int ytCode, int gvCode) {
        if (exitIndex + 1 >= EXITS.length) {
            state = "ERROR";
            detail = "Маршрут не найден · сохраните ZIP-лог";
            AiAccessLog.w(this, "ROUTE_POOL_EXHAUSTED", "yt=" + ytCode + " gv=" + gvCode);
            refreshOverlays();
            return;
        }
        exitIndex++;
        state = "CONNECTING";
        detail = "Пробую другой маршрут · " + currentExit().toUpperCase(Locale.ROOT);
        switchExit(currentExit());
        scheduleProbe(10500);
        refreshOverlays();
    }

    private void switchExit(String cc) {
        try {
            Intent i = new Intent(this, OrbotService.class)
                    .setAction(OrbotConstants.CMD_SET_EXIT)
                    .putExtra("exit", cc)
                    .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true);
            AiAccessLog.i(this, "EXIT_SWITCH", "country=" + cc);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Throwable t) {
            fail("EXIT_SWITCH", t);
        }
    }

    private void launchYouTubeApp() {
        try {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            q.setPackage(YOUTUBE);
            List<ResolveInfo> candidates = pm.queryIntentActivities(q, PackageManager.MATCH_DEFAULT_ONLY);
            ResolveInfo chosen = null;
            for (ResolveInfo ri : candidates) {
                if (ri != null && ri.activityInfo != null && YOUTUBE.equals(ri.activityInfo.packageName) && ri.activityInfo.enabled) {
                    chosen = ri;
                    break;
                }
            }
            if (chosen == null) {
                AiAccessLog.w(this, "YOUTUBE_LAUNCH_FAIL", "no launcher activity for package=" + YOUTUBE);
                Toast.makeText(this, "Не найден экран запуска приложения YouTube", Toast.LENGTH_LONG).show();
                return;
            }
            ComponentName component = new ComponentName(chosen.activityInfo.packageName, chosen.activityInfo.name);
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(component);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            AiAccessLog.i(this, "YOUTUBE_APP_LAUNCH", "component=" + component.flattenToShortString());
            startActivity(launch);
        } catch (Throwable t) {
            AiAccessLog.e(this, "YOUTUBE_APP_LAUNCH_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Ошибка запуска приложения YouTube", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isYouTubeInstalled() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(YOUTUBE, 0);
            return ai.enabled;
        } catch (Throwable t) {
            return false;
        }
    }

    private String currentExit() { return EXITS[Math.max(0, Math.min(exitIndex, EXITS.length - 1))]; }

    private void refreshOverlays() {
        if (root == null || screenView == null) return;
        if (screen == SCREEN_MAIN) {
            statusOverlay.setVisibility(View.VISIBLE);
            statusOverlay.setText(detail);
            positionOverlay(statusOverlay, 0.16f, 0.742f, 0.68f, 0.045f);
            settingsStateOverlay.setVisibility(View.GONE);
            logOverlay.setVisibility(View.GONE);
        } else if (screen == SCREEN_SETTINGS) {
            statusOverlay.setVisibility(View.GONE);
            settingsStateOverlay.setVisibility(View.VISIBLE);
            settingsStateOverlay.setText("Состояние: " + state + "\nYouTube: приложение\nМаршрут: автоматически\nПодключение: Tor / Snowflake\n" + detail);
            positionOverlay(settingsStateOverlay, 0.32f, 0.205f, 0.55f, 0.145f);
            logOverlay.setVisibility(View.VISIBLE);
            String tail = AiAccessLog.tail(this, 2600);
            String[] lines = tail.split("\\n");
            StringBuilder last = new StringBuilder();
            int start = Math.max(0, lines.length - 13);
            for (int i = start; i < lines.length; i++) {
                String line = lines[i];
                if (line.length() > 96) line = line.substring(0, 96);
                last.append(line).append('\n');
            }
            logOverlay.setText(last.toString());
            positionOverlay(logOverlay, 0.09f, 0.705f, 0.82f, 0.245f);
        } else {
            statusOverlay.setVisibility(View.GONE);
            settingsStateOverlay.setVisibility(View.GONE);
            logOverlay.setVisibility(View.GONE);
        }
    }

    private void positionOverlay(View v, float nx, float ny, float nw, float nh) {
        if (screenView.getDrawable() == null) return;
        float[] values = new float[9];
        screenView.getImageMatrix().getValues(values);
        float sx = values[Matrix.MSCALE_X], sy = values[Matrix.MSCALE_Y];
        float tx = values[Matrix.MTRANS_X], ty = values[Matrix.MTRANS_Y];
        int iw = screenView.getDrawable().getIntrinsicWidth();
        int ih = screenView.getDrawable().getIntrinsicHeight();
        int x = Math.round(tx + iw * sx * nx);
        int y = Math.round(ty + ih * sy * ny);
        int w = Math.round(iw * sx * nw);
        int h = Math.round(ih * sy * nh);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, w), Math.max(1, h));
        lp.leftMargin = x;
        lp.topMargin = y;
        v.setLayoutParams(lp);
    }

    private void saveZip() {
        AiAccessLog.i(this, "LOG_SAVE_REQUEST", "Downloads/FreeVideo");
        new Thread(() -> {
            try {
                String path = LogDownloads.save(this);
                main.post(() -> Toast.makeText(this, "ZIP сохранён: " + path, Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                AiAccessLog.e(this, "LOG_SAVE_FAIL", String.valueOf(t.getMessage()), t);
                main.post(() -> Toast.makeText(this, "Не удалось сохранить ZIP", Toast.LENGTH_LONG).show());
            }
        }, "SaveLogZip").start();
    }

    private Uri createZipUri(File zip) { return FileProvider.getUriForFile(this, getPackageName() + ".aiaccess.files", zip); }

    private Intent zipShareIntent(File zip, Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_SUBJECT, "FreeVideo diagnostic ZIP");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("FreeVideo log", uri));
        return i;
    }

    private void shareTelegram() {
        try {
            File zip = AiAccessLog.createZip(this);
            Uri uri = createZipUri(zip);
            for (String pkg : TELEGRAM_PACKAGES) {
                Intent direct = zipShareIntent(zip, uri);
                direct.setPackage(pkg);
                if (direct.resolveActivity(getPackageManager()) != null) {
                    AiAccessLog.i(this, "LOG_SHARE_TELEGRAM", "package=" + pkg + " file=" + zip.getName());
                    startActivity(direct);
                    return;
                }
            }
            startActivity(Intent.createChooser(zipShareIntent(zip, uri), "Отправить ZIP-лог"));
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SHARE_TELEGRAM_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Не удалось отправить лог", Toast.LENGTH_LONG).show();
        }
    }

    private void shareMax() {
        try {
            File zip = AiAccessLog.createZip(this);
            Uri uri = createZipUri(zip);
            Intent direct = zipShareIntent(zip, uri);
            direct.setPackage(MAX);
            if (direct.resolveActivity(getPackageManager()) != null) startActivity(direct);
            else startActivity(Intent.createChooser(zipShareIntent(zip, uri), "Отправить ZIP-лог"));
            AiAccessLog.i(this, "LOG_SHARE_MAX", "file=" + zip.getName());
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SHARE_MAX_FAIL", String.valueOf(t.getMessage()), t);
            Toast.makeText(this, "Не удалось отправить лог", Toast.LENGTH_LONG).show();
        }
    }

    private void fail(String where, Throwable t) {
        state = "ERROR";
        detail = "Ошибка: " + where + " · сохраните ZIP-лог";
        AiAccessLog.e(this, where, String.valueOf(t.getMessage()), t);
        AiAccessLog.setState(state, detail);
        refreshOverlays();
    }

    private void hideSystemUi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable t) {
            AiAccessLog.w(this, "SYSTEM_UI_FAIL", String.valueOf(t.getMessage()));
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
