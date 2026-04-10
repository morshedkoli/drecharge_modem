package com.dRecharge.modem.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.dRecharge.modem.MainActivity;
import com.dRecharge.modem.R;

import java.lang.ref.WeakReference;

public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "drecharge_keepalive";
    static final int NOTIFICATION_ID = 1001;

    /** Full-colour logo bitmap — shown as the large icon in the notification card. */
    private static WeakReference<Bitmap> logoColorRef = new WeakReference<>(null);
    /** White/alpha-only logo bitmap — used as the small status-bar icon. */
    private static WeakReference<Bitmap> logoWhiteRef = new WeakReference<>(null);

    /** Called from MainActivity after the logo has been downloaded. */
    public static void setLogoBitmaps(Bitmap color, Bitmap white) {
        logoColorRef = new WeakReference<>(color);
        logoWhiteRef = new WeakReference<>(white);
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

            builder.setSmallIcon(R.drawable.ic_notification_drecharge);

            Bitmap largeLogo = getCachedBitmap(logoColorRef);
            if (largeLogo == null) {
                largeLogo = getDefaultColorLogo(ctx);
            }
            if (largeLogo != null) {
                builder.setLargeIcon(largeLogo);
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

        builder.setSmallIcon(R.drawable.ic_notification_drecharge);

        Bitmap largeLogo = getCachedBitmap(logoColorRef);
        if (largeLogo == null) {
            largeLogo = getDefaultColorLogo(this);
        }
        if (largeLogo != null) {
            builder.setLargeIcon(largeLogo);
        }

        return builder.build();
    }

    private static Bitmap getCachedBitmap(WeakReference<Bitmap> bitmapReference) {
        return bitmapReference == null ? null : bitmapReference.get();
    }

    private static Bitmap getDefaultColorLogo(Context context) {
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.app_logo_square);
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
