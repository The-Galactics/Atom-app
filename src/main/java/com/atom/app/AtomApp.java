package com.atom.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.atom.app.di.AppContainer;
import com.atom.app.settings.AtomPreferences;
import com.atom.application.port.out.security.SessionListener;
import com.atom.infrastructure.adapter.wake.WakeWordService;

import java.lang.ref.WeakReference;

public class AtomApp extends Application
        implements Application.ActivityLifecycleCallbacks, SessionListener {

    public interface ForegroundListener {
        void onAppForeground();

        void onAppBackground();
    }

    private AppContainer appContainer;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // The currently resumed Activity, used to redirect to Login on session expiry.
    @Nullable
    private WeakReference<Activity> currentActivity;

    private int startedActivities = 0;
    private boolean foreground = false;
    // Attempt the wake-word FGS once per foreground session, at onResume (when
    // the app is mic-eligible). Reset when we go to the background.
    private boolean wakeAttempted = false;
    @Nullable
    private ForegroundListener foregroundListener;

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved UI language before any Activity inflates, so the first
        // screen already renders in the chosen locale. AppCompat persists this in
        // its own store; we drive it from our AtomPreferences value as the source
        // of truth (and re-apply here to cover a fresh process / cleared state).
        applyLocale(new AtomPreferences(this).getLanguage());
        appContainer = new AppContainer(this);
        registerActivityLifecycleCallbacks(this);
    }

    /**
     * Applies a UI language via AppCompat's per-app locale API. Pass
     * {@link AtomPreferences#LANGUAGE_SYSTEM} (or null) to follow the device
     * locale (empty list), or a BCP-47 tag like "en"/"es" to force one. AppCompat
     * recreates visible Activities so the change takes effect immediately.
     */
    public static void applyLocale(String language) {
        LocaleListCompat locales = (language == null
                || AtomPreferences.LANGUAGE_SYSTEM.equals(language))
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(language);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }

    /** True while at least one Activity is in the started (visible) state. */
    public boolean isAppInForeground() {
        return foreground;
    }

    /** The overlay service registers itself here to react to app visibility. */
    public void setForegroundListener(@Nullable ForegroundListener listener) {
        this.foregroundListener = listener;
    }

    /** Clears the listener only if it is still the one registered. */
    public void clearForegroundListener(ForegroundListener listener) {
        if (this.foregroundListener == listener) {
            this.foregroundListener = null;
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (appContainer != null) {
            appContainer.shutdown();
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities++;
        if (startedActivities == 1 && !foreground) {
            foreground = true;
            if (foregroundListener != null) {
                foregroundListener.onAppForeground();
            }
        }
    }

    /** Starts the wake-word service if the user enabled it. Safe to call repeatedly. */
    private void maybeStartWakeWord() {
        try {
            if (new AtomPreferences(this).isWakeWordEnabled()) {
                startForegroundService(new Intent(this, WakeWordService.class)
                        .setAction(WakeWordService.ACTION_START));
            }
        } catch (Exception ignored) {
            // Background-start restrictions etc. — the Settings toggle still works.
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (startedActivities > 0) {
            startedActivities--;
        }
        if (startedActivities == 0 && foreground) {
            foreground = false;
            wakeAttempted = false;
            if (foregroundListener != null) {
                foregroundListener.onAppBackground();
            }
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = new WeakReference<>(activity);
        // At onResume the app is truly foreground, so a microphone FGS is allowed.
        if (!wakeAttempted) {
            wakeAttempted = true;
            maybeStartWakeWord();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity != null && currentActivity.get() == activity) {
            currentActivity = null;
        }
    }

    // --- SessionListener (US-10.3) ------------------------------------------

    /**
     * Called from the auth layer (a background gRPC thread) when a token refresh
     * is rejected as UNAUTHENTICATED. Credentials are already wiped by then; here
     * we send the user to Login immediately, clearing the back stack so the
     * expired session is not reachable via Back.
     */
    @Override
    public void onSessionExpired() {
        mainHandler.post(() -> {
            Activity activity = currentActivity != null ? currentActivity.get() : null;
            Intent intent = new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activity != null) {
                activity.startActivity(intent);
            } else {
                startActivity(intent);
            }
        });
    }
}
