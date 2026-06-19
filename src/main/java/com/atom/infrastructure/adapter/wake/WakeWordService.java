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
    /** Broadcast the bubble/app sends when it has released the mic, so we resume. */
    public static final String ACTION_LISTEN_DONE = "com.atom.app.wake.LISTEN_DONE";
    /** Broadcast to the app when it's foreground: drive its in-app mic, not the bubble. */
    public static final String ACTION_WAKE_IN_APP = "com.atom.app.wake.IN_APP";
    /** Rebuilds the engine in-place (e.g. after the wake-word name changed). */
    public static final String ACTION_RECONFIGURE = "com.atom.app.wake.RECONFIGURE";
    /** App/bubble is about to use the mic manually: release it until LISTEN_DONE. */
    public static final String ACTION_WAKE_PAUSE = "com.atom.app.wake.PAUSE";

    private static final String CHANNEL_ID = "atom_wake_word";
    private static final int NOTIF_ID = 4711;
    private static final String MODEL_ASSET_DIR = "model-es";
    private static final String MODEL_TARGET_DIR = "vosk-model-es";
    // Safety net: resume listening even if the listener never signals completion
    // (e.g. wake fired while a non-Main screen was foreground and nobody captured).
    private static final long RESUME_FALLBACK_MS = 6_000L;
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
    // Set as soon as configuration begins, so repeated START intents arriving
    // before the async model unpack finishes don't spin up a second engine.
    private boolean configured;

    private final Runnable resumeFallback = this::resumeListening;

    private final BroadcastReceiver doneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WAKE_PAUSE.equals(intent.getAction())) {
                pauseForExternalMic();
            } else {
                resumeListening();
            }
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
        if (!startForegroundNotification()) {
            // Not allowed to be a mic FGS right now (e.g. app not foreground).
            // Stop cleanly instead of crashing; it'll start when eligible.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RECONFIGURE.equals(action) && configured) {
            reconfigure();
            return START_STICKY;
        }
        if (!configured) {
            configured = true;
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
        startEngineForName(preferences.getWakeWordName());
    }

    /** Rebuilds the engine for the current name without restarting the service. */
    private void reconfigure() {
        if (preferences == null) {
            configureAndStart();
            return;
        }
        mainHandler.removeCallbacks(resumeFallback);
        handingOff = false;
        if (engine != null) {
            engine.release();
            engine = null;
        }
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
        startEngineForName(preferences.getWakeWordName());
    }

    /**
     * Starts Vosk listening for {@code name}. Vosk needs the (large) model
     * unpacked to internal storage once, so we do it lazily here.
     */
    private void startEngineForName(String name) {
        StorageService.unpack(this, MODEL_ASSET_DIR, MODEL_TARGET_DIR,
                model -> {
                    voskModel = model;
                    engine = new VoskWakeWordEngine(voskModel, name, this);
                    startEngineSafely();
                },
                exception -> {
                    Log.e(TAG, "Vosk model unpack failed", exception);
                    stopSelf();
                });
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
            // Release the mic so the listener (app or bubble) can use it.
            if (engine != null) {
                engine.stop();
            }
            if (isAppInForeground()) {
                // App is open: drive its own mic instead of the overlay bubble.
                sendBroadcast(new Intent(ACTION_WAKE_IN_APP).setPackage(getPackageName()));
            } else {
                try {
                    Intent listen = new Intent(this, FloatingBubbleService.class)
                            .setAction(FloatingBubbleService.ACTION_LISTEN);
                    startForegroundService(listen);
                } catch (Exception e) {
                    Log.e(TAG, "Could not summon bubble to listen", e);
                    resumeListening();
                    return;
                }
            }
            mainHandler.removeCallbacks(resumeFallback);
            mainHandler.postDelayed(resumeFallback, RESUME_FALLBACK_MS);
        });
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Wake-word error: " + message);
    }

    private boolean isAppInForeground() {
        try {
            android.app.Application app = getApplication();
            return app instanceof com.atom.app.AtomApp
                    && ((com.atom.app.AtomApp) app).isAppInForeground();
        } catch (Exception e) {
            return false;
        }
    }

    /** Resumes listening after the listener (app/bubble) has freed the mic. Idempotent. */
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

    /**
     * Releases the mic because the app/bubble is about to capture manually (the
     * user tapped the mic). Mirrors the wake-detected handoff; the listener sends
     * ACTION_LISTEN_DONE when finished so we resume. The fallback resumes anyway.
     */
    private void pauseForExternalMic() {
        if (engine == null || handingOff) {
            return;
        }
        handingOff = true;
        engine.stop();
        mainHandler.removeCallbacks(resumeFallback);
        mainHandler.postDelayed(resumeFallback, RESUME_FALLBACK_MS);
    }

    // --- setup helpers ---

    private void registerDoneReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_LISTEN_DONE);
        filter.addAction(ACTION_WAKE_PAUSE);
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

    /** Promotes to a mic foreground service. Returns false (no crash) if denied. */
    private boolean startForegroundNotification() {
        ensureChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.wake_notification_title))
                .setContentText(getString(R.string.wake_notification_text))
                .setSmallIcon(R.drawable.ic_mic)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIF_ID, notification);
            }
            return true;
        } catch (Exception e) {
            // Android 14+: starting a microphone FGS while not in an eligible
            // (foreground) state throws. Don't crash the app — bail out.
            Log.e(TAG, "startForeground(microphone) not allowed right now", e);
            return false;
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
