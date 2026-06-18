package com.atom.infrastructure.adapter.wake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.atom.app.BuildConfig;
import com.atom.app.R;
import com.atom.app.overlay.FloatingBubbleService;
import com.atom.app.settings.AtomPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Foreground service that listens for the wake word ("Atom") with Porcupine and,
 * on detection, hands the microphone to the floating bubble to capture the
 * actual command — then resumes listening. Only Porcupine runs continuously
 * (cheap); everything heavy (STT, network, TTS) runs only after a detection.
 */
public class WakeWordService extends Service implements WakeWordEngine.Listener {

    private static final String TAG = "AtomWake";

    public static final String ACTION_START = "com.atom.app.wake.START";
    public static final String ACTION_STOP = "com.atom.app.wake.STOP";
    /** Broadcast the bubble sends when it has released the mic, so we resume. */
    public static final String ACTION_LISTEN_DONE = "com.atom.app.wake.LISTEN_DONE";

    private static final String CHANNEL_ID = "atom_wake_word";
    private static final int NOTIF_ID = 4711;
    private static final String DEFAULT_KEYWORD_ASSET = "atom.ppn";
    private static final float SENSITIVITY = 0.6f;
    // Safety net: resume listening even if the bubble never signals completion.
    private static final long RESUME_FALLBACK_MS = 10_000L;
    // Give the bubble's SpeechRecognizer time to fully release the mic before
    // Porcupine re-opens it, so the two never contend for the microphone.
    private static final long RESUME_DELAY_MS = 600L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AtomPreferences preferences;
    private WakeWordEngine engine;

    // True while the mic is handed off to the bubble (so a stray DONE is ignored).
    private boolean handingOff;
    private boolean screenReceiverRegistered;

    private final Runnable resumeFallback = this::resumeListening;

    private final BroadcastReceiver doneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            resumeListening();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (engine != null && !handingOff) {
                    engine.stop();
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (engine != null && !handingOff) {
                    startEngineSafely();
                }
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNotification();
        if (engine == null) {
            configureAndStart();
        }
        return START_STICKY;
    }

    private void configureAndStart() {
        preferences = new AtomPreferences(this);

        String accessKey = BuildConfig.PICOVOICE_ACCESS_KEY;
        if (accessKey == null || accessKey.trim().isEmpty()) {
            Log.w(TAG, "No Picovoice access key configured — wake word disabled.");
            stopSelf();
            return;
        }
        String keywordPath = resolveKeywordPath();
        if (keywordPath == null) {
            Log.w(TAG, "No wake-word keyword (.ppn) available — wake word disabled.");
            stopSelf();
            return;
        }

        engine = new PorcupineWakeWordEngine(this, accessKey, keywordPath, SENSITIVITY, this);
        startEngineSafely();

        registerDoneReceiver();
        if (preferences.isWakeWordScreenOnOnly()) {
            registerScreenReceiver();
        }
    }

    private void startEngineSafely() {
        try {
            engine.start();
        } catch (Exception e) {
            Log.e(TAG, "Wake-word engine failed to start", e);
            onError(e.getMessage());
        }
    }

    // --- WakeWordEngine.Listener (called off the main thread) ---

    @Override
    public void onWakeWordDetected() {
        mainHandler.post(() -> {
            if (handingOff) {
                return;
            }
            handingOff = true;
            // Release the mic so the bubble's SpeechRecognizer can use it.
            if (engine != null) {
                engine.stop();
            }
            try {
                Intent listen = new Intent(this, FloatingBubbleService.class)
                        .setAction(FloatingBubbleService.ACTION_LISTEN);
                startForegroundService(listen);
            } catch (Exception e) {
                // e.g. ForegroundServiceStartNotAllowedException if overlay perm
                // was revoked. Don't crash — just resume listening.
                Log.e(TAG, "Could not summon bubble to listen", e);
                resumeListening();
                return;
            }
            // Resume even if the bubble never reports back (user cancels, etc.).
            mainHandler.removeCallbacks(resumeFallback);
            mainHandler.postDelayed(resumeFallback, RESUME_FALLBACK_MS);
        });
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Wake-word error: " + message);
    }

    /** Resumes Porcupine after the bubble has freed the mic. Idempotent. */
    private void resumeListening() {
        mainHandler.removeCallbacks(resumeFallback);
        if (!handingOff) {
            return;
        }
        handingOff = false;
        if (engine != null) {
            // Small delay so the SpeechRecognizer's mic teardown completes first.
            mainHandler.postDelayed(this::startEngineSafely, RESUME_DELAY_MS);
        }
    }

    // --- setup helpers ---

    @Nullable
    private String resolveKeywordPath() {
        String custom = preferences.getWakeWordPpnPath();
        if (!custom.isEmpty() && new File(custom).exists()) {
            return custom;
        }
        // Fall back to the bundled default keyword, copied to internal storage.
        File out = new File(getFilesDir(), DEFAULT_KEYWORD_ASSET);
        if (!out.exists()) {
            try (InputStream in = getAssets().open(DEFAULT_KEYWORD_ASSET);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    fos.write(buffer, 0, read);
                }
            } catch (IOException e) {
                Log.w(TAG, "Bundled keyword asset missing", e);
                return null;
            }
        }
        return out.getAbsolutePath();
    }

    private void registerDoneReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_LISTEN_DONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(doneReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(doneReceiver, filter);
        }
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
        screenReceiverRegistered = true;
    }

    private void startForegroundNotification() {
        ensureChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.wake_notification_title))
                .setContentText(getString(R.string.wake_notification_text))
                .setSmallIcon(R.drawable.ic_mic)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.wake_channel_name),
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(resumeFallback);
        try {
            unregisterReceiver(doneReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (engine != null) {
            engine.release();
            engine = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
