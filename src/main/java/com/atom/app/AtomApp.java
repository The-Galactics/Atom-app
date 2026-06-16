package com.atom.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atom.app.di.AppContainer;

public class AtomApp extends Application implements Application.ActivityLifecycleCallbacks {

    public interface ForegroundListener {
        void onAppForeground();

        void onAppBackground();
    }

    private AppContainer appContainer;

    private int startedActivities = 0;
    private boolean foreground = false;
    @Nullable
    private ForegroundListener foregroundListener;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer();
        registerActivityLifecycleCallbacks(this);
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

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (startedActivities > 0) {
            startedActivities--;
        }
        if (startedActivities == 0 && foreground) {
            foreground = false;
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
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
