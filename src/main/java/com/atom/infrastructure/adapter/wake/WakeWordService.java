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

import com.atom.app.R;
import com.atom.app.overlay.FloatingBubbleService;
import com.atom.app.settings.AtomPreferences;

import org.vosk.Model;
import org.vosk.android.StorageService;

/**
 * Foreground service that listens for the wake word (default "Atom") with Vosk
 * on-device recognition and, on detection, hands the microphone to the floating
 * bubble to capture the actual command — then resumes. The wake word is just a
 * string the user types (no model file), matched against what Vosk hears.
 */
public class WakeWordService extends Service implements WakeWordEngine.Listener {

    private static final String TAG = "AtomWake";

    public static final String ACTION_START = "com.atom.app.wake.START";
    public static final String ACTION_STOP = "com.atom.app.wake.STOP";
    /** Broadcast the bubble sends when it has released the mic, so we resume. */
    public static final String ACTION_LISTEN_DONE = "com.atom.app.wake.LISTEN_DONE";

    private static final String CHANNEL_ID = "atom_wake_word";
    private static final int NOTIF_ID = 4711;
    private static final String MODEL_ASSET_DIR = "model-es";
    private static final String MODEL_TARGET_DIR = "vosk-model-es";
    // Safety net: resume listening even if the bubble never signals completion.
    private static final long RESUME_FALLBACK_MS = 10_000L;
    // Give the bubble's SpeechRecognizer time to fully release the mic before
    // Vosk re-opens it, so the two never contend for the microphone.
    private static final long RESUME_DELAY_MS = 600L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AtomPreferences preferences;
    private WakeWordEngine engine;
    private Model voskModel;

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
        if (engine == null && voskModel == null) {
            configureAndStart();
        }
        return START_STICKY;
    }

    private void configureAndStart() {
        preferences = new AtomPreferences(this);
        registerDoneReceiver();
        if (preferences.isWakeWordScreenOnOnly()) {
            registerScreenReceiver();
        }
        // Unpack the bundled model to internal storage (one-time), then listen.
        StorageService.unpack(this, MODEL_ASSET_DIR, MODEL_TARGET_DIR,
                model -> {
                    voskModel = model;
                    buildAndStartEngine();
                },
                exception -> {
                    Log.e(TAG, "Vosk model unpack failed", exception);
                    stopSelf();
                });
    }

    private void buildAndStartEngine() {
        engine = new VoskWakeWordEngine(voskModel, preferences.getWakeWordName(), this);
        startEngineSafely();
    }

    private void startEngineSafely() {
        if (engine == null) {
            return;
        }
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
                Log.e(TAG, "Could not summon bubble to listen", e);
                resumeListening();
                return;
            }
            mainHandler.removeCallbacks(resumeFallback);
            mainHandler.postDelayed(resumeFallback, RESUME_FALLBACK_MS);
        });
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Wake-word error: " + message);
    }

    /** Resumes listening after the bubble has freed the mic. Idempotent. */
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
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
