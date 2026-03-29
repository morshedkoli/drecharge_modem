package com.dRecharge.modem.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.dRecharge.modem.MainActivity;
import com.dRecharge.modem.R;

/**
 * Foreground service that keeps the app process alive so the USSDService
 * (AccessibilityService) is never killed by the OS or battery optimization.
 *
 * START_STICKY ensures Android restarts this service if it ever gets killed.
 * stopWithTask="false" (in manifest) keeps it alive even when the task is removed.
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "drecharge_keepalive";
    static final int NOTIFICATION_ID = 1001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * When the user swipes away the app from Recents, Android calls onTaskRemoved
     * before stopping a stopWithTask="false" service. We reschedule a restart here
     * as a safety net for devices that ignore stopWithTask.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Intent restart = new Intent(getApplicationContext(), KeepAliveService.class);
        restart.setPackage(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
    }

    private Notification buildNotification() {
        createChannel();

        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("dRecharge Running")
                .setContentText("USSD service is active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "dRecharge Keep-Alive",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Keeps the USSD accessibility service active");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
