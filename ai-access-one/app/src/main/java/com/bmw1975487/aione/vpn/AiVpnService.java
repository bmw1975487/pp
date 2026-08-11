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
        AppLog.i(this, "VPN_SERVICE_CREATE", "package=" + getPackageName() + " sdk=" + Build.VERSION.SDK_INT);
        startForeground(101, notification("Диагностический VPN запущен"));
        AppLog.i(this, "VPN_FOREGROUND_READY", "notificationId=101 channel=aione_vpn");

        try {
            AppLog.i(this, "TUN_CONFIG_BEGIN",
                    "session=AI Access One DIAG mtu=1400 address=10.250.0.1/30 allowedApp=" + getPackageName() + " routes=none");

            Builder builder = new Builder()
                    .setSession("AI Access One DIAG")
                    .setMtu(1400)
                    .addAddress("10.250.0.1", 30)
                    .addAllowedApplication(getPackageName());

            AppLog.i(this, "TUN_ESTABLISH_BEGIN", "Builder.establish()");
            tunFd = builder.establish();
            if (tunFd == null) throw new IllegalStateException("Builder.establish() returned null");

            StateStore.set(this, AppConstants.STATE_READY,
                    "PASS: VPN permission + service + TUN. Маршрутизация пока не включена.");
            AppLog.i(this, "TUN_READY",
                    "fd=" + tunFd.getFd() + " routes=none networkCore=not-installed");
        } catch (Throwable t) {
            String detail = "TUN error: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            StateStore.set(this, AppConstants.STATE_ERROR, detail);
            AppLog.e(this, "TUN_FAILED", detail, t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.i(this, "VPN_SERVICE_START_COMMAND", "startId=" + startId + " flags=" + flags);
        return START_NOT_STICKY;
    }

    @Override
    public void onRevoke() {
        AppLog.w(this, "VPN_PERMISSION_REVOKED", "Android revoked VPN permission");
        StateStore.set(this, AppConstants.STATE_OFF, "VPN-разрешение отозвано Android");
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        AppLog.i(this, "VPN_SERVICE_DESTROY_BEGIN", "tunOpen=" + (tunFd != null));
        if (tunFd != null) {
            try {
                int fd = tunFd.getFd();
                tunFd.close();
                AppLog.i(this, "TUN_CLOSED", "fd=" + fd);
            } catch (Throwable t) {
                AppLog.e(this, "TUN_CLOSE_FAILED", String.valueOf(t.getMessage()), t);
            }
            tunFd = null;
        }
        StateStore.set(this, AppConstants.STATE_OFF, "Диагностический VPN остановлен");
        AppLog.i(this, "VPN_SERVICE_DESTROY_DONE", "state=OFF");
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
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("AI Access One")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
