package com.bmw1975487.aione.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.bmw1975487.aione.MainActivity;
import com.bmw1975487.aione.R;
import com.bmw1975487.aione.core.AppConstants;
import com.bmw1975487.aione.core.StateStore;
import com.bmw1975487.aione.diag.AppLog;

public final class AiVpnService extends VpnService {
    private ParcelFileDescriptor tunFd;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(101, notification("Bootstrap VPN запущен"));
        try {
            tunFd = new Builder()
                    .setSession("AI Access One Bootstrap")
                    .setMtu(1400)
                    .addAddress("10.250.0.1", 30)
                    .addAllowedApplication(getPackageName())
                    .establish();
            if (tunFd == null) throw new IllegalStateException("Builder.establish() returned null");
            StateStore.set(this, AppConstants.STATE_READY, "PASS: Activity + VPN permission + VpnService + TUN");
            AppLog.i(this, "BOOTSTRAP_READY", "tunFd=" + tunFd.getFd());
        } catch (Throwable t) {
            StateStore.set(this, AppConstants.STATE_ERROR,
                    "TUN error: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            AppLog.e(this, "BOOTSTRAP_FAILED", String.valueOf(t.getMessage()), t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (tunFd != null) {
            try { tunFd.close(); } catch (Throwable ignored) {}
            tunFd = null;
        }
        StateStore.set(this, AppConstants.STATE_OFF, "Bootstrap остановлен");
        super.onDestroy();
    }

    private Notification notification(String text) {
        final String channelId = "aione_vpn";
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    channelId, "AI Access One", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, channelId)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("AI Access One")
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build();
        }
        return new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("AI Access One")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
