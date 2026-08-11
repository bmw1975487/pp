package org.torproject.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.torproject.android.service.OrbotConstants;
import org.torproject.android.service.OrbotService;
import org.torproject.jni.TorService;

public final class AiAccessActivity extends Activity {
    private static final int VPN_REQ = 3301;
    private static final String CHATGPT = "com.openai.chatgpt";
    private static final String MAX = "ru.oneme.app";
    private static final String[] EXITS = {"nl", "de", "fr", "gb", "us"};

    private static final int NAVY = Color.rgb(4, 16, 35);
    private static final int CARD = Color.rgb(9, 34, 65);
    private static final int CARD2 = Color.rgb(6, 25, 49);
    private static final int BLUE = Color.rgb(30, 111, 219);
    private static final int GREEN = Color.rgb(41, 199, 133);
    private static final int AMBER = Color.rgb(255, 190, 66);
    private static final int RED = Color.rgb(242, 85, 85);
    private static final int TEXT = Color.rgb(244, 249, 255);
    private static final int MUTED = Color.rgb(150, 183, 216);

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView stateView, detailView, routeView, logView;
    private Button powerButton, probeButton, shareButton;
    private String state = "OFF";
    private String detail = "Готов к запуску";
    private int socksPort = -1;
    private int exitIndex = 0;
    private boolean probing = false;
    private boolean receiverRegistered = false;
    private int probeGeneration = 0;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            main.postDelayed(this, 750);
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
                    detail = "Tor bootstrap " + percent + "% · " + currentExit().toUpperCase();
                }
            } else if (OrbotConstants.LOCAL_ACTION_PORTS.equals(action)) {
                socksPort = intent.getIntExtra(OrbotConstants.EXTRA_SOCKS_PROXY_PORT, -1);
                int dns = intent.getIntExtra(OrbotConstants.EXTRA_DNS_PORT, -1);
                int trans = intent.getIntExtra(OrbotConstants.EXTRA_TRANS_PORT, -1);
                AiAccessLog.i(AiAccessActivity.this, "TOR_PORTS", "socks=" + socksPort + " dns=" + dns + " trans=" + trans);
                if (socksPort > 0) scheduleProbe(1800);
            } else if (OrbotConstants.LOCAL_ACTION_STATUS.equals(action)) {
                String status = intent.getStringExtra(TorService.EXTRA_STATUS);
                AiAccessLog.i(AiAccessActivity.this, "TOR_STATUS", "status=" + status);
                if (TorService.STATUS_ON.equals(status)) {
                    state = "TOR_ON";
                    detail = "Tor подключён · проверяю маршрут " + currentExit().toUpperCase();
                    scheduleProbe(1400);
                } else if (TorService.STATUS_OFF.equals(status)) {
                    if (!"READY".equals(state) && !"ERROR".equals(state)) {
                        state = "OFF";
                        detail = "Tor остановлен";
                    }
                }
            }
            render();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        AiAccessLog.i(this, "ACTIVITY_CREATE", "version=0.2.0-orbot-route sdk=" + Build.VERSION.SDK_INT + " device=" + Build.MANUFACTURER + " " + Build.MODEL);
        setContentView(buildUi());
        registerOrbotReceiver();
        checkChatGptInstalled();
        AiAccessLog.setState(state, detail);
        main.post(refresh);
    }

    @Override protected void onResume() {
        super.onResume();
        AiAccessLog.i(this, "ACTIVITY_RESUME", "state=" + state);
    }

    private void registerOrbotReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(OrbotConstants.LOCAL_ACTION_LOG);
        f.addAction(OrbotConstants.LOCAL_ACTION_STATUS);
        f.addAction(OrbotConstants.LOCAL_ACTION_PORTS);
        ContextCompat.registerReceiver(this, orbotReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        AiAccessLog.i(this, "TOR_RECEIVER_READY", "status/log/ports");
    }

    private void checkChatGptInstalled() {
        try {
            getPackageManager().getPackageInfo(CHATGPT, 0);
            AiAccessLog.i(this, "CHATGPT_PACKAGE_FOUND", CHATGPT);
        } catch (Throwable t) {
            AiAccessLog.w(this, "CHATGPT_PACKAGE_NOT_FOUND", CHATGPT);
        }
    }

    private View buildUi() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(NAVY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(26));
        outer.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = text("AI ACCESS ONE", 28, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2, 0, 0, 0, 4));
        TextView sub = text("ChatGPT · автоматический маршрут", 14, MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1, -2, 0, 0, 0, 20));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setPadding(dp(16), dp(17), dp(16), dp(17));
        statusCard.setBackground(round(CARD, 18, Color.rgb(31, 70, 108), 1));
        root.addView(statusCard, lp(-1, -2, 0, 0, 0, 16));

        stateView = text("●  ВЫКЛ", 23, MUTED, Typeface.BOLD);
        stateView.setGravity(Gravity.CENTER);
        statusCard.addView(stateView, new LinearLayout.LayoutParams(-1, -2));
        detailView = text(detail, 13, MUTED, Typeface.NORMAL);
        detailView.setGravity(Gravity.CENTER);
        detailView.setPadding(0, dp(7), 0, 0);
        statusCard.addView(detailView, new LinearLayout.LayoutParams(-1, -2));

        powerButton = button("ВКЛЮЧИТЬ CHATGPT", BLUE);
        powerButton.setTextSize(18);
        powerButton.setOnClickListener(v -> onPower());
        root.addView(powerButton, lp(-1, 68, 0, 0, 0, 11));

        probeButton = button("ПРОВЕРИТЬ МАРШРУТ", CARD);
        probeButton.setOnClickListener(v -> startProbe(false));
        root.addView(probeButton, lp(-1, 56, 0, 0, 0, 9));

        shareButton = button("ОТПРАВИТЬ ЛОГ В MAX", CARD);
        shareButton.setOnClickListener(v -> shareLog());
        root.addView(shareButton, lp(-1, 56, 0, 0, 0, 18));

        TextView routeLabel = text("МАРШРУТ", 12, MUTED, Typeface.BOLD);
        root.addView(routeLabel, lp(-1, -2, 0, 0, 0, 7));
        routeView = text("Ожидание запуска.", 13, TEXT, Typeface.NORMAL);
        routeView.setPadding(dp(13), dp(13), dp(13), dp(13));
        routeView.setBackground(round(CARD2, 14, Color.rgb(28, 62, 98), 1));
        root.addView(routeView, lp(-1, -2, 0, 0, 0, 16));

        TextView logLabel = text("ЖИВОЙ ЛОГ", 12, MUTED, Typeface.BOLD);
        root.addView(logLabel, lp(-1, -2, 0, 0, 0, 7));
        ScrollView logScroll = new ScrollView(this);
        logScroll.setBackground(round(Color.rgb(2, 10, 23), 14, Color.rgb(28, 62, 98), 1));
        logView = text("", 10, Color.rgb(184, 216, 244), Typeface.NORMAL);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(11), dp(11), dp(11), dp(11));
        logScroll.addView(logView, new ScrollView.LayoutParams(-1, -2));
        root.addView(logScroll, lp(-1, 250, 0, 0, 0, 12));

        TextView note = text("VPN направляет только официальное приложение ChatGPT. Движок: Tor/Orbot SmartConnect. Выходы тестируются автоматически.", 12, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));
        return outer;
    }

    private void onPower() {
        AiAccessLog.i(this, "POWER_BUTTON", "state=" + state);
        if (!"OFF".equals(state) && !"ERROR".equals(state)) {
            stopRoute();
            return;
        }
        exitIndex = 0;
        probeGeneration++;
        AiAccessPrefs.configure(this, currentExit());
        AiAccessLog.i(this, "ROUTE_CONFIG", "allowedApp=" + CHATGPT + " smartConnect=" + AiAccessPrefs.smartConnectEnabled() + " exit=" + AiAccessPrefs.selectedExit());
        state = "PREPARING";
        detail = "Запрашиваю VPN-разрешение";
        render();
        try {
            Intent prep = VpnService.prepare(this);
            if (prep != null) {
                AiAccessLog.i(this, "VPN_PERMISSION_UI", "Android confirmation required");
                startActivityForResult(prep, VPN_REQ);
            } else {
                AiAccessLog.i(this, "VPN_PERMISSION_ALREADY_GRANTED", "start Orbot");
                startOrbot();
            }
        } catch (Throwable t) {
            fail("VPN prepare", t);
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
            render();
        }
    }

    private void startOrbot() {
        try {
            state = "CONNECTING";
            detail = "SmartConnect · exit " + currentExit().toUpperCase();
            Intent i = new Intent(this, OrbotService.class)
                    .setAction(TorService.ACTION_START)
                    .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true);
            AiAccessLog.i(this, "ENGINE_START", "OrbotService action=START transport=autoconf exit=" + currentExit());
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            render();
        } catch (Throwable t) {
            fail("Orbot start", t);
        }
    }

    private void stopRoute() {
        probeGeneration++;
        probing = false;
        try {
            Intent i = new Intent(this, OrbotService.class)
                    .setAction(TorService.ACTION_STOP)
                    .putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            AiAccessLog.i(this, "ENGINE_STOP", "user requested stop");
        } catch (Throwable t) {
            AiAccessLog.e(this, "ENGINE_STOP_FAIL", String.valueOf(t.getMessage()), t);
        }
        state = "OFF";
        detail = "Остановлено";
        socksPort = -1;
        render();
    }

    private void scheduleProbe(long delayMs) {
        final int generation = probeGeneration;
        main.postDelayed(() -> {
            if (generation == probeGeneration && !"OFF".equals(state)) startProbe(true);
        }, delayMs);
    }

    private void startProbe(boolean auto) {
        if (probing) return;
        if (socksPort <= 0) {
            AiAccessLog.w(this, "ROUTE_PROBE_SKIPPED", "SOCKS port not ready");
            if (!auto) Toast.makeText(this, "SOCKS ещё не готов", Toast.LENGTH_SHORT).show();
            return;
        }
        probing = true;
        probeButton.setEnabled(false);
        probeButton.setText("ПРОВЕРЯЮ…");
        final int generation = probeGeneration;
        final int port = socksPort;
        final String exit = currentExit();
        new Thread(() -> {
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_PROBE_START", "exit=" + exit + " socks=" + port);
            SocksProbe.Result trace = SocksProbe.fetch(port, "www.cloudflare.com", "/cdn-cgi/trace", 12000, 4096);
            String ip = SocksProbe.traceValue(trace.body, "ip");
            String loc = SocksProbe.traceValue(trace.body, "loc");
            AiAccessLog.i(AiAccessActivity.this, "ROUTE_EXIT_TRACE", "configured=" + exit + " actual=" + loc + " ip=" + ip + " result=" + trace.summary());

            SocksProbe.Result api = SocksProbe.fetch(port, "api.openai.com", "/v1/models", 14000, 2048);
            AiAccessLog.i(AiAccessActivity.this, api.okHttp() ? "ROUTED_OPENAI_HTTP" : "ROUTED_OPENAI_FAIL", api.summary());
            SocksProbe.Result chat = SocksProbe.fetch(port, "chatgpt.com", "/", 14000, 2048);
            AiAccessLog.i(AiAccessActivity.this, chat.okHttp() ? "ROUTED_CHATGPT_HTTP" : "ROUTED_CHATGPT_FAIL", chat.summary());

            boolean apiAccepted = api.httpCode == 401 || (api.httpCode >= 200 && api.httpCode < 400);
            boolean chatAccepted = chat.httpCode >= 200 && chat.httpCode < 400;
            boolean accepted = apiAccepted || chatAccepted;
            String summary = "exit=" + exit.toUpperCase() + (loc.isEmpty() ? "" : " / " + loc) +
                    "\nIP: " + (ip.isEmpty() ? "unknown" : ip) +
                    "\nOpenAI API: " + api.summary() +
                    "\nChatGPT: " + chat.summary();

            main.post(() -> {
                probing = false;
                probeButton.setEnabled(true);
                probeButton.setText("ПРОВЕРИТЬ МАРШРУТ");
                if (generation != probeGeneration) return;
                routeView.setText(summary);
                if (accepted) {
                    state = "READY";
                    detail = "Маршрут принят · " + exit.toUpperCase() + (loc.isEmpty() ? "" : " / " + loc);
                    AiAccessLog.i(AiAccessActivity.this, "ROUTE_ACCEPTED", "exit=" + exit + " loc=" + loc + " ip=" + ip + " api=" + api.httpCode + " chat=" + chat.httpCode);
                    launchChatGpt();
                } else {
                    AiAccessLog.w(AiAccessActivity.this, "ROUTE_REJECTED", "exit=" + exit + " loc=" + loc + " api=" + api.httpCode + " chat=" + chat.httpCode);
                    tryNextExit(api.httpCode, chat.httpCode);
                }
                render();
            });
        }, "RouteProbe").start();
    }

    private void tryNextExit(int apiCode, int chatCode) {
        if (exitIndex + 1 >= EXITS.length) {
            state = "ERROR";
            detail = "Все Tor-exit проверены · последний API=" + apiCode + " ChatGPT=" + chatCode;
            AiAccessLog.w(this, "ROUTE_POOL_EXHAUSTED", detail);
            return;
        }
        exitIndex++;
        String next = currentExit();
        state = "CONNECTING";
        detail = "Предыдущий exit отклонён · пробую " + next.toUpperCase();
        switchExit(next);
        scheduleProbe(11000);
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
            fail("Exit switch " + cc, t);
        }
    }

    private void launchChatGpt() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(CHATGPT);
            if (launch == null) {
                AiAccessLog.w(this, "CHATGPT_LAUNCH_FAIL", "package not installed or no launcher");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            AiAccessLog.i(this, "CHATGPT_LAUNCH", CHATGPT);
            startActivity(launch);
        } catch (Throwable t) {
            AiAccessLog.e(this, "CHATGPT_LAUNCH_FAIL", String.valueOf(t.getMessage()), t);
        }
    }

    private void shareLog() {
        AiAccessLog.i(this, "LOG_SHARE_REQUEST", "target=MAX");
        String payload = AiAccessLog.sharePayload(this);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "AI Access One route log");
        i.putExtra(Intent.EXTRA_TEXT, payload);
        i.setPackage(MAX);
        try {
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
                return;
            }
        } catch (Throwable t) {
            AiAccessLog.e(this, "LOG_SHARE_MAX_FAIL", String.valueOf(t.getMessage()), t);
        }
        Intent fallback = new Intent(Intent.ACTION_SEND);
        fallback.setType("text/plain");
        fallback.putExtra(Intent.EXTRA_SUBJECT, "AI Access One route log");
        fallback.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(fallback, "Отправить лог"));
    }

    private void fail(String where, Throwable t) {
        state = "ERROR";
        detail = where + ": " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        AiAccessLog.e(this, "APP_ERROR", detail, t);
        render();
    }

    private String currentExit() { return EXITS[Math.max(0, Math.min(exitIndex, EXITS.length - 1))]; }

    private void render() {
        if (stateView == null) return;
        AiAccessLog.setState(state, detail);
        detailView.setText(detail);
        if ("READY".equals(state)) {
            stateView.setText("●  CHATGPT ГОТОВ"); stateView.setTextColor(GREEN); powerButton.setText("ВЫКЛЮЧИТЬ");
        } else if ("ERROR".equals(state)) {
            stateView.setText("●  ОШИБКА"); stateView.setTextColor(RED); powerButton.setText("ПОВТОРИТЬ");
        } else if ("OFF".equals(state)) {
            stateView.setText("●  ВЫКЛ"); stateView.setTextColor(MUTED); powerButton.setText("ВКЛЮЧИТЬ CHATGPT");
        } else {
            stateView.setText("●  ПОДКЛЮЧЕНИЕ"); stateView.setTextColor(AMBER); powerButton.setText("ВЫКЛЮЧИТЬ");
        }
        if (logView != null) logView.setText(AiAccessLog.tail(this, 16000));
    }

    private TextView text(String s, int sp, int color, int style) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); v.setLineSpacing(0, 1.12f); return v;
    }
    private Button button(String s, int bg) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(bg, 16, Color.rgb(37, 79, 120), 1)); return b;
    }
    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor); return g;
    }
    private LinearLayout.LayoutParams lp(int w, int hDp, int l, int t, int r, int b) {
        int h = hDp < 0 ? hDp : dp(hDp); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        main.removeCallbacks(refresh);
        if (receiverRegistered) {
            try { unregisterReceiver(orbotReceiver); } catch (Throwable ignored) {}
        }
        AiAccessLog.i(this, "ACTIVITY_DESTROY", "finishing=" + isFinishing());
        super.onDestroy();
    }
}
