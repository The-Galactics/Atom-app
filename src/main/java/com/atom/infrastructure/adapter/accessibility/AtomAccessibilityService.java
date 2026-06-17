package com.atom.infrastructure.adapter.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;

/**
 * On-device executor for actions a plain {@code Intent} cannot perform — global
 * navigation (back/home/recents) and future gesture/node interactions.
 *
 * <p>The OS owns this service's lifecycle (enabled by the user in Settings), so
 * the action layer reaches the live instance via {@link #getInstance()}.
 */
public class AtomAccessibilityService extends AccessibilityService {

    private static final String TAG = "AtomA11yService";

    // Set while the service is connected; read by the action executor.
    @Nullable
    private static volatile AtomAccessibilityService instance;

    /** The running service, or {@code null} when the user has not enabled it. */
    @Nullable
    public static AtomAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Atom accessibility service connected.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // FUTURE WORK: inspect the active window for node-targeted actions.
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "Atom accessibility service interrupted.");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) {
            instance = null;
        }
        Log.i(TAG, "Atom accessibility service unbound.");
        return super.onUnbind(intent);
    }

    // --- global-action helpers (used by the action layer) -------------------

    /** Navigates back, equivalent to the system Back button. */
    public boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /** Goes to the launcher/home screen. */
    public boolean home() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /** Opens the recent-apps overview. */
    public boolean recents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    /** Opens the Quick Settings shade (handy for manual radio toggles). */
    public boolean quickSettings() {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }
}
