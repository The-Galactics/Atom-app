package com.atom.infrastructure.adapter.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

// FUTURE WORK: scaffolding only — hosts a mediaProjection foreground service; no capture yet.
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";

    public static final String EXTRA_RESULT_CODE = "com.atom.app.extra.RESULT_CODE";
    public static final String EXTRA_RESULT_DATA = "com.atom.app.extra.RESULT_DATA";

    private static final String CHANNEL_ID = "atom_screen_capture";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());

        // FUTURE WORK: read result extras, obtain the MediaProjection, and start capturing.
        Log.i(TAG, "ScreenCaptureService started (scaffolding — no capture performed).");

        // Not sticky: the session must be re-established with fresh user consent.
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Atom screen view",
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Atom")
                .setContentText("Screen view is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Not a bound service.
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // FUTURE WORK: release MediaProjection / VirtualDisplay here.
        Log.i(TAG, "ScreenCaptureService destroyed.");
    }
}
