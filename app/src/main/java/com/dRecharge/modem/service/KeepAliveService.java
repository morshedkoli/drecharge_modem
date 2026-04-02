package com.dRecharge.modem.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.dRecharge.modem.MainActivity;
import com.dRecharge.modem.R;

public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "drecharge_keepalive";
    static final int NOTIFICATION_ID = 1001;

    /** Full-colour logo bitmap — shown as the large icon in the notification card. */
    private static Bitmap logoColor = null;
    /** White/alpha-only logo bitmap — used as the small status-bar icon. */
    private static Bitmap logoWhite = null;

    /** Called from MainActivity after the logo has been downloaded. */
    public static void setLogoBitmaps(Bitmap color, Bitmap white) {
        logoColor = color;
        logoWhite = white;
    }

    /** Re-posts the notification with the latest logo bitmaps. */
    public static void updateNotification(Context ctx) {
        // We need a service instance to call startForeground; instead update via NotificationManager.
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            // Build a fresh notification; channel already created when service started.
            PendingIntent openApp = PendingIntent.getActivity(
                    ctx, 0,
                    new Intent(ctx, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setContentTitle("dRecharge Running")
                    .setContentText("USSD service is active")
                    .setContentIntent(openApp)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setSilent(true);

            if (logoWhite != null) {
                builder.setSmallIcon(IconCompat.createWithBitmap(logoWhite));
            } else {
                builder.setSmallIcon(R.mipmap.ic_launcher);
            }

            if (logoColor != null) {
                builder.setLargeIcon(logoColor);
            }

            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("dRecharge Running")
                .setContentText("USSD service is active")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true);

        if (logoWhite != null) {
            builder.setSmallIcon(IconCompat.createWithBitmap(logoWhite));
        } else {
            builder.setSmallIcon(R.mipmap.ic_launcher);
        }

        if (logoColor != null) {
            builder.setLargeIcon(logoColor);
        }

        return builder.build();
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
