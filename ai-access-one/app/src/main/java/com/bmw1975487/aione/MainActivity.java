package com.bmw1975487.aione;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
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

import com.bmw1975487.aione.core.AppConstants;
import com.bmw1975487.aione.core.StateStore;
import com.bmw1975487.aione.diag.AppLog;
import com.bmw1975487.aione.diag.NetworkDiagnostics;
import com.bmw1975487.aione.vpn.AiVpnService;

public final class MainActivity extends Activity {
    private static final int RQ_VPN = 1001;
    private static final int NAVY = Color.rgb(5, 18, 38);
    private static final int CARD = Color.rgb(10, 35, 66);
    private static final int CARD_2 = Color.rgb(7, 27, 52);
    private static final int BLUE = Color.rgb(35, 113, 214);
    private static final int GREEN = Color.rgb(31, 181, 119);
    private static final int RED = Color.rgb(232, 80, 80);
    private static final int TEXT = Color.rgb(242, 248, 255);
    private static final int MUTED = Color.rgb(148, 183, 218);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView stateText;
    private TextView detailText;
    private TextView diagText;
    private TextView logText;
    private Button powerButton;
    private Button testButton;
    private Button shareButton;
    private boolean diagnosticsRunning;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 750);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        AppLog.i(this, "ACTIVITY_CREATE",
                "version=0.1.2-diagfix sdk=" + Build.VERSION.SDK_INT + " device=" + Build.MANUFACTURER + " " + Build.MODEL);
        setContentView(buildUi());
        render();
        handler.post(poll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLog.i(this, "ACTIVITY_RESUME", "state=" + StateStore.state(this));
    }

    @Override
    protected void onPause() {
        AppLog.i(this, "ACTIVITY_PAUSE", "state=" + StateStore.state(this));
        super.onPause();
    }

    private View buildUi() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(NAVY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        outer.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = text("AI ACCESS ONE", 27, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2, 0, 0, 0, 0));

        TextView subtitle = text("Диагностика доступа к AI-сервисам", 14, MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(-1, -2, 0, 5, 0, 22));

        LinearLayout statusCard = card(CARD, 18);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(statusCard, lp(-1, -2, 0, 0, 0, 18));

        stateText = text("ВЫКЛ", 24, TEXT, Typeface.BOLD);
        stateText.setGravity(Gravity.CENTER);
        statusCard.addView(stateText, new LinearLayout.LayoutParams(-1, -2));

        detailText = text("Готов к запуску", 13, MUTED, Typeface.NORMAL);
        detailText.setGravity(Gravity.CENTER);
        detailText.setPadding(0, dp(7), 0, 0);
        statusCard.addView(detailText, new LinearLayout.LayoutParams(-1, -2));

        powerButton = button("ВКЛЮЧИТЬ", BLUE, TEXT);
        powerButton.setTextSize(18);
        powerButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        powerButton.setOnClickListener(v -> onPower());
        root.addView(powerButton, lp(-1, 68, 0, 0, 0, 12));

        testButton = button("ПРОВЕРИТЬ СЕТЬ", CARD, TEXT);
        testButton.setOnClickListener(v -> runDiagnostics());
        root.addView(testButton, lp(-1, 56, 0, 0, 0, 10));

        shareButton = button("ОТПРАВИТЬ ЛОГ В MAX", CARD, TEXT);
        shareButton.setOnClickListener(v -> shareLog());
        root.addView(shareButton, lp(-1, 56, 0, 0, 0, 20));

        TextView diagLabel = text("РЕЗУЛЬТАТ ПРОВЕРКИ", 12, MUTED, Typeface.BOLD);
        root.addView(diagLabel, lp(-1, -2, 0, 0, 0, 8));

        diagText = text("Проверка ещё не запускалась.", 13, TEXT, Typeface.NORMAL);
        diagText.setPadding(dp(14), dp(14), dp(14), dp(14));
        diagText.setBackground(round(CARD_2, 14, 0, 0));
        root.addView(diagText, lp(-1, -2, 0, 0, 0, 18));

        TextView logLabel = text("ЖИВОЙ ЛОГ", 12, MUTED, Typeface.BOLD);
        root.addView(logLabel, lp(-1, -2, 0, 0, 0, 8));

        ScrollView logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logScroll.setBackground(round(Color.rgb(2, 12, 25), 14, Color.rgb(31, 67, 103), 1));
        logText = text("", 11, Color.rgb(184, 216, 244), Typeface.MONOSPACE.getStyle());
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logScroll.addView(logText, new ScrollView.LayoutParams(-1, -2));
        root.addView(logScroll, lp(-1, 220, 0, 0, 0, 12));

        TextView note = text(
                "Сейчас эта версия не обходит ограничения. Она проверяет Android VPN/TUN, сеть, DNS и прямую доступность OpenAI и пишет каждый шаг в лог.",
                12, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));

        return outer;
    }

    private void onPower() {
        String state = StateStore.state(this);
        AppLog.i(this, "POWER_BUTTON", "currentState=" + state);
        if (AppConstants.STATE_READY.equals(state)
                || AppConstants.STATE_SERVICE_STARTED.equals(state)
                || AppConstants.STATE_PREPARING.equals(state)) {
            AppLog.i(this, "VPN_STOP_REQUEST", "user pressed power off");
            stopService(new Intent(this, AiVpnService.class));
            StateStore.set(this, AppConstants.STATE_OFF, "Остановлено пользователем");
            return;
        }

        StateStore.set(this, AppConstants.STATE_PREPARING, "Запрашиваю VPN-разрешение Android");
        AppLog.i(this, "VPN_PERMISSION_CHECK", "VpnService.prepare()");
        try {
            Intent intent = VpnService.prepare(this);
            if (intent != null) {
                AppLog.i(this, "VPN_PERMISSION_UI", "Android confirmation required");
                startActivityForResult(intent, RQ_VPN);
            } else {
                AppLog.i(this, "VPN_PERMISSION_ALREADY_GRANTED", "starting service directly");
                startVpn();
            }
        } catch (Throwable t) {
            fail("VPN prepare failed", t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RQ_VPN) return;
        AppLog.i(this, "VPN_PERMISSION_RESULT", "resultCode=" + resultCode);
        if (resultCode == RESULT_OK) {
            startVpn();
        } else {
            StateStore.set(this, AppConstants.STATE_ERROR, "VPN-разрешение не выдано");
            AppLog.w(this, "VPN_PERMISSION_DENIED", "user/system denied VPN permission");
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, AiVpnService.class);
        try {
            AppLog.i(this, "VPN_SERVICE_START_REQUEST", "foreground=" + (Build.VERSION.SDK_INT >= 26));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            StateStore.set(this, AppConstants.STATE_SERVICE_STARTED, "Сервис запущен, создаю TUN");
        } catch (Throwable t) {
            fail("VPN service start failed", t);
        }
    }

    private void runDiagnostics() {
        if (diagnosticsRunning) return;
        diagnosticsRunning = true;
        testButton.setEnabled(false);
        testButton.setText("ПРОВЕРЯЮ…");
        diagText.setText("Проверяю активную сеть, DNS и HTTPS-доступ…");
        AppLog.i(this, "DIAG_BUTTON", "user requested network diagnostics");

        NetworkDiagnostics.runAsync(this, summary -> {
            diagnosticsRunning = false;
            testButton.setEnabled(true);
            testButton.setText("ПРОВЕРИТЬ СЕТЬ");
            diagText.setText(summary);
            AppLog.i(this, "DIAG_UI_UPDATED", "summaryChars=" + summary.length());
            render();
        });
    }

    private void shareLog() {
        AppLog.i(this, "LOG_SHARE_REQUEST", "target=MAX package=ru.oneme.app");
        String payload = AppLog.sharePayload(this);

        Intent direct = new Intent(Intent.ACTION_SEND);
        direct.setType("text/plain");
        direct.putExtra(Intent.EXTRA_SUBJECT, "AI Access One diagnostic log");
        direct.putExtra(Intent.EXTRA_TEXT, payload);
        direct.setPackage("ru.oneme.app");

        try {
            if (direct.resolveActivity(getPackageManager()) != null) {
                AppLog.i(this, "LOG_SHARE_MAX", "MAX handler found; opening share screen");
                startActivity(direct);
                return;
            }
            AppLog.w(this, "LOG_SHARE_MAX_NOT_FOUND", "ru.oneme.app has no ACTION_SEND handler");
        } catch (Throwable t) {
            AppLog.e(this, "LOG_SHARE_MAX_FAILED", String.valueOf(t.getMessage()), t);
        }

        Intent fallback = new Intent(Intent.ACTION_SEND);
        fallback.setType("text/plain");
        fallback.putExtra(Intent.EXTRA_SUBJECT, "AI Access One diagnostic log");
        fallback.putExtra(Intent.EXTRA_TEXT, payload);
        try {
            Toast.makeText(this, "MAX не найден. Выберите приложение для отправки лога.", Toast.LENGTH_LONG).show();
            startActivity(Intent.createChooser(fallback, "Отправить диагностический лог"));
        } catch (ActivityNotFoundException t) {
            fail("No app can share diagnostic log", t);
        }
    }

    private void fail(String message, Throwable t) {
        String detail = message + ": " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        StateStore.set(this, AppConstants.STATE_ERROR, detail);
        AppLog.e(this, "UI_ERROR", detail, t);
        Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
    }

    private void render() {
        if (stateText == null) return;
        String state = StateStore.state(this);
        String detail = StateStore.detail(this);
        detailText.setText(detail == null || detail.isEmpty() ? "Готов к работе" : detail);

        if (AppConstants.STATE_READY.equals(state)) {
            stateText.setText("●  ВКЛЮЧЕНО");
            stateText.setTextColor(GREEN);
            powerButton.setText("ВЫКЛЮЧИТЬ");
            powerButton.setBackground(round(Color.rgb(22, 77, 116), 16, 0, 0));
        } else if (AppConstants.STATE_ERROR.equals(state)) {
            stateText.setText("●  ОШИБКА");
            stateText.setTextColor(RED);
            powerButton.setText("ПОВТОРИТЬ");
            powerButton.setBackground(round(BLUE, 16, 0, 0));
        } else if (AppConstants.STATE_PREPARING.equals(state)
                || AppConstants.STATE_SERVICE_STARTED.equals(state)) {
            stateText.setText("●  ПОДКЛЮЧЕНИЕ…");
            stateText.setTextColor(Color.rgb(255, 197, 79));
            powerButton.setText("ВЫКЛЮЧИТЬ");
            powerButton.setBackground(round(Color.rgb(22, 77, 116), 16, 0, 0));
        } else {
            stateText.setText("●  ВЫКЛ");
            stateText.setTextColor(MUTED);
            powerButton.setText("ВКЛЮЧИТЬ");
            powerButton.setBackground(round(BLUE, 16, 0, 0));
        }

        if (logText != null) logText.setText(AppLog.tail(this, 12_000));
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setLineSpacing(0, 1.12f);
        return v;
    }

    private Button button(String value, int bg, int fg) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(fg);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, 16, Color.rgb(38, 83, 126), 1));
        return b;
    }

    private LinearLayout card(int bg, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(bg, radius, Color.rgb(29, 67, 105), 1));
        return l;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int hDp, int l, int t, int r, int b) {
        int h = hDp < 0 ? hDp : dp(hDp);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        AppLog.i(this, "ACTIVITY_DESTROY", "finishing=" + isFinishing());
        super.onDestroy();
    }
}
