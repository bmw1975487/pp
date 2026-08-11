package com.bmw1975487.aione.diag;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public final class NetworkDiagnostics {
    public interface Callback { void onDone(String summary); }

    private NetworkDiagnostics() {}

    public static void runAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String result = run(app);
            new Handler(Looper.getMainLooper()).post(() -> callback.onDone(result));
        }, "NetDiag").start();
    }

    public static String run(Context c) {
        long allStart = SystemClock.elapsedRealtime();
        AppLog.i(c, "DIAG_START", "network diagnostics requested");
        List<String> lines = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) throw new IllegalStateException("ConnectivityManager unavailable");

            Network network = cm.getActiveNetwork();
            if (network == null) throw new IllegalStateException("No active network");

            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            String transport = describeTransport(caps);
            boolean internet = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            String capLine = "Сеть: " + transport + ", internet=" + internet + ", validated=" + validated;
            lines.add(capLine);
            AppLog.i(c, "DIAG_NETWORK", capLine);

            long dnsStart = SystemClock.elapsedRealtime();
            InetAddress[] addresses = network.getAllByName("api.openai.com");
            long dnsMs = SystemClock.elapsedRealtime() - dnsStart;
            if (addresses.length == 0) throw new IllegalStateException("DNS returned zero addresses");
            StringBuilder ips = new StringBuilder();
            for (int i = 0; i < addresses.length && i < 3; i++) {
                if (i > 0) ips.append(',');
                ips.append(addresses[i].getHostAddress());
            }
            String dnsLine = "DNS OpenAI: PASS " + dnsMs + " ms [" + ips + "]";
            lines.add(dnsLine);
            AppLog.i(c, "DIAG_DNS_PASS", dnsLine);

            lines.add(probe(c, network, "OpenAI API", "https://api.openai.com/v1/models"));
            lines.add(probe(c, network, "ChatGPT", "https://chatgpt.com/"));

            long total = SystemClock.elapsedRealtime() - allStart;
            lines.add("Итог: диагностика завершена за " + total + " ms");
            AppLog.i(c, "DIAG_DONE", "durationMs=" + total);
        } catch (Throwable t) {
            String msg = "Диагностика: ERROR " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            lines.add(msg);
            AppLog.e(c, "DIAG_FAILED", msg, t);
        }
        return join(lines);
    }

    private static String probe(Context c, Network network, String name, String url) {
        long start = SystemClock.elapsedRealtime();
        HttpsURLConnection conn = null;
        try {
            conn = (HttpsURLConnection) network.openConnection(new URL(url));
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "AI-Access-One/0.1.1-diag Android");
            int code = conn.getResponseCode();
            long ms = SystemClock.elapsedRealtime() - start;
            String line = name + ": HTTP " + code + " за " + ms + " ms";
            AppLog.i(c, "DIAG_HTTPS_PASS", "target=" + name + " code=" + code + " durationMs=" + ms);
            return line;
        } catch (Throwable t) {
            long ms = SystemClock.elapsedRealtime() - start;
            String line = name + ": ERROR " + t.getClass().getSimpleName() + " за " + ms + " ms";
            AppLog.e(c, "DIAG_HTTPS_FAIL", "target=" + name + " durationMs=" + ms + " message=" + String.valueOf(t.getMessage()), t);
            return line;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String describeTransport(NetworkCapabilities c) {
        if (c == null) return "unknown";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
        return "Other";
    }

    private static String join(List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) b.append('\n');
            b.append(lines.get(i));
        }
        return b.toString();
    }
}
