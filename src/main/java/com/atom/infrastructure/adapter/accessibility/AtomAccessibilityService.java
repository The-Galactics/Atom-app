package com.atom.infrastructure.adapter.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

// FUTURE WORK: scaffolding only — logs events; no gesture dispatch or node inspection yet.
public class AtomAccessibilityService extends AccessibilityService {

    private static final String TAG = "AtomA11yService";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Atom accessibility service connected.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // FUTURE WORK: inspect the active window / dispatch gestures here.
        if (event != null) {
            Log.d(TAG, "Accessibility event: type=" + event.getEventType()
                    + " package=" + event.getPackageName());
        }
    }

    @Override
    public void onInterrupt() {
        // Called when the system wants the service to stop any in-progress feedback.
        Log.i(TAG, "Atom accessibility service interrupted.");
    }
}
